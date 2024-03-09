package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.UiConfigRepository

class MainViewModel(settings: SettingsRepository, config: UiConfigRepository, private val player: PlayerRepository) : ViewModel() {
    val tabs = config.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())
    val defaultTab = config.defaultTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mpdHost = settings.mpdHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mpdPort = settings.mpdPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val loading = player.isLoading.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val playing = player.isPlaying.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val currentItem = player.currentItem.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)
    val queue = player.queue.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val duration = player.duration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun play() {
        player.play()
    }

    suspend fun pause() {
        player.pause()
    }

    suspend fun next() {
        player.next()
    }

    suspend fun prev() {
        player.previous()
    }

    suspend fun progress(): Long {
        return player.progress()
    }
}