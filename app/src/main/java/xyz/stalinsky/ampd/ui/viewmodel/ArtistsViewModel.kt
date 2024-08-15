package xyz.stalinsky.ampd.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.service.MpdClient
import xyz.stalinsky.ampd.ui.MpdConnectionState
import javax.inject.Inject

@HiltViewModel
class ArtistsViewModel @Inject constructor(repo: ArtistsRepository, private val tracks: TracksRepository, private val player: PlayerRepository) : ViewModel() {
    val artists =
        repo.getAllArtists()
            .map {
                if (it != null)
                    MpdConnectionState.Ok(it)
                else
                    MpdConnectionState.Loading()
            }.catch {
                this.emit(MpdConnectionState.Error(it))
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MpdConnectionState.Loading())

    suspend fun addToQueue(id: String) {
        val songs = tracks.getSongsForArtist(id).first()
        player.addToQueue(songs?.map {
            val metadata = MediaMetadata.Builder()
                .setTitle(it.title)
                .setArtist(it.artistId)
                .setAlbumTitle(it.albumId)
                .build()
            MediaItem.Builder()
                .setMediaId(it.id)
                .setMediaMetadata(metadata)
                .setUri(Uri.parse(""))
                .build()
        } ?: listOf())
    }

    suspend fun playNext(id: String) {
        val songs = tracks.getSongsForArtist(id).first()
        player.playNext(songs?.map {
            val metadata = MediaMetadata.Builder()
                .setTitle(it.title)
                .setArtist(it.artistId)
                .setAlbumTitle(it.albumId)
                .build()
            MediaItem.Builder()
                .setMediaId(it.id)
                .setMediaMetadata(metadata)
                .setUri(Uri.parse(""))
                .build()
        } ?: listOf())
    }
}
