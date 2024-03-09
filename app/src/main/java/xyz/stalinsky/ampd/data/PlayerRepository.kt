package xyz.stalinsky.ampd.data

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.compose.material3.contentColorFor
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.guava.await
import xyz.stalinsky.ampd.service.PlaybackService

class PlayerRepository(context: Context) {
    private val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync()


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
                for (i in 0..< timeline.windowCount) {
                    val win = Timeline.Window()
                    timeline.getWindow(i, win)
                    list.add(win.mediaItem)
                }
                trySend(list.toList())
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
            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (old.mediaItemIndex != new.mediaItemIndex)
                    trySend(new.mediaItemIndex)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    trySend(controller.currentMediaItemIndex)
                }
            }
        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    val duration = callbackFlow {
        var updateDuration = false
        val controller = controller.await()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && updateDuration) {
                    val win = Timeline.Window()
                    controller.currentTimeline.getWindow(controller.currentMediaItemIndex, win)
                    Log.d("TAG", win.durationMs.toString())
                    trySend(win.durationMs)
                    updateDuration = false
                }
            }

            override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
                if (old.mediaItemIndex != new.mediaItemIndex) {
                    updateDuration = true
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val win = Timeline.Window()
                    controller.currentTimeline.getWindow(controller.currentMediaItemIndex, win)
                    Log.d("TAG", win.durationMs.toString())
                    trySend(win.durationMs)
                }
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

    suspend fun next() {
        val controller = controller.await()
        controller.seekToNext()
    }

    suspend fun previous() {
        val controller = controller.await()
        controller.seekToPrevious()
    }

    suspend fun seek(pos: Long) {
        val controller = controller.await()
        controller.seekTo(pos)
    }

    suspend fun go(i: Int) {
        val controller = controller.await()
        controller.seekToDefaultPosition(i)
    }

    suspend fun setQueue(items: List<MediaItem>, i: Int) {
        val controller = controller.await()
        controller.setMediaItems(items)
        controller.seekTo(i, 0)
        controller.playWhenReady = true
        controller.prepare()
    }

    suspend fun addToQueue(items: List<MediaItem>) {
        val controller = controller.await()
        controller.addMediaItems(items)
    }

    suspend fun progress(): Long {
        val controller = controller.await()
        return controller.currentPosition
    }
}