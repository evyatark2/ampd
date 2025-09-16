package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.MpdRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.UiConfigRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
        private val mpd: MpdRepository,
        settings: SettingsRepository,
        config: UiConfigRepository,
        private val player: PlayerRepository) : ViewModel() {
    val tabs = config.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())
    val defaultTab = config.defaultTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mpdHost = settings.mpdHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mpdPort = settings.mpdPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val mpdTls = settings.mpdTls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val loading = player.isLoading.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val playing = player.isPlaying.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val queue = player.queue.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val duration = player.duration.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun connect(addr: SocketAddress, tls: Boolean, onConnect: () -> Unit) {
        mpd.connect(addr, tls, onConnect)
    }

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.clearQueue()
    }

    fun seek(pos: Long) {
        player.seek(pos)
    }

    fun next() {
        player.next()
    }

    fun prev() {
        player.previous()
    }

    fun skipTo(i: Int) {
        player.skipTo(i)
    }

    fun progress(): Long {
        return player.progress()
    }
}