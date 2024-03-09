package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.MpdRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(handle: SavedStateHandle, private val mpd: MpdRepository, private val tracks: TracksRepository, private val albums: AlbumsRepository, val player: PlayerRepository, val settings: SettingsRepository) : ViewModel() {
    val id = handle.get<String>(ALBUM_ID_SAVED_STATE_KEY)!!
    val mpdTls = settings.mpdTls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val mpdHost = settings.mpdHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mpdPort = settings.mpdPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val trackList = tracks.getTracksForAlbum(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun connect(addr: SocketAddress?, tls: Boolean) {
        mpd.connect(addr, tls)
    }

    suspend fun getName(id: String) =
        albums.getAlbumById(id)?.title

    companion object {
        private const val ALBUM_ID_SAVED_STATE_KEY = "id"
    }
}