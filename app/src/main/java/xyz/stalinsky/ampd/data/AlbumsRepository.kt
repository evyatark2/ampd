package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Album
import javax.inject.Inject

class AlbumsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllAlbums() =
        mpd.fetchAlbumIds().map {
            var list: MutableList<Album>? = null
            if (it != null) {
                list = mutableListOf()
                for (id in it) {
                    val title = mpd.fetchAlbumTitleById(id)
                    val artistId = mpd.fetchAlbumArtistId(id)
                    if (title != null && artistId != null) {
                        list?.add(Album(id, title, artistId))
                    } else {
                        list = null
                        break
                    }
                }
            }

            list?.toList()
        }

    suspend fun getAlbumById(id: String): Album? {
        val title = mpd.fetchAlbumTitleById(id)
        val artistId = mpd.fetchAlbumArtistId(id)
        return if (title != null)
            Album(id, title, artistId ?: "")
        else
            null
    }
}