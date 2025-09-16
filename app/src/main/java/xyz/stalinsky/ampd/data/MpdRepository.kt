package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpdRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun connect(addr: SocketAddress, tls: Boolean, onConnect: () -> Unit) {
        withContext(Dispatchers.IO) {
            mpd.setAddr(addr, tls, onConnect)
        }
    }
}