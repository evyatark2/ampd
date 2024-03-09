package xyz.stalinsky.ampd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist

class ArtistsRepository(private val mpd: MpdRemoteDataSource) {
    fun getAllArtists(): Flow<List<Artist>?> {
        return flow {
            emit(withContext(Dispatchers.IO) {
                val conn = mpd.connect()
                if (conn == null) {
                    null
                } else {
                    val ids = mpd.fetchArtistIds(conn)
                    if (ids == null) {
                        null
                    } else {
                        val list = mutableListOf<Artist>()
                        for (id in ids) {
                            val name = mpd.fetchArtistNameById(conn, id)
                            if (name != null) {
                                list.add(Artist(id, name))
                            } else {
                                emit(null)
                            }
                        }
                        list
                    }
                }
            })
        }
    }

    suspend fun getArtistById(id: String): Artist? {
        return withContext(Dispatchers.IO) {
            val conn = mpd.connect()
            if (conn == null) {
                null
            } else {
                val name = mpd.fetchArtistNameById(conn, id)
                if (name == null) {
                    null
                } else {
                    Artist(id, name)
                }
            }
        }
    }
    suspend fun refresh() {
        if (mpd.artistsInvalid) {
            //flow.
        }
    }
}