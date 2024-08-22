package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.ui.MpdConnectionState
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
        handle: SavedStateHandle,
        tracks: TracksRepository,
        private val albums: AlbumsRepository,
        private val player: PlayerRepository,
        val settings: SettingsRepository) : ViewModel() {
    val id = handle.get<String>(ALBUM_ID_SAVED_STATE_KEY)!!
    val trackList = tracks.getTracksForAlbum(id).map {
        it?.map { MpdConnectionState.Ok(it) }?.getOrElse { MpdConnectionState.Error(it) }
                ?: MpdConnectionState.Loading()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MpdConnectionState.Loading())

    suspend fun getTitle(id: String) = albums.getAlbumById(id)?.title

    suspend fun setQueue(items: List<Pair<String, MediaItem.Builder>>, i: Int) {
        player.setQueue(items.map { it.second.setUri(Uri.parse("${settings.libraryHost.first()}/${it.first}")).build() }, i)
    }

    companion object {
        private const val ALBUM_ID_SAVED_STATE_KEY = "id"
    }
}