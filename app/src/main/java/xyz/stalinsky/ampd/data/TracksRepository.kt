package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.model.Track

class TracksRepository(private val mpd: MpdRemoteDataSource) {
    suspend fun getSongsForArtist(addr: SocketAddress, id: String): List<Song>? {
        return mpd.fetchArtistSongIds(addr, id)
    }

    suspend fun getTracksForAlbum(id: String): List<Track> {
        return listOf()
    }
}