package xyz.stalinsky.ampd.di

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.guava.await
import xyz.stalinsky.ampd.service.PlaybackService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControllerWrapper @Inject constructor(@ApplicationContext private val context: Context) {
    lateinit var mediaController: MediaController

    fun init(): ListenableFuture<MediaController> {
        val fut = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java)))
            .buildAsync()
        fut.addListener({
            mediaController = fut.get()
        }, context.mainExecutor)
        return fut
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ControllerModule {

    @Singleton
    @Provides
    fun provideMediaController(wrapper: MediaControllerWrapper): MediaController {
        return wrapper.mediaController
    }
}