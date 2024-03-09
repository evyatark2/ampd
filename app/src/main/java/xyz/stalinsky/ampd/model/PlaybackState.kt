package xyz.stalinsky.ampd.model

import java.lang.IllegalArgumentException

enum class PlaybackState {
    IDLE,
    BUFFERING,
    READY,
    ENDED;

    companion object {
        fun of(state: Int): PlaybackState {
            return when (state) {
                1 -> IDLE
                2 -> BUFFERING
                3 -> READY
                4 -> ENDED
                else -> throw IllegalArgumentException()
            }
        }
    }
}