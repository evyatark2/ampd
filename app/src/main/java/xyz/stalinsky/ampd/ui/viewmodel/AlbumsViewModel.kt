package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.ui.MpdConnectionState
import javax.inject.Inject

@HiltViewModel
class AlbumsViewModel @Inject constructor(
        repo: AlbumsRepository,
        private val tracks: TracksRepository,
        private val player: PlayerRepository) : ViewModel() {
    val albums = repo.getAllAlbums().map {
        it?.map {
            MpdConnectionState.Ok(it)
        }?.getOrElse {
            MpdConnectionState.Error(it)
        } ?: MpdConnectionState.Loading()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MpdConnectionState.Loading())

    suspend fun addToQueue(id: String) {
        val songs = tracks.getTracksForAlbum(id).first()
        player.addToQueue(songs?.getOrElse {
            listOf()
        }?.map {
            val metadata =
                    MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId).build()
            MediaItem.Builder().setMediaId(it.id).setMediaMetadata(metadata)
                    .setUri(Uri.parse("")).build()
        } ?: listOf())
    }

    suspend fun playNext(id: String) {
        val songs = tracks.getTracksForAlbum(id).first()
        player.playNext(songs?.getOrElse { listOf() }?.map {
                    val metadata =
                            MediaMetadata.Builder().setTitle(it.title).setArtist(it.artistId).setAlbumTitle(it.albumId)
                                    .build()
                    MediaItem.Builder().setMediaId(it.id).setMediaMetadata(metadata)
                            .setUri(Uri.parse("")).build()
                } ?: listOf())
    }
}