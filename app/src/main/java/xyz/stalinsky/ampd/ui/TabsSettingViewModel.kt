package xyz.stalinsky.ampd.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.data.UiConfigRepository
import javax.inject.Inject

@HiltViewModel
class TabsSettingViewModel @Inject constructor(private val config: UiConfigRepository) : ViewModel() {
    val tabs = config.tabs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf())
    val defaultTab = config.defaultTab.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    suspend fun moveTab(src: Int, dst: Int) {
        val tabs = mutableListOf<Settings.Tab>()
        tabs.addAll(this.tabs.value)
        val temp = tabs[src]
        tabs[src] = tabs[dst]
        tabs[dst] = temp

        config.setTabs(tabs)
    }

    suspend fun toggleTab(index: Int) {
        val tab = tabs.value[index]
        val newTab = tab.toBuilder().setEnabled(!tab.enabled).build()
        val tabs = mutableListOf<Settings.Tab>()
        tabs.addAll(this.tabs.value)
        tabs[index] = newTab

        config.setTabs(tabs)
    }

    suspend fun setDefaultTab(tab: Int) {
        config.setDefaultTab(tab)
    }
}