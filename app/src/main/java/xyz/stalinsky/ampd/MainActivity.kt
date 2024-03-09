package xyz.stalinsky.ampd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.UiConfigRepository
import xyz.stalinsky.ampd.data.settingsStore
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel
import xyz.stalinsky.ampd.ui.TopScreen
import xyz.stalinsky.ampd.ui.theme.AMPDTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repo = SettingsRepository(settingsStore)
        val uiRepo = UiConfigRepository(settingsStore)
        val viewModel = MainViewModel(repo, uiRepo)

        setContent {
            AMPDTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Main(viewModel)
                }
            }
        }
    }
}

@Composable
fun Main(viewModel: MainViewModel) {
    TopScreen(viewModel)
}