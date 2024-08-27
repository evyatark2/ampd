package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun getAllArtists() = mpd.fetchArtists()

    suspend fun getArtistById(id: String) = mpd.fetchArtistNameById(id).map { Artist(id, it) }
}