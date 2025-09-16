package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import xyz.stalinsky.ampd.data.GenresRespository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import javax.inject.Inject

@HiltViewModel
class GenresViewModel @Inject constructor(
        private val repo: GenresRespository,
        private val tracks: TracksRepository,
        private val settings: SettingsRepository) : ViewModel() {
    suspend fun getGenres() = repo.getAllGenres()

    /*suspend fun addToQueue(genre: String) {
        val songs = tracks.getSongsForGenre(genre)
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

    /*suspend fun playNext(genre: String) {
        val songs = tracks.getSongsForGenre(genre)
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