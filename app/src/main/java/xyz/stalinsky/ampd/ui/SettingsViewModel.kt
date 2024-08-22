package xyz.stalinsky.ampd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.UiConfigRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
        private val settings: SettingsRepository, private val config: UiConfigRepository) : ViewModel() {
    val tabs = config.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())
    val defaultTab = config.defaultTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val libraryHost = settings.libraryHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val libraryPort = settings.libraryPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val mpdTls = settings.mpdTls.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val mpdHost = settings.mpdHost.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val mpdPort = settings.mpdPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun setLibraryHost(host: String) {
        settings.setLibraryHost(host)
    }

    suspend fun setLibraryPort(port: Int) {
        settings.setLibraryPort(port)
    }

    suspend fun toggleMpdTls() {
        settings.toggleMpdTls()
    }

    suspend fun setMpdHost(host: String) {
        settings.setMpdHost(host)
    }

    suspend fun setMpdPort(port: Int) {
        settings.setMpdPort(port)
    }
}