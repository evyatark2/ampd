package xyz.stalinsky.ampd.datasource

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import xyz.stalinsky.ampd.model.PlaybackState
import xyz.stalinsky.ampd.service.PlaybackService

class MediaSessionDataSource(context: Context) {
    private val mediaController =
            MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))

    fun playbackState(): Flow<PlaybackState> {
        return callbackFlow {
            val controller = mediaController.buildAsync().await()
            send(PlaybackState.of(controller.playbackState))
            while (true) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        trySend(PlaybackState.of(playbackState))
                    }
                }

                controller.addListener(listener)

                awaitClose {
                    controller.removeListener(listener)
                }
            }
        }
    }
}