package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class ArtistViewModel @Inject constructor(handle: SavedStateHandle, private val mpd: MpdRepository, private val tracks: TracksRepository, private val artists: ArtistsRepository, val player: PlayerRepository, val settings: SettingsRepository) : ViewModel() {
    val id = handle.get<String>(ARTIST_ID_SAVED_STATE_KEY)!!
    val mpdHost = settings.mpdHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mpdPort = settings.mpdPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mpdTls = settings.mpdTls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val songs = tracks.getSongsForArtist(id).map {
        it?.map { MpdConnectionState.Ok(it) }?.getOrElse { MpdConnectionState.Error(it) } ?: MpdConnectionState.Loading()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MpdConnectionState.Loading())

    suspend fun connect(addr: SocketAddress?, tls: Boolean) {
        mpd.connect(addr, tls)
    }

    suspend fun getName(id: String) =
        artists.getArtistById(id)?.name

    companion object {
        private const val ARTIST_ID_SAVED_STATE_KEY = "id"
    }
}