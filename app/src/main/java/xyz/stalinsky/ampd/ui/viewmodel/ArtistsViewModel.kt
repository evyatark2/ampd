package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import xyz.stalinsky.ampd.data.ArtistsRepository

class ArtistsViewModel(repo: ArtistsRepository) : ViewModel() {
    val artists = repo.getAllArtists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}