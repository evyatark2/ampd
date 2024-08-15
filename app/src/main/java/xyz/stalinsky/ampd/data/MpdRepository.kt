package xyz.stalinsky.ampd.data

import io.ktor.network.sockets.SocketAddress
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpdRepository @Inject constructor(private val mpd: MpdRemoteDataSource) {
    suspend fun connect(addr: SocketAddress?, tls: Boolean) {
        mpd.connect(addr, tls)
    }
}