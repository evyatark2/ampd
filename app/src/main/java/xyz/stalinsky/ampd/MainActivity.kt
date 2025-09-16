package xyz.stalinsky.ampd

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.di.MediaControllerWrapper
import xyz.stalinsky.ampd.service.PlaybackService
import xyz.stalinsky.ampd.ui.Main
import xyz.stalinsky.ampd.ui.theme.AMPDTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    val controller = flow {
        emit(MediaController.Builder(this@MainActivity,
                SessionToken(this@MainActivity, ComponentName(this@MainActivity, PlaybackService::class.java)))
                .buildAsync()
                .await())
    }.flowOn(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AMPDTheme { // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Main(controller)
                }
            }
        }
    }
}