package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(
        private val repo: ArtistsRepository,
        private val tracks: TracksRepository,
        private val settings: SettingsRepository) : ViewModel() {
    suspend fun getArtists() = repo.getAllArtists()

    /*suspend fun addToQueue(id: String) {
        val songs = tracks.getSongsForArtist(id)
        player.addToQueue(songs.getOrDefault(listOf()).map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder()
                    .setMediaId(it.id)
                    .setMediaMetadata(metadata)
                    .setUri("${settings.libraryHost.first()}/${it.file}".toUri())
                    .build()
        })
    }*/

    /*suspend fun playNext(id: String) {
        val songs = tracks.getSongsForArtist(id)
        player.playNext(songs.getOrDefault(listOf()).map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder()
                    .setMediaId(it.id)
                    .setMediaMetadata(metadata)
                    .setUri("${settings.libraryHost.first()}/${it.file}".toUri())
                    .build()
        })
    }*/
}
