package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.PlayerRepository

class PlayerViewModel(private val repo: PlayerRepository) : ViewModel() {
    val playing = repo.isPlaying.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentItem = repo.currentItem.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val queue = repo.queue.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun play() {
        repo.play()
    }

    suspend fun pause() {
        repo.pause()
    }
}