package xyz.stalinsky.ampd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Album

class AlbumsRepository(private val mpd: MpdRemoteDataSource) {
    fun getAllAlbums(): Flow<List<Album>?> {
        return flow {
            emit(withContext(Dispatchers.IO) {
                val conn = mpd.connect()
                if (conn == null) {
                    null
                } else {
                    val ids = mpd.fetchAlbumIds(conn)
                    if (ids == null) {
                        null
                    } else {
                        val list = mutableListOf<Album>()
                        for (id in ids) {
                            val title = mpd.fetchAlbumTitleById(conn, id)
                            val artistId = mpd.fetchAlbumArtistId(conn, id)
                            if (title != null && artistId != null) {
                                list.add(Album(id, title, artistId))
                            } else {
                                return@withContext null
                            }
                        }
                        list
                    }
                }
            })
        }
    }

    suspend fun refresh() {

    }
}