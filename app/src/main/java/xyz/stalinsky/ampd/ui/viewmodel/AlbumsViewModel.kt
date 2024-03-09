package xyz.stalinsky.ampd.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.model.Album

class AlbumsViewModel(private val repo: AlbumsRepository) : ViewModel() {
    suspend fun getAlbums(addr: SocketAddress) =
        repo.getAllAlbums(addr)
}