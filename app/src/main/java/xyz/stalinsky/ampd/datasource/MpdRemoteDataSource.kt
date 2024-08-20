package xyz.stalinsky.ampd.datasource

import android.util.Log
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpdRemoteDataSource @Inject constructor() {
    private val mutex = Mutex()

    private val reqChannel = Channel<Request>()
    private val resChannel = Channel<Response?>()

    private val subscribersMutex = Mutex()
    private val subscribers = mutableListOf<Pair<SendChannel<Result<MpdResponse>?>, MpdRequest>>()

    private suspend fun request(req: MpdRequest): MpdResponse? {
        mutex.lock()
        return try {
            reqChannel.send(Request.Fetch(req))
            (resChannel.receive() as Response.Fetch?)?.res
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                resChannel.receive()
            }
            e.printStackTrace()
            null
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun subscribe(req: MpdRequest): ReceiveChannel<Result<MpdResponse>?>? {
        mutex.lock()
        return try {
            reqChannel.send(Request.Subscribe(req))
            (resChannel.receive() as Response.Subscribe).res
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                resChannel.receive()
            }
            e.printStackTrace()
            null
        } finally {
            mutex.unlock()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(addr: SocketAddress?, tls: Boolean) {
        withContext(Dispatchers.IO) {
            subscribersMutex.lock()
            try {
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
            } finally {
                subscribersMutex.unlock()
            }

            if (addr == null)
                return@withContext

            var exp: Throwable? = null
            val client = try {
                MpdClient(addr, tls)
            } catch (e: CancellationException) {
                return@withContext
            } catch (e: Throwable) {
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
                            return@withContext
                        }
                    }
                }
                exp = e
                null
            }

            if (client != null) {
                subscribersMutex.lock()
                try {
                    val iter = subscribers.iterator()
                    while (iter.hasNext()) {
                        val s = iter.next()
                        try {
                            val res = Result.success(client.request(s.second))
                            s.first.send(res)
                        } catch (e: CancellationException) {
                            if (s.first.isClosedForSend) {
                                s.first.close()
                                iter.remove()
                            } else {
                                return@withContext
                            }
                        } catch (e: Throwable) {
                            s.first.send(Result.failure(e))
                        }
                    }
                } finally {
                    subscribersMutex.unlock()
                }
            }

            try {
                while (isActive) {
                    val req = reqChannel.receive()
                    if (client != null) {
                        when (req) {
                            is Request.Fetch -> {
                                try {
                                    resChannel.send(Response.Fetch(client.request(req.req)))
                                } catch (e: CancellationException) {
                                    e.printStackTrace()
                                } catch (e: Throwable) {
                                    resChannel.send(null)
                                }
                            }

                            is Request.Subscribe -> {
                                Log.i("TAG", "Subscription")
                                val source = Channel<Result<MpdResponse>?>()
                                subscribersMutex.lock()
                                subscribers.add(Pair(source, req.req))
                                subscribersMutex.unlock()
                                resChannel.send(Response.Subscribe(source))
                                try {
                                    source.send(Result.success(client.request(req.req)))
                                } catch (e: Throwable) {
                                    source.send(Result.failure(e))
                                }
                            }
                        }
                    } else {
                        when (req) {
                            is Request.Fetch -> {
                                try {
                                    resChannel.send(Response.Fetch(null))
                                } catch (e: CancellationException) {
                                    e.printStackTrace()
                                } catch (e: Throwable) {
                                    resChannel.send(null)
                                }
                            }

                            is Request.Subscribe -> {
                                Log.i("TAG", "Subscription")
                                val source = Channel<Result<MpdResponse>?>()
                                subscribersMutex.lock()
                                subscribers.add(Pair(source, req.req))
                                subscribersMutex.unlock()
                                resChannel.send(Response.Subscribe(source))
                                try {
                                    source.send(Result.failure(exp!!))
                                } catch (e: CancellationException) {
                                    e.printStackTrace()
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
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.Artist, null, listOf(MpdTag.MUSICBRAINZ_ARTISTID)))
            ?: return@flow

        try {
            while (true) {
                val res = channel.receive()
                val test = res?.map {
                    val list = buildList {
                        val res = it as MpdResponse.MpdListResponse
                        for (id in res.data) {
                            val artistId = (id as MpdGroupNode.Node).data
                            add(Artist(artistId, ((id as MpdGroupNode.Node).children[0] as MpdGroupNode.Leaf).data))
                        }
                    }
                    list
                }
                emit(test)
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
                            it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "")
                    }
                })
            }
        } finally {
            channel.cancel()
        }
    }

    suspend fun fetchArtistNameById(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(
            MpdTag.Artist,
            MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id),
            listOf()
        ))

        return ((res as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
    }

    fun fetchAlbums() = flow {
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.Album, null, listOf(MpdTag.AlbumSort, MpdTag.MUSICBRAINZ_ALBUMID, MpdTag.MUSICBRAINZ_ALBUMARTISTID)))
            ?: return@flow

        try {
            while(true) {
                val res = channel.receive()

                data class SortAlbum(val id: String, val title: String, val sort: String, val artistId: String,)

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
                    out.map {
                        Album(it.id, it.title, it.artistId)
                    }
                })
            }
        } catch (e: Throwable) {
            channel.cancel()
        }
    }

    suspend fun fetchAlbumTitleById(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(
                MpdTag.Album,
                MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id),
                listOf()
        ))

        return ((res as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
    }

    suspend fun fetchAlbumArtistId(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf()))

        return ((res as MpdResponse.MpdListResponse?)?.data?.first() as MpdGroupNode.Leaf?)?.data
    }

    fun fetchAlbumTracks(id: String) = flow {
        val channel = subscribe(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), null))
            ?: return@flow

        try {
            while (true) {
                val res = channel.receive()

                emit(res?.map {
                    (it as MpdResponse.MpdFindResponse).data.map {
                        Track(it.tags[MpdTag.MUSICBRAINZ_TRACKID] ?: "", it.file, it.tags[MpdTag.Title] ?: "", it.tags[MpdTag.MUSICBRAINZ_ALBUMID] ?: "", it.tags[MpdTag.MUSICBRAINZ_ARTISTID] ?: "", it.tags[MpdTag.Disc]?.toInt() ?: 0, it.tags[MpdTag.Track]?.toInt() ?: 0)
                    }
                })
            }
        } finally {
            channel.cancel()
        }
    }

    private sealed class Request(val req: MpdRequest) {
        class Fetch(req: MpdRequest) : Request(req)
        class Subscribe(req: MpdRequest) : Request(req)
    }

    private sealed interface Response {
        class Fetch(val res: MpdResponse?) : Response
        class Subscribe(val res: ReceiveChannel<Result<MpdResponse>?>) : Response
    }
}