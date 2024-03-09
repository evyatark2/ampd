package xyz.stalinsky.ampd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import xyz.stalinsky.ampd.ui.Main
import xyz.stalinsky.ampd.ui.SettingsViewModel
import xyz.stalinsky.ampd.ui.TabsSettingViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel
import xyz.stalinsky.ampd.ui.theme.AMPDTheme
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AmpdApplication

        val mainViewModel: MainViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    MainViewModel(app.settingRepo, app.uiConfigRepo, app.playerRepo) as T
            }
        }

        val artistsViewModel: ArtistsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    ArtistsViewModel(app.artistRepo) as T
            }
        }

        val albumsViewModel: AlbumsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    AlbumsViewModel(app.albumRepo) as T
            }
        }

        val artistViewModel: ArtistViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    ArtistViewModel(app.trackRepo, app.artistRepo, app.playerRepo) as T
            }
        }

        val settingsViewModel: SettingsViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    SettingsViewModel(app.settingRepo, app.uiConfigRepo) as T
            }
        }

        val tabsViewModel: TabsSettingViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T =
                    TabsSettingViewModel(app.uiConfigRepo) as T
            }
        }

        setContent {
            AMPDTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Main(mainViewModel, artistsViewModel, albumsViewModel, artistViewModel, settingsViewModel, tabsViewModel)
                }
            }
        }
    }
}