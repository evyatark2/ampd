package xyz.stalinsky.ampd.datasource

import android.util.Log
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
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.model.Track
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.service.MpdFilter
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
    private val subscribers = mutableListOf<Pair<SendChannel<Response.Response?>, MpdRequest>>()

    private suspend fun request(req: MpdRequest): MpdResponse? {
        mutex.lock()
        return try {
            reqChannel.send(Request.Fetch(req))
            (resChannel.receive() as Response.Fetch?)?.res
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun subscribe(req: MpdRequest): ReceiveChannel<Response.Response?> {
        mutex.lock()
        return try {
            reqChannel.send(Request.Subscribe(req))
            (resChannel.receive() as Response.Subscribe).res
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

            val client = try {
                MpdClient(addr, tls)
            } catch (e: CancellationException) {
                return@withContext
            } catch (e: Throwable) {
                val iter = subscribers.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    try {
                        s.first.send(Response.Response.Err(e))
                    } catch (e: CancellationException) {
                        if (s.first.isClosedForSend) {
                            s.first.close()
                            iter.remove()
                        } else {
                            return@withContext
                        }
                    }
                }
                return@withContext
            }

            subscribersMutex.lock()
            try {
                val iter = subscribers.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    try {
                        s.first.send(Response.Response.Ok(client.request(s.second)))
                    } catch (e: CancellationException) {
                        if (s.first.isClosedForSend) {
                            s.first.close()
                            iter.remove()
                        } else {
                            return@withContext
                        }
                    } catch (e: Throwable) {
                        s.first.send(Response.Response.Err(e))
                    }
                }
            } finally {
                subscribersMutex.unlock()
            }

            while (isActive) {
                val req = reqChannel.receive()
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
                        val source = Channel<Response.Response?>()
                        subscribersMutex.lock()
                        subscribers.add(Pair(source, req.req))
                        subscribersMutex.unlock()
                        resChannel.send(Response.Subscribe(source))
                        try {
                            source.send(Response.Response.Ok(client.request(req.req)))
                        } catch (e: Throwable) {
                            source.send(Response.Response.Err(e))
                        }
                    }
                }
            }

            client.close()
        }
    }

    fun fetchArtistIds(): Flow<List<String>?> = flow {
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, null, listOf()))

        try {
            while (true) {
                val res = channel.receive()
                when (res) {
                    is Response.Response.Ok -> {
                        val list = mutableListOf<String>()
                        val res = res.res as MpdResponse.MpdListResponse
                        for (kv in res.data) {
                            list.add(kv.value)
                        }
                        emit(list)
                    }
                    is Response.Response.Err -> {
                        throw res.e
                    }
                    null -> {
                        emit(null)
                    }
                }
            }
        } finally {
            channel.cancel()
        }
    }

    fun fetchArtistSongs(id: String): Flow<List<Song>?> = flow {
        val channel = subscribe(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id), null))

        try {
            while (true) {
                val res = channel.receive()
                when (res) {
                    is Response.Response.Err -> {
                        throw res.e
                    }

                    is Response.Response.Ok -> {
                        val list = mutableListOf<Song>()
                        val iter = (res.res as MpdResponse.MpdListResponse).data.iterator()
                        var file = iter.next().value
                        var id = ""
                        var title = ""
                        var albumId = ""
                        var artistId = ""
                        for (kv in iter) {
                            when (kv.key) {
                                "file" -> {
                                    list.add(Song(id, file, title, albumId, artistId))
                                    file = kv.value
                                    id = ""
                                    artistId = ""
                                    albumId = ""
                                }

                                "MUSICBRAINZ_TRACKID" -> id = kv.value
                                "Title" -> title = kv.value
                                "MUSICBRAINZ_ARTISTID" -> artistId = kv.value
                                "MUSICBRAINZ_ALBUMID" -> albumId = kv.value
                            }
                        }
                        emit(list)
                    }

                    null -> {
                        emit(null)
                    }
                }
            }
        } finally {
            channel.cancel()
        }
    }

    suspend fun fetchArtistNameById(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(
            MpdTag.ARTIST,
            MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id),
            listOf()
        ))

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    fun fetchAlbumIds(): Flow<List<String>?> = flow {
        val channel = subscribe(MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMID, null, listOf()))

        try {
            while(true) {
                val res = channel.receive()

                when (res) {
                    is Response.Response.Err -> {
                        throw res.e
                    }

                    is Response.Response.Ok -> {
                        val list = mutableListOf<String>()
                        val res = res.res as MpdResponse.MpdListResponse
                        for (kv in res.data) {
                            list.add(kv.value)
                        }
                        emit(list)
                    }

                    null -> {
                        emit(null)
                    }
                }
            }
        } catch (e: Throwable) {
            channel.cancel()
        }
    }

    suspend fun fetchAlbumTitleById(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(
                MpdTag.ALBUM,
                MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id),
                listOf()
        ))

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    suspend fun fetchAlbumArtistId(id: String): String? {
        val res = request(MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf()))

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    fun fetchAlbumTracks(id: String) = flow {
        val channel = subscribe(MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), null))

        try {
            while (true) {
                val res = channel.receive()
                when (res) {
                    is Response.Response.Err -> {
                        throw res.e
                    }

                    is Response.Response.Ok -> {
                        val list = mutableListOf<Track>()
                        val iter = (res.res as MpdResponse.MpdListResponse).data.iterator()
                        var file = iter.next().value
                        var id = ""
                        var title = ""
                        var albumId = ""
                        var artistId = ""
                        var side = 0
                        var track = 0
                        for (kv in iter) {
                            when (kv.key) {
                                "file" -> {
                                    list.add(Track(id, file, title, albumId, artistId, side, track))
                                    file = kv.value
                                    id = ""
                                    artistId = ""
                                    albumId = ""
                                }

                                "MUSICBRAINZ_TRACKID" -> id = kv.value
                                "Title" -> title = kv.value
                                "MUSICBRAINZ_ARTISTID" -> artistId = kv.value
                                "MUSICBRAINZ_ALBUMID" -> albumId = kv.value
                                "Disc" -> side = kv.value.toInt()
                                "Track" -> track = kv.value.toInt()
                            }
                        }
                        emit(list)
                    }

                    null -> {
                        emit(null)
                    }
                }
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
        class Fetch(val res: MpdResponse?) : MpdRemoteDataSource.Response
        class Subscribe(val res: ReceiveChannel<Response?>) : MpdRemoteDataSource.Response
        sealed interface Response {
            class Err(val e: Throwable) : Response
            class Ok(val res: MpdResponse) : Response
        }
    }
}