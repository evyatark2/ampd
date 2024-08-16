package xyz.stalinsky.ampd.data

import kotlinx.coroutines.flow.map
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllArtists() =
        mpd.fetchArtistIds().map {
            it?.map {
                buildList {
                    for (id in it) {
                        val name = mpd.fetchArtistNameById(id)
                        add(Artist(id, name ?: ""))
                    }
                }
            }
        }

    suspend fun getArtistById(id: String) =
        mpd.fetchArtistNameById(id)?.let { Artist(id, it) }
}