package xyz.stalinsky.ampd.data

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(private val controller: MediaController) {
    val isPlaying = callbackFlow {
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
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                trySend(state != Player.STATE_READY)
            }
        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    val queue = callbackFlow {
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

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && updateDuration) {
                    val win = Timeline.Window()
                    controller.currentTimeline.getWindow(controller.currentMediaItemIndex, win)
                    trySend(win.durationMs)
                    updateDuration = false
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateDuration = true
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem != null) {
                    val win = Timeline.Window()
                    controller.currentTimeline.getWindow(controller.currentMediaItemIndex, win)
                    trySend(win.durationMs)
                }
            }
        }

        controller.addListener(listener)

        awaitClose {
            controller.removeListener(listener)
        }
    }

    fun play() {
        controller.play()
    }

    fun pause() {
        controller.pause()
    }

    fun next() {
        controller.seekToNext()
    }

    fun previous() {
        controller.seekToPrevious()
    }

    fun seek(pos: Long) {
        controller.seekTo(pos)
    }

    fun skipTo(i: Int) {
        controller.seekToDefaultPosition(i)
    }

    fun setQueue(items: List<MediaItem>, i: Int) {
        controller.stop()
        controller.setMediaItems(items)
        controller.seekTo(i, 0)
        controller.playWhenReady = true
        controller.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        controller.addMediaItems(items)
        controller.prepare()
    }

    fun playNext(items: List<MediaItem>) {
        controller.addMediaItems(controller.currentMediaItemIndex + 1, items)
        controller.prepare()
    }

    fun progress(): Long {
        return controller.currentPosition
    }
}