package xyz.stalinsky.ampd.data

import okio.Buffer
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject

class AlbumsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun getAllAlbums() = mpd.fetchAlbums()

    suspend fun getAlbumById(id: String) = mpd.fetchAlbumById(id)

    suspend fun getAlbumArt(id: String, offset: Long, buf: Buffer) = mpd.fetchAlbumArt(id, offset, buf)
}