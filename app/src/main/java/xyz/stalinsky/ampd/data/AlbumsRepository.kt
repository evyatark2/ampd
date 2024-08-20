package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Album
import javax.inject.Inject

class AlbumsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllAlbums() =
        mpd.fetchAlbums()

    suspend fun getAlbumById(id: String) =
        mpd.fetchAlbumTitleById(id)?.let { Album(id, it, mpd.fetchAlbumArtistId(id) ?: "") }
}