package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Album

class AlbumsRepository(private val mpd: MpdRemoteDataSource) {
    suspend fun getAllAlbums(addr: SocketAddress): List<Album>? {
        var list: MutableList<Album>? = null
        val ids = mpd.fetchAlbumIds(addr)
        if (ids != null) {
            list = mutableListOf()
            for (id in ids) {
                val title = mpd.fetchAlbumTitleById(addr, id)
                val artistId = mpd.fetchAlbumArtistId(addr, id)
                if (title != null && artistId != null) {
                    list?.add(Album(id, title, artistId))
                } else {
                    list = null
                    break
                }
            }
        }

        return list?.toList()
    }

    suspend fun refresh() {

    }
}