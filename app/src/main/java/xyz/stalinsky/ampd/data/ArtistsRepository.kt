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
            val list: MutableList<Artist>?

            if (it != null) {
                list = mutableListOf()
                for (id in it) {
                    val name = mpd.fetchArtistNameById(id)
                    if (name != null) {
                        list.add(Artist(id, name))
                    } else {
                        return@map null
                    }
                }
                list.toList()
            } else {
                null
            }
        }

    suspend fun getArtistById(id: String): Artist? {
        val name = mpd.fetchArtistNameById(id)
        return if (name != null)
            Artist(id, name)
        else
            null
    }
}