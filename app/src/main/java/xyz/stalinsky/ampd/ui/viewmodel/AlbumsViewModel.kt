package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.AlbumsRepository

class AlbumsViewModel(repo: AlbumsRepository) : ViewModel() {
    val albums = repo.getAllAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}