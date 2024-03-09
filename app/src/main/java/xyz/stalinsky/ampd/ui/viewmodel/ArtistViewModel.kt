package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Song

class ArtistViewModel(private val tracks: TracksRepository, private val artists: ArtistsRepository, val player: PlayerRepository) : ViewModel() {
    suspend fun getName(addr: SocketAddress, id: String) =
        artists.getArtistById(addr, id)?.name

    suspend fun getSongs(addr: SocketAddress, id: String) =
        tracks.getSongsForArtist(addr, id)
}