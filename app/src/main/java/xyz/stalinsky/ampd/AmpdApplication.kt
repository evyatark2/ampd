package xyz.stalinsky.ampd

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.data.PlayerRepository
import xyz.stalinsky.ampd.data.SettingsRepository
import xyz.stalinsky.ampd.data.TracksRepository
import xyz.stalinsky.ampd.data.UiConfigRepository
import xyz.stalinsky.ampd.data.settingsStore
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel

class AmpdApplication : Application() {
    val mpd = MpdRemoteDataSource(MainScope())

    lateinit var settingRepo: SettingsRepository
    lateinit var uiConfigRepo: UiConfigRepository
    lateinit var albumRepo: AlbumsRepository
    lateinit var artistRepo: ArtistsRepository
    lateinit var trackRepo: TracksRepository
    lateinit var playerRepo: PlayerRepository

    override fun onCreate() {
        super.onCreate()
        settingRepo = SettingsRepository(settingsStore)
        uiConfigRepo = UiConfigRepository(settingsStore)
        albumRepo = AlbumsRepository(mpd)
        artistRepo = ArtistsRepository(mpd)
        trackRepo = TracksRepository(mpd)
        playerRepo = PlayerRepository(this)
    }
}