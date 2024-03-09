package xyz.stalinsky.ampd.data

import kotlinx.coroutines.flow.Flow
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Song
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TracksRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getSongsForArtist(id: String): Flow<List<Song>?> {
        return mpd.fetchArtistSongs(id)
    }

    fun getTracksForAlbum(id: String) =
        mpd.fetchAlbumTracks(id)
}