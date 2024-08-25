package xyz.stalinsky.ampd.datasource

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.model.Track
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.service.MpdFilter
import xyz.stalinsky.ampd.service.MpdGroupNode
import xyz.stalinsky.ampd.service.MpdRequest
import xyz.stalinsky.ampd.service.MpdResponse
import xyz.stalinsky.ampd.service.MpdTag
import java.io.EOFException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpdRemoteDataSource @Inject constructor() {
    private val channel = Channel<Request>(Channel.UNLIMITED)

    private val subscribersMutex = Mutex()
    private val subscribers = mutableListOf<Pair<SendChannel<Result<MpdResponse>?>, MpdRequest>>()

    private suspend fun request(req: MpdRequest): MpdResponse? {
        val c = Channel<MpdResponse?>(1)
        return try {
            channel.send(Request.Fetch(req, c))
            c.receive()
        } catch (_: CancellationException) {
            null
        }
    }

    private suspend fun subscribe(req: MpdRequest): ReceiveChannel<Result<MpdResponse>?>? {
        val c = Channel<Result<MpdResponse>?>(1)
        return try {
            channel.send(Request.Subscribe(req, c))
            c
        } catch (e: CancellationException) {
            null
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun reconnect(addr: SocketAddress?, tls: Boolean): Result<MpdClient>? {
        if (addr == null) return null

        val client = try {
            MpdClient(addr, tls)
        } catch (e: CancellationException) {
            return null
        } catch (e: Throwable) {
            subscribersMutex.withLock {
                val iter = subscribers.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    try {
                        s.first.send(Result.failure(e))
                    } catch (e: CancellationException) {
                        if (s.first.isClosedForSend) {
                            s.first.close()
                            iter.remove()
                        } else {
                            return null
                        }
                    }
                }
            }
            return Result.failure(e)
        }

        subscribersMutex.withLock {
            val iter = subscribers.iterator()
            while (iter.hasNext()) {
                val s = iter.next()
                try {
                    val res = Result.success(client.request(s.second))
                    s.first.send(res)
                } catch (e: CancellationException) {
                    if (s.first.isClosedForSend) {
                        // Even if this CancellationException happened due to coroutine cancellation,
                        // we will eventually return from this coroutine in a later cancel point
                        s.first.close()
                        iter.remove()
                    } else {
                        client.close()
                        return null
                    }
                } catch (e: Throwable) {
                    client.close()
                    s.first.send(Result.failure(e))
                    return Result.failure(e)
                }
            }
        }

        return Result.success(client)
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(addr: SocketAddress?, tls: Boolean) {
        withContext(Dispatchers.IO) {
            subscribersMutex.withLock {
                val iter = subscribers.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    try {
                        s.first.send(null)
                    } catch (e: CancellationException) {
                        if (s.first.isClosedForSend) {
                            s.first.close()
                            iter.remove()
                        } else {
                            return@withContext
                        }
                    }
                }
            }

            val res = reconnect(addr, tls) ?: return@withContext
            var exp: Throwable? = null
            var client = res.getOrElse {
                exp = it
                null
            }

            try {
                while (isActive) {
                    val req = channel.receive()
                    if (client != null) {
                        when (req) {
                            is Request.Fetch     -> {
                                try {
                                    req.res.send(client.request(req.req))
                                } catch (_: EOFException) {
                                    val res = reconnect(addr, tls) ?: return@withContext
                                    client = res.getOrElse {
                                        exp = it
                                        null
                                    }

                                    if (client != null) {
                                        try {
                                            req.res.send(client.request(req.req))
                                        } catch (_: CancellationException) {
                                        } catch (e: Throwable) {
                                            try {
                                                req.res.send(null)
                                            } catch (_: CancellationException) { }
                                        }
                                    }
                                } catch (_: CancellationException) {
                                } catch (e: Throwable) {
                                    try {
                                        req.res.send(null)
                                    } catch (_: CancellationException) { }
                                } finally {
                                    req.res.close()
                                }
                            }

                            is Request.Subscribe -> {
                                subscribersMutex.withLock {
                                    subscribers.add(Pair(req.res, req.req))
                                }
                                try {
                                    req.res.send(Result.success(client.request(req.req)))
                                } catch (e: EOFException) {
                                    val res = reconnect(addr, tls) ?: return@withContext
                                    client = res.getOrElse {
                                        exp = it
                                        null
                                    }
                                } catch (_: CancellationException) {
                                    if (req.res.isClosedForSend) {
                                        subscribersMutex.withLock {
                                            subscribers.removeAll { it.first == req.res }
                                        }
                                    }
                                } catch (e: Throwable) {
                                    try {
                                        req.res.send(Result.failure(e))
                                    } catch (_: CancellationException) {
                                        if (req.res.isClosedForSend) {
                                            subscribersMutex.withLock {
                                                subscribers.removeAll { it.first == req.res }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        when (req) {
                            is Request.Fetch     -> {
                                try {
                                    req.res.send(null)
                                } catch (_: CancellationException) {
                                } finally {
                                    req.res.close()
                                }
                            }

                            is Request.Subscribe -> {
                                subscribersMutex.withLock {
                                    subscribers.add(Pair(req.res, req.req))
                                }
                                try {
                                    req.res.send(Result.failure(exp!!))
                                } catch (_: CancellationException) {
                                }
                            }
                        }
                    }
                }
            } finally {
                client?.close()
            }
        }
    }

    fun fetchArtists() = flow {
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.Artist,
                null,
                listOf(MpdTag.ArtistSort, MpdTag.MUSICBRAINZ_ARTISTID))) ?: return@flow

        try {
            while (true) {
                val res = channel.receive()

                data class SortArtist(val id: String, val name: String, val sort: String)

                emit(res?.map {
                    val res = it as MpdResponse.MpdListResponse
                    val out = mutableListOf<SortArtist>()
                    res.data.mapTo(out) {
                        val artistId = (it as MpdGroupNode.Node).data
                        val artist = it.children[0] as MpdGroupNode.Node
                        val sort = artist.data
                        val name = (artist.children[0] as MpdGroupNode.Leaf).data
                        SortArtist(artistId, name, sort)
                    }
                    out.sortBy {
                        it.sort
                    }
                    out.map { Artist(it.id, it.name) }
                })
            }
        } finally {
            channel.cancel()
        }
    }

    fun fetchArtistSongs(id: String): Flow<Result<List<Song>>?> = flow {
        val channel = subscribe(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id), null))
                ?: return@flow

        try {
            while (true) {
                val res = channel.receive()
                emit(res?.map {
                    (it as MpdResponse.MpdFindResponse).data.map {
                        Song(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "",
                                it.file,
                                it.tags[MpdTag.Title] ?: "",
                                it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "",
                                it.tags[MpdTag.Album] ?: "",
                                it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "",
                                it.tags[MpdTag.Artist] ?: "")
                    }
                })
            }
        } finally {
            channel.cancel()
        }
    }

    suspend fun fetchArtistNameById(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(MpdTag.Artist, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id)))

        return ((res as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
    }

    fun fetchAlbums() = flow {
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.Album,
                null,
                listOf(MpdTag.AlbumSort, MpdTag.MUSICBRAINZ_ALBUMID, MpdTag.MUSICBRAINZ_ALBUMARTISTID))) ?: return@flow

        try {
            while (true) {
                val res = channel.receive()

                data class SortAlbum(val id: String, val title: String, val sort: String, val artistId: String)

                emit(res?.map {
                    val out = mutableListOf<SortAlbum>()
                    (it as MpdResponse.MpdListResponse).data.flatMapTo(out) {
                        val artistId = (it as MpdGroupNode.Node).data
                        it.children.map {
                            val albumId = (it as MpdGroupNode.Node).data
                            val album = it.children[0] as MpdGroupNode.Node
                            val sort = album.data
                            val title = (album.children[0] as MpdGroupNode.Leaf).data
                            SortAlbum(albumId, title, sort, artistId)
                        }
                    }
                    out.sortBy {
                        it.sort
                    }

                    val res = request(MpdRequest.MpdCommandListRequest(out.map {
                        MpdRequest.MpdListRequest(MpdTag.AlbumArtist,
                                MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMARTISTID, it.artistId))
                    })) as MpdResponse.MpdCommandListResponse? ?: return@flow

                    out.zip(res.data.map { (it as MpdResponse.MpdListResponse).data.first() }).map {
                        val res = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID,
                                it.first.id), null, Pair(0, 1))) as MpdResponse.MpdFindResponse?

                        Album(it.first.id,
                                it.first.title,
                                it.first.artistId,
                                (it.second as MpdGroupNode.Leaf).data,
                                res?.data?.get(0)?.file ?: "")
                    }
                })
            }
        } catch (e: Throwable) {
            channel.cancel()
        }
    }

    suspend fun fetchAlbumById(id: String): Album? {
        val res = request(MpdRequest.MpdCommandListRequest(listOf(MpdRequest.MpdListRequest(MpdTag.Album,
                MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id)),
                MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMARTISTID,
                        MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id)),
                MpdRequest.MpdListRequest(MpdTag.AlbumArtist,
                        MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id))))) as MpdResponse.MpdCommandListResponse?

        val album = ((res?.data?.get(0) as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
        val artistId = ((res?.data?.get(1) as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
        val artist = ((res?.data?.get(2) as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data

        val uri = request(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id),
                null,
                Pair(0, 1))) as MpdResponse.MpdFindResponse?

        return Album(id,
                album ?: return null,
                artistId ?: return null,
                artist ?: return null,
                uri?.data?.get(0)?.file ?: "")
    }

    suspend fun fetchAlbumArt(uri: String, offset: Long, buf: Buffer): Long? {
        val art = request(MpdRequest.MpdAlbumArtRequest(uri, offset)) as MpdResponse.MpdAlbumArtResponse?
        buf.write(art?.data ?: ByteArray(0))
        return art?.size
    }

    fun fetchAlbumTracks(id: String) = flow {
        val channel = subscribe(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), null))
                ?: return@flow

        try {
            while (true) {
                val res = channel.receive()

                emit(res?.map {
                    (it as MpdResponse.MpdFindResponse).data.map {
                        Track(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "",
                                it.file,
                                it.tags[MpdTag.Title] ?: "",
                                it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "",
                                it.tags[MpdTag.Album] ?: "",
                                it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "",
                                it.tags[MpdTag.Artist] ?: "",
                                it.tags[MpdTag.Disc]?.toInt() ?: 0,
                                it.tags[MpdTag.Track]?.toInt() ?: 0)
                    }
                })
            }
        } finally {
            channel.cancel()
        }
    }

    private sealed interface Request {
        class Fetch(val req: MpdRequest, val res: SendChannel<MpdResponse?>) : Request
        class Subscribe(val req: MpdRequest, val res: SendChannel<Result<MpdResponse>?>) : Request
    }
}