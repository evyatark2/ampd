package xyz.stalinsky.ampd.data

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSession
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.service.PlaybackService

class PlayerRepository(private val context: Context, scope: CoroutineScope) {
    private val controller = scope.async {
        MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync().await()
    }


    val isPlaying = callbackFlow {
        val controller = controller.await()

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                trySend(isPlaying)
            }

        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    val isLoading = callbackFlow {
        val controller = controller.await()

        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(isLoading: Boolean) {
                trySend(isLoading)
            }

        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    val queue = callbackFlow {
        val controller = controller.await()

        MediaItem.fromUri("")
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                val list = mutableListOf<MediaItem>()
                for (i in 0..timeline.windowCount) {
                    val win = Timeline.Window()
                    timeline.getWindow(0, win)
                    list.add(win.mediaItem)
                }
                trySend(list)
            }
        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    val currentItem = callbackFlow {
        val controller = controller.await()

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                trySend(mediaItem)
            }
        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    suspend fun play() {
        val controller = controller.await()
        controller.play()
    }

    suspend fun pause() {
        val controller = controller.await()
        controller.pause()
    }
}