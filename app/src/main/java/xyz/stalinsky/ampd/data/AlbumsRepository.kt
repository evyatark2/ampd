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
            it?.let {
                buildList {
                    for (id in it) {
                        val title = mpd.fetchAlbumTitleById(id)
                        val artistId = mpd.fetchAlbumArtistId(id)
                        if (title != null && artistId != null) {
                            add(Album(id, title, artistId))
                        } else {
                            break
                        }
                    }
                }
            }
        }

    suspend fun getAlbumById(id: String) =
        mpd.fetchAlbumTitleById(id)?.let { Album(id, it, mpd.fetchAlbumArtistId(id) ?: "") }
}