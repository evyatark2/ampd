package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class AlbumViewModel @Inject constructor(
        handle: SavedStateHandle,
        private val tracks: TracksRepository,
        private val albums: AlbumsRepository,
        private val player: PlayerRepository,
        val settings: SettingsRepository) : ViewModel() {
    val id = handle.get<String>(ALBUM_ID_SAVED_STATE_KEY)!!

    suspend fun getTracks() = tracks.getTracksForAlbum(id)

    suspend fun getTitle() = albums.getAlbumById(id).map { it.title }

    suspend fun setQueue(items: List<Pair<String, MediaItem.Builder>>, i: Int) {
        player.setQueue(items.map {
            it.second.setUri("${settings.libraryHost.first()}/${it.first}".toUri()).build()
        }, i)
    }

    companion object {
        private const val ALBUM_ID_SAVED_STATE_KEY = "id"
    }
}