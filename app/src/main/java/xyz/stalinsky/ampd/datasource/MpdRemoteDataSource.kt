package xyz.stalinsky.ampd.datasource

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.service.MpdFilter
import xyz.stalinsky.ampd.service.MpdRequest
import xyz.stalinsky.ampd.service.MpdResponse
import xyz.stalinsky.ampd.service.MpdTag

class MpdRemoteDataSource(scope: CoroutineScope) {
    private val lock = Mutex()
    private lateinit var addr: SocketAddress

    private val reqChannel = Channel<MpdRequest>()
    private val resChannel = Channel<MpdResponse?>()

    init {
        scope.launch {
            var addr: SocketAddress? = null
            var client: MpdClient? = null
            withContext(Dispatchers.IO) {
                while (true) {
                    val req = reqChannel.receive()
                    if (addr != this@MpdRemoteDataSource.addr) {
                        addr = this@MpdRemoteDataSource.addr
                        client = MpdClient(addr!!)
                    }

                    try {
                        resChannel.send(client?.request(req))
                    } catch (ign: Exception) {
                        // Try again
                        client = MpdClient(addr!!)
                        try {
                            resChannel.send(client?.request(req))
                        } catch (e: Exception) {
                            resChannel.send(null)
                        }
                    }
                }
            }
        }
    }

    private suspend fun request(addr: SocketAddress, req: MpdRequest): MpdResponse? {
        lock.lock()
        return try {
            this.addr = addr

            reqChannel.send(req)
            resChannel.receive()
        } finally {
            lock.unlock()
        }
    }


    suspend fun fetchArtistIds(addr: SocketAddress): List<String>? {
        val res = request(addr, MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, null, listOf()))

        return if (res == null) {
            null
        } else {
            val list = mutableListOf<String>()
            val res = res as MpdResponse.MpdListResponse
            for (kv in res.data) {
                list.add(kv.value)
            }
            list
        }
    }

    suspend fun fetchArtistSongIds(addr: SocketAddress, id: String): List<Song>? {
        val res = request(addr, MpdRequest.MpdFindRequest(MpdFilter.Equal(MpdTag.MUSICBRAINZ_ARTISTID, id), null))

        return if (res == null) {
            null
        } else {
            val list = mutableListOf<Song>()
            val iter = (res as MpdResponse.MpdListResponse).data.iterator()
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
            list
        }
    }

    suspend fun fetchArtistNameById(addr: SocketAddress, id: String): String? {
        val res = request(addr, MpdRequest.MpdListRequest(
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

    suspend fun fetchAlbumIds(addr: SocketAddress): List<String>? {
        val res = request(addr, MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMID, null, listOf()))

        return if (res == null) {
            null
        } else {
            val list = mutableListOf<String>()
            val res = res as MpdResponse.MpdListResponse
            for (kv in res.data) {
                list.add(kv.value)
            }
            list
        }
    }

    suspend fun fetchAlbumTitleById(addr: SocketAddress, id: String): String? {
        val res = request(addr, MpdRequest.MpdListRequest(
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

    suspend fun fetchAlbumArtistId(addr: SocketAddress, id: String): String? {
        val res = request(addr, MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ARTISTID, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf()))

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.first().value
        }
    }

    suspend fun fetchAlbumTrackIds(addr: SocketAddress, id: String): List<String>? {
        val res = request(addr, MpdRequest.MpdListRequest(MpdTag.MUSICBRAINZ_ALBUMID, MpdFilter.Equal(MpdTag.MUSICBRAINZ_ALBUMID, id), listOf()))

        return if (res == null) {
            null
        } else {
            (res as MpdResponse.MpdListResponse).data.map { it.value }
        }
    }
}