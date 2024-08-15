package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistsRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    fun getAllArtists() =
        mpd.fetchArtistIds().map {
            it?.let {
                buildList {
                    for (id in it) {
                        val name = mpd.fetchArtistNameById(id)
                        if (name != null) {
                            add(Artist(id, name))
                        } else {
                            return@map null
                        }
                    }
                }
            }
        }

    suspend fun getArtistById(id: String) =
        mpd.fetchArtistNameById(id)?.let { Artist(id, it) }
}