package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject

class AlbumsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllAlbums() = mpd.fetchAlbums()

    suspend fun getAlbumById(id: String) = mpd.fetchAlbumById(id)
}