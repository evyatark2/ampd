package xyz.stalinsky.ampd.data

import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllArtists() =
        mpd.fetchArtists()

    suspend fun getArtistById(id: String) =
        mpd.fetchArtistNameById(id)?.let { Artist(id, it) }
}