package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository

@HiltViewModel
class ArtistViewModel @AssistedInject constructor(
        @Assisted private val id: String,
        private val tracks: TracksRepository,
        private val artists: ArtistsRepository,
        private val settings: SettingsRepository) : ViewModel() {
    suspend fun getSongs() = tracks.getSongsForArtist(id)
    suspend fun getName() = artists.getArtistById(id).map { it.name }

    /*suspend fun setQueue(items: List<Pair<String, MediaItem.Builder>>, i: Int) {
        player.setQueue(items.map {
            it.second.setUri("${settings.libraryHost.first()}/${it.first}".toUri()).build()
        }, i)
    }*/
}