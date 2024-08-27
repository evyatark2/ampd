package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TracksRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun getSongsForArtist(id: String) = mpd.fetchArtistSongs(id)

    suspend fun getTracksForAlbum(id: String) = mpd.fetchAlbumTracks(id)
}