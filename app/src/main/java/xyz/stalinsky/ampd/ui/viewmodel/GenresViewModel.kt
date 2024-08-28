package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import xyz.stalinsky.ampd.data.GenresRespository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject

@HiltViewModel
class GenresViewModel @Inject constructor(
        private val repo: GenresRespository,
        private val tracks: TracksRepository,
        private val settings: SettingsRepository,
        private val player: PlayerRepository) : ViewModel() {
    suspend fun getGenres() = repo.getAllGenres()

    suspend fun addToQueue(genre: String) {
        val songs = tracks.getSongsForGenre(genre)
        player.addToQueue(songs.getOrDefault(listOf()).map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder()
                    .setMediaId(it.id)
                    .setMediaMetadata(metadata)
                    .setUri(Uri.parse("${settings.libraryHost.first()}/${it.file}"))
                    .build()
        })
    }

    suspend fun playNext(genre: String) {
        val songs = tracks.getSongsForGenre(genre)
        player.playNext(songs.getOrDefault(listOf()).map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder()
                    .setMediaId(it.id)
                    .setMediaMetadata(metadata)
                    .setUri(Uri.parse("${settings.libraryHost.first()}/${it.file}"))
                    .build()
        })
    }
}
