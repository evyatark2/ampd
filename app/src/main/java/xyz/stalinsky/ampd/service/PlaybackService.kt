package xyz.stalinsky.ampd.service

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.ConnectionResult.AcceptedResultBuilder
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()
        val player = ExoPlayer.Builder(this).setAudioAttributes(attributes, true).build()
        session = MediaSession.Builder(this, player).setCallback(Callback()).build()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }

        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return session
    }

    class Callback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
                session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            return AcceptedResultBuilder(session).build()
        }
    }
}