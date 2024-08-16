package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.MpdRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.ui.MpdConnectionState
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(handle: SavedStateHandle, tracks: TracksRepository, private val artists: ArtistsRepository, private val player: PlayerRepository) : ViewModel() {
    val id = handle.get<String>(ARTIST_ID_SAVED_STATE_KEY)!!
    val songs = tracks.getSongsForArtist(id).map {
        it?.map { MpdConnectionState.Ok(it) }?.getOrElse { MpdConnectionState.Error(it) } ?: MpdConnectionState.Loading()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MpdConnectionState.Loading())

    suspend fun getName(id: String) =
        artists.getArtistById(id)?.name

    fun setQueue(items: List<MediaItem>, i: Int) {
        player.setQueue(items, i)
    }

    companion object {
        private const val ARTIST_ID_SAVED_STATE_KEY = "id"
    }
}