package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject
import androidx.core.net.toUri

@HiltViewModel
class ArtistViewModel @Inject constructor(
        handle: SavedStateHandle,
        private val tracks: TracksRepository,
        private val artists: ArtistsRepository,
        private val settings: SettingsRepository,
        private val player: PlayerRepository) : ViewModel() {
    val id = handle.get<String>(ARTIST_ID_SAVED_STATE_KEY)!!

    suspend fun getSongs() = tracks.getSongsForArtist(id)
    suspend fun getName() = artists.getArtistById(id).map { it.name }

    suspend fun setQueue(items: List<Pair<String, MediaItem.Builder>>, i: Int) {
        player.setQueue(items.map {
            it.second.setUri("${settings.libraryHost.first()}/${it.first}".toUri()).build()
        }, i)
    }

    companion object {
        private const val ARTIST_ID_SAVED_STATE_KEY = "id"
    }
}