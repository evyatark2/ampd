package xyz.stalinsky.ampd.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.model.Artist
import kotlin.coroutines.suspendCoroutine

class ArtistsViewModel(private val repo: ArtistsRepository) : ViewModel() {
    suspend fun getArtists(addr: SocketAddress) =
        repo.getAllArtists(addr)
}