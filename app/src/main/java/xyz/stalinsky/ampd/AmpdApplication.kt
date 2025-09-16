package xyz.stalinsky.ampd

import android.app.Application
import android.content.ComponentName
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.Provides
import dagger.hilt.android.HiltAndroidApp
import xyz.stalinsky.ampd.service.PlaybackService

@HiltAndroidApp
class AmpdApplication : Application()