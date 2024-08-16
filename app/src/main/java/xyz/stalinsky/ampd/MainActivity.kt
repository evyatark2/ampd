package xyz.stalinsky.ampd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import xyz.stalinsky.ampd.di.MediaControllerWrapper
import xyz.stalinsky.ampd.ui.Main
import xyz.stalinsky.ampd.ui.theme.AMPDTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var controller: MediaControllerWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runBlocking {
            controller.init().addListener({
                setContent {
                    AMPDTheme {
                        // A surface container using the 'background' color from the theme
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Main()
                        }
                    }
                }
            }, mainExecutor)
        }

    }
}