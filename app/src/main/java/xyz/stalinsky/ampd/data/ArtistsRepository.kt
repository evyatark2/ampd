package xyz.stalinsky.ampd.data

import android.util.Log
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Artist

class ArtistsRepository(private val mpd: MpdRemoteDataSource) {
    suspend fun getAllArtists(addr: SocketAddress): List<Artist>? {
        val list: MutableList<Artist>?
        val ids = mpd.fetchArtistIds(addr)

        return if (ids != null) {
            list = mutableListOf()
            for (id in ids) {
                val name = mpd.fetchArtistNameById(addr, id)
                if (name != null) {
                    list.add(Artist(id, name))
                } else {
                    return null
                }
            }
            list.toList()
        } else {
            null
        }
    }

    suspend fun getArtistById(addr: SocketAddress, id: String): Artist? {
        val name = mpd.fetchArtistNameById(addr, id)
        return if (name != null)
            Artist(id, name)
        else
            null
    }
}