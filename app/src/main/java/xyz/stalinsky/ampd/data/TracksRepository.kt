package xyz.stalinsky.ampd.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Track

class TracksRepository(private val mpd: MpdRemoteDataSource) {
    suspend fun getTracksForAlbum(id: String): Track {
        return withContext(Dispatchers.IO) {
            val conn = mpd.connect()
            if (conn == null) {
                null
            } else {
                val name = mpd.fetchArtistNameById(conn, id)
                if (name == null) {
                    null
                } else {
                    Track(id, name)
                }
            }
        }
    }
}