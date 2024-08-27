package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import okio.Buffer
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.model.Album
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
        private val repo: AlbumsRepository,
        private val tracks: TracksRepository,
        private val settings: SettingsRepository,
        private val player: PlayerRepository) : ViewModel() {
    suspend fun getAlbums(): Result<List<Album>> = repo.getAllAlbums()

    suspend fun getAlbumArt(id: String, offset: Long, buf: Buffer) = repo.getAlbumArt(id, offset, buf)

    suspend fun addToQueue(id: String) {
        val songs = tracks.getTracksForAlbum(id)
        player.addToQueue(songs.getOrElse {
            listOf()
        }.map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder()
                    .setMediaId(it.id)
                    .setMediaMetadata(metadata)
                    .setUri(Uri.parse("${settings.libraryHost.first()}/${it.file}"))
                    .build()
        })
    }

    suspend fun playNext(id: String) {
        val songs = tracks.getTracksForAlbum(id)
        player.playNext(songs.getOrElse { listOf() }.map {
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