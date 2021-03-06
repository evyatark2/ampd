@file:Suppress("NAME_SHADOWING")
package xyz.stalinsky.ampd

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.LocalOverScrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.coroutineScope
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.session.*
import androidx.preference.PreferenceManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.google.common.util.concurrent.Futures
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.ui.SingleLineText
import xyz.stalinsky.ampd.ui.PlayerSheet
import xyz.stalinsky.ampd.ui.theme.AMPDTheme
import java.util.Stack
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()

    private val playerState = PlayerState()

    private var updateSongProgressJob: Job? = null
    private var updateBufferedProgressJob: Job? = null

    private lateinit var controller: MediaBrowser

    private val connectionState = MutableStateFlow(MusicService.ConnectionState.DISCONNECTED)

    // A map between media IDs and the screens that use it
    private val screens: MutableMap<String, MutableList<Screen>> = hashMapOf()
    private val backstack = Stack<Screen>()

    private val mainScreen = Screen.MainScreen {
        controller.sendCustomCommand(MusicService.COMMAND_CONNECT, null)
    }
    private val screenState: MutableStateFlow<Screen> = MutableStateFlow(mainScreen)

    private var mpdHost = ""
    private var mpdPort = -1
    private var mediaLibrary = ""

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        val onValueChanged = {
            controller.sendCustomCommand(MusicService.COMMAND_SET_MPD_ADDRESS, Bundle().apply {
                putString(MusicService.COMMAND_ARG_MPD_HOST, mpdHost)
                putInt(MusicService.COMMAND_ARG_MPD_PORT, mpdPort)
            }).addListener({
                controller.sendCustomCommand(MusicService.COMMAND_CONNECT, null).get()
            }, executor)
        }

        when (key) {
            "mpd_host_preference" -> {
                mpdHost = sharedPreferences.getString(key, "") ?: ""
                onValueChanged()
            }

            "mpd_port_preference" -> {
                mpdPort = sharedPreferences.getString(key, "")?.toIntOrNull() ?: -1
                onValueChanged()
            }

            "media_library_host_preference" -> {
                mediaLibrary = sharedPreferences.getString(key, "") ?: ""
                controller.sendCustomCommand(MusicService.COMMAND_SET_MEDIA_LIBRARY, Bundle().apply {
                    putString(MusicService.COMMAND_ARG_MEDIA_LIBRARY, mediaLibrary)
                })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = PreferenceManager.getDefaultSharedPreferences(this)
        manager.registerOnSharedPreferenceChangeListener(listener)
        mpdHost = manager.getString("mpd_host_preference", "")!!
        mpdPort = manager.getString("mpd_port_preference", "")!!.toIntOrNull() ?: -1
        mediaLibrary = manager.getString("media_library_host_preference", "")!!

        controller = MediaBrowser.Builder(this)
            .setSessionToken(SessionToken(this, ComponentName(this, MusicService::class.java)))
            .setControllerCallback(executor, Callback())
            .build()

        setContent {
            AMPDTheme {
                Surface(color = MaterialTheme.colors.background) {
                    Main(connectionState, {
                        val screen = screenState.value
                        if (screen is Screen.ArtistScreen) {
                            controller.sendCustomCommand(MusicService.COMMAND_PUT_CHILDREN, Bundle().apply {
                                putString(MusicService.COMMAND_ARG_PARENT_ID, screen.id)
                            }).addListener({
                                controller.unsubscribe(screen.id)
                            }, executor)
                        }

                        if (screen is Screen.AlbumScreen) {
                            controller.sendCustomCommand(MusicService.COMMAND_PUT_CHILDREN, Bundle().apply {
                                putString(MusicService.COMMAND_ARG_PARENT_ID, screen.id)
                            }).addListener({
                                controller.unsubscribe(screen.id)
                            }, executor)
                        }

                        screenState.value = backstack.pop()
                    }, screenState, playerState, { playlist, index ->
                        controller.setPlaylist(playlist, null).addListener({
                            controller.skipToPlaylistItem(index).addListener({
                                if (controller.playerState == SessionPlayer.PLAYER_STATE_IDLE) {
                                    controller.prepare().addListener({
                                        controller.play()
                                    }, executor)
                                } else if (controller.playerState == SessionPlayer.PLAYER_STATE_PAUSED) {
                                    controller.play()
                                }
                            }, executor)
                        }, executor)
                    }, {
                        controller.skipToPlaylistItem(it)
                    }, {
                        if (playerState.state.value == SessionPlayer.PLAYER_STATE_IDLE)
                            controller.prepare().addListener({
                                controller.play()
                            }, executor)
                        else if (playerState.state.value == SessionPlayer.PLAYER_STATE_PAUSED)
                            controller.play()
                        else if (playerState.state.value == SessionPlayer.PLAYER_STATE_PLAYING)
                            controller.pause()
                    }, { // onSkipToPrevious
                        controller.skipToPreviousPlaylistItem()
                    }, { // onSkipToNext
                        controller.skipToNextPlaylistItem()
                    }, { // onSeekStart
                        updateSongProgressJob?.cancel()
                        updateSongProgressJob = null
                    }, { // onSeek
                        playerState.setProgress(it) // Immediately assign a temporary value before the service finalizes the decision in onSeekComlete()
                        controller.seekTo(it)
                    }) {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.close()
        executor.shutdown()

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(listener)
    }

    inner class Callback : MediaBrowser.BrowserCallback() {
        override fun onConnected(controller: MediaController, allowedCommands: SessionCommandGroup) {
            playerState.setState(controller.playerState)
            playerState.setPlaylist(controller.playlist?.map {
                Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))))
            } ?: listOf())

            playerState.setCurrentIndex(controller.currentMediaItemIndex)

            controller.sendCustomCommand(MusicService.COMMAND_SET_MEDIA_LIBRARY,
                Bundle().apply { putString(MusicService.COMMAND_ARG_MEDIA_LIBRARY, mediaLibrary) }).addListener({
                controller.sendCustomCommand(MusicService.COMMAND_SET_MPD_ADDRESS, Bundle().apply {
                    putString(MusicService.COMMAND_ARG_MPD_HOST, mpdHost)
                    putInt(MusicService.COMMAND_ARG_MPD_PORT, mpdPort)
                }).addListener({
                    controller.sendCustomCommand(MusicService.COMMAND_CONNECT, null).get()
                }, executor)
            }, executor)
        }

        override fun onDisconnected(controller: MediaController) {
            Log.i("MainActivity.Callback", "Disconnected")
        }

        override fun onCurrentMediaItemChanged(controller: MediaController, item: MediaItem?) {
            playerState.setCurrentIndex(controller.currentMediaItemIndex)
            playerState.setProgress(0L)
        }

        override fun onSeekCompleted(controller: MediaController, position: Long) {
            playerState.setProgress(position)
            if (playerState.state.value == SessionPlayer.PLAYER_STATE_PLAYING)
                startSongProgressUpdate()
        }

        override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle?): SessionResult {
            when (command) {
                MusicService.COMMAND_MPD_CONNECTION_STATUS_CHANGED -> {
                    when (args!!.getParcelable<MusicService.ConnectionState>(MusicService.COMMAND_ARG_MPD_CONNECTION_STATUS)!!) {
                        MusicService.ConnectionState.CONNECTED -> {
                            connectionState.value = MusicService.ConnectionState.CONNECTED
                            val root = (controller as MediaBrowser).getLibraryRoot(null).get().mediaItem?.metadata?.mediaId!!
                            controller.subscribe(root, null).addListener({
                                onChildrenChanged(controller, root, 0, null)
                            }, executor)
                        }

                        MusicService.ConnectionState.CONNECTING -> {
                            connectionState.value = MusicService.ConnectionState.CONNECTING
                        }

                        MusicService.ConnectionState.DISCONNECTED -> {
                            connectionState.value = MusicService.ConnectionState.DISCONNECTED
                            var screen = screenState.value
                            while (backstack.isNotEmpty()) {
                                if (screen is Screen.AlbumScreen)
                                    (controller as MediaBrowser).unsubscribe(screen.id)
                                else if (screen is Screen.ArtistScreen)
                                    (controller as MediaBrowser).unsubscribe(screen.id)

                                screen = backstack.pop()
                            }

                            screenState.value = mainScreen
                        }
                    }
                }
            }

            return SessionResult(SessionResult.RESULT_SUCCESS, null)
        }

        override fun onPlaylistChanged(controller: MediaController, list: MutableList<MediaItem>?, metadata: MediaMetadata?) {
            playerState.setPlaylist(list?.map {
                Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))))
            } ?: listOf())
        }

        override fun onPlayerStateChanged(controller: MediaController, state: Int) {
            when (state) {
                SessionPlayer.PLAYER_STATE_PAUSED -> {
                    if (playerState.state.value == SessionPlayer.PLAYER_STATE_PLAYING) {
                        updateSongProgressJob?.cancel()
                        updateSongProgressJob = null
                    }
                }

                SessionPlayer.PLAYER_STATE_PLAYING -> {
                    startSongProgressUpdate()
                }
            }

            playerState.setState(state)
        }

        override fun onBufferingStateChanged(controller: MediaController, item: MediaItem, state: Int) {
            if ((state == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE || state == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_STARVED)) {
                updateBufferedProgressJob = lifecycle.coroutineScope.launch {
                    while (true) {
                        // 24 fps
                        delay(42)
                        playerState.setBufferedProgress(controller.bufferedPosition)
                    }
                }
            } else if (state == SessionPlayer.BUFFERING_STATE_COMPLETE) {
                updateBufferedProgressJob?.cancel()
                playerState.setBufferedProgress(controller.bufferedPosition)
                updateBufferedProgressJob = null
            }
        }

        @SuppressLint("RestrictedApi")
        override fun onChildrenChanged(browser: MediaBrowser, parentId: String, itemCount: Int, params: MediaLibraryService.LibraryParams?) {
            val result = browser.getChildren(parentId, 0, Int.MAX_VALUE, null).get()
            if (result.resultCode != LibraryResult.RESULT_SUCCESS)
                return

            val children = result.mediaItems!!

            when {
                parentId == "/" -> {
                    val tabs = mutableListOf<Screen.MainScreen.Tab>()
                    children.forEach {
                        when (it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!) {
                            "/artists" -> {
                                tabs.add(Screen.MainScreen.Tab.ArtistsTab {
                                    val pair = artists.value!![it]
                                    val id = pair.first
                                    val artist = pair.second
                                    backstack.push(screenState.value)
                                    val screen = Screen.ArtistScreen(id, artist.name)
                                    screenState.value = screen
                                    browser.subscribe(id, null).addListener({
                                        if (screens.contains(id))
                                            screens[id]!!.add(screen)
                                        else
                                            screens[id] = mutableListOf(screen)
                                        onChildrenChanged(browser, id, 0, null)
                                    }, executor)
                                })
                            }

                            "/albums" -> {
                                tabs.add(Screen.MainScreen.Tab.AlbumsTab {
                                    backstack.push(screenState.value)
                                    val pair = albums.value!![it]
                                    val id = pair.first
                                    val album = pair.second
                                    val screen = Screen.AlbumScreen(id,
                                        album.title,
                                        album.art, {
                                            val current = browser.currentMediaItemIndex
                                            // TODO: Lock the executor from running other playlist-mutating runnables while this future is running
                                            var future = browser.addPlaylistItem(current + 1, (screenState.value as Screen.AlbumScreen).tracks.value!!.last().first)
                                            (screenState.value as Screen.AlbumScreen).tracks.value?.asReversed()?.drop(1)?.forEach { track ->
                                                future = Futures.transformAsync(future, {
                                                    browser.addPlaylistItem(current + 1, track.first)
                                                }, executor)
                                            }
                                            future.addListener({
                                                if (current == -1)
                                                    browser.play().get()
                                            }, executor)
                                        }, { // onAddAlbumToQueue
                                            var future = browser.addPlaylistItem(browser.playlist?.size ?: 0, (screenState.value as Screen.AlbumScreen).tracks.value!!.first().first)
                                            (screenState.value as Screen.AlbumScreen).tracks.value?.drop(1)?.forEach { track ->
                                                future = Futures.transformAsync(future, {
                                                    browser.addPlaylistItem(browser.playlist?.size ?: 0, track.first)
                                                }, executor)
                                            }
                                        }, { // onPlayNext
                                            val current = browser.currentMediaItemIndex
                                            browser.addPlaylistItem(current + 1, (screenState.value as Screen.AlbumScreen).tracks.value!![it].first).addListener({
                                                if (current == -1)
                                                    browser.play().get()
                                            }, executor)
                                        }, { // onAddToQueue
                                            browser.addPlaylistItem(browser.playlist?.size ?: 0, (screenState.value as Screen.AlbumScreen).tracks.value!![it].first).get()
                                        }, { // onGoToArtist
                                            backstack.push(screenState.value)
                                            val id = (screenState.value as Screen.AlbumScreen).tracks.value!![it].second.artistId
                                            val screen = Screen.ArtistScreen(id, (screenState.value as Screen.AlbumScreen).tracks.value!![it].second.artist)
                                            screenState.value = screen
                                            browser.subscribe(id, null).addListener({
                                                if (screens.contains(id))
                                                    screens[id]!!.add(screen)
                                                else
                                                    screens[id] = mutableListOf(screen)
                                                onChildrenChanged(browser, id, 0, null)
                                            }, executor)
                                        })
                                    screenState.value = screen
                                    browser.subscribe(id, null).addListener({
                                        if (screens.contains(id))
                                            screens[id]!!.add(screen)
                                        else
                                            screens[id] = mutableListOf(screen)
                                        onChildrenChanged(browser, id, 0, null)
                                    }, executor)
                                })
                            }
                        }
                    }

                    mainScreen.setTabs(tabs)

                    for (child in children) {
                        val id = child.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                        browser.subscribe(id, null).addListener({
                            onChildrenChanged(browser, id, 0, null)
                        }, executor)
                    }
                }

                parentId == "/artists" -> {
                    for (tab in mainScreen.tabs.value!!) {
                        if (tab is Screen.MainScreen.Tab.ArtistsTab) {
                            tab.setArtists(children.map {
                                Pair(it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!, Artist(it.metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE)!!))
                            })
                        }
                    }
                }

                parentId == "/albums" -> {
                    for (tab in mainScreen.tabs.value!!) {
                        if (tab is Screen.MainScreen.Tab.AlbumsTab) {
                            tab.setAlbums(children.map {
                                Pair(it.metadata!!.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!, Album(it.metadata!!.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)))
                            })
                        }
                    }
                }

                parentId.startsWith("/artists") -> {
                    val children = children.map {
                        Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                            Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)))
                    }

                    for (screen in screens[parentId]!!)
                        (screen as Screen.ArtistScreen).setSongs(children)
                }

                parentId.startsWith("/albums") -> {
                    val children = children.map {
                        Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                            Track(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER)!!.toInt(),
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)!!.toInt()))
                    }

                    for (screen in screens[parentId]!!)
                        (screen as Screen.AlbumScreen).setTracks(children)
                }
            }
        }

        private fun startSongProgressUpdate() {
            updateSongProgressJob = lifecycle.coroutineScope.launch {
                while (true) {
                    // 24 fps
                    delay(42)
                    playerState.setProgress(controller.currentPosition)
                }
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class, ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun Main(connectionFlow: StateFlow<MusicService.ConnectionState>,
         onBackPressed: () -> Unit,
         screenFlow: StateFlow<Screen>,
         playerState: PlayerState,
         setPlaylist: (List<String>, Int) -> Unit,
         setCurrentItemIndex: (Int) -> Unit,
         onPlayPause: () -> Unit,
         onPrev: () -> Unit,
         onNext: () -> Unit,
         onSeekStart: () -> Unit,
         onSeek: (Long) -> Unit,
         onSettings: () -> Unit) {
    val screen = screenFlow.collectAsState().value
    BackHandler(screen !is Screen.MainScreen, onBackPressed)

    val connectionState by connectionFlow.collectAsState()
    val playingState by playerState.state.collectAsState()
    val playerVisible = playerState.current.collectAsState().value != -1

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState()
    val swipeState = rememberSwipeableState(false)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val transition = updateTransition(playerVisible, label = "")
        val playerHeight = transition.animateDp({ tween() }, label = "") {
            if (it)
                72.dp
            else
                0.dp
        }

        val swipeOffsetDp = with(LocalDensity.current) { swipeState.offset.value.toDp() }

        Box(Modifier.fillMaxSize().padding(bottom = playerHeight.value)) {
            when (screen) {
                is Screen.MainScreen -> Scaffold(Modifier.fillMaxSize(), topBar = {
                    TopAppBar(title = {
                        Text(stringResource(R.string.app_name))
                    }, actions = {
                        var expanded by remember { mutableStateOf(false) }

                        Box(Modifier.padding(end = 16.dp)) {
                            IconButton({
                                expanded = true
                            }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }

                            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem({
                                    expanded = false
                                    onSettings()
                                }) {
                                    Text("Settings")
                                }
                            }
                        }
                    })
                }) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        when (connectionState) {
                            MusicService.ConnectionState.CONNECTED -> {
                                val tabs = screen.tabs.collectAsState().value
                                if (tabs != null) {
                                    Column(Modifier.fillMaxSize()) {
                                        TabRow(selectedTabIndex = pagerState.currentPage, indicator = {
                                            TabRowDefaults.Indicator(Modifier.pagerTabIndicatorOffset(pagerState, it))
                                        }) {
                                            tabs.forEachIndexed { i, tab ->
                                                Tab(selected = i == pagerState.currentPage, onClick = {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(i)
                                                    }
                                                }, text = { Text(tab.name) })
                                            }
                                        }

                                        HorizontalPager(count = tabs.size, state = pagerState) { page ->
                                            val tab = tabs[page]
                                            if (tab is Screen.MainScreen.Tab.ArtistsTab) {
                                                val list = tab.artists.collectAsState().value
                                                if (list != null) {
                                                    LazyColumn(Modifier.fillMaxSize()) {
                                                        items(list.size) {
                                                            ArtistView(list[it].second) {
                                                                tab.onClick(tab, it)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                            } else if (tab is Screen.MainScreen.Tab.AlbumsTab) {
                                                val list = tab.albums.collectAsState().value
                                                if (list != null) {
                                                    LazyColumn(Modifier.fillMaxSize()) {
                                                        items(list.size) {
                                                            AlbumView(list[it].second) {
                                                                tab.onClick(tab, it)
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                                                        CircularProgressIndicator()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            MusicService.ConnectionState.CONNECTING -> {
                                CircularProgressIndicator()
                            }
                            MusicService.ConnectionState.DISCONNECTED -> {
                                Button(screen.retryConnection) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                is Screen.ArtistScreen -> Column {
                    TopAppBar(title = {
                        Text(screen.artistName)
                    }, navigationIcon = {
                        IconButton(onBackPressed) {
                            Icon(Icons.Default.ArrowBack, "")
                        }

                    }, actions = {
                        var expanded by remember { mutableStateOf(false) }

                        Box(Modifier.padding(end = 16.dp)) {
                            IconButton({
                                expanded = true
                            }) {
                                Icon(Icons.Default.MoreVert, "More")
                            }

                            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem({
                                    expanded = false
                                    onSettings()
                                }) {
                                    Text("Settings")
                                }
                            }
                        }
                    })
                    val songs = screen.songs.collectAsState().value
                    if (songs != null) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(songs.size) {
                                ConstraintLayout(Modifier.fillMaxWidth().height(72.dp).clickable {
                                    setPlaylist(songs.map { it.first }, it)
                                }) {
                                    val (titleConstraint) = createRefs()

                                    SingleLineText(songs[it].second.title, Modifier.constrainAs(titleConstraint) {
                                        top.linkTo(parent.top)
                                        bottom.linkTo(parent.bottom)
                                        start.linkTo(parent.start, 16.dp)
                                        end.linkTo(parent.end, 16.dp)

                                        width = Dimension.fillToConstraints
                                    })
                                }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }

                is Screen.AlbumScreen -> {
                    val expandedHeight = 372.dp
                    val redactedHeight = 56.dp

                    val heightDifference = expandedHeight - redactedHeight

                    val minOffset = with(LocalDensity.current) {
                        -heightDifference.toPx()
                    }

                    val scrollState = rememberScrollState()

                    val imageHeight = with(LocalDensity.current) { expandedHeight.toPx().roundToInt() }

                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val imageWidth = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }
                        val offset = max(minOffset, -scrollState.value.toFloat())
                        val offsetProgress = min(0f, offset * 3f - minOffset * 2f) / (minOffset)
                        TopAppBar(Modifier.fillMaxWidth().offset { IntOffset(0, offset.roundToInt()) }.height(expandedHeight),
                            elevation = if (offset <= minOffset) 4.dp else 0.dp) {
                            Box(Modifier.fillMaxSize()) {
                                GlideImage(screen.art, Modifier.fillMaxSize().alpha(1f - offsetProgress), requestOptions = {
                                    RequestOptions().override(imageWidth, imageHeight).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                                }, contentScale = ContentScale.Crop)

                                SingleLineText(screen.albumTitle,
                                    Modifier.align(Alignment.BottomStart)
                                        .paddingFromBaseline(bottom = 20.dp)
                                        .padding(start = (16 + 56 * offsetProgress).dp)
                                        .alpha(offsetProgress),
                                    style = MaterialTheme.typography.h6)

                            }
                        }

                        ConstraintLayout(Modifier.fillMaxWidth().height(56.dp)) {
                            val (backConstraint, moreConstraint) = createRefs()

                            IconButton(onBackPressed, Modifier.constrainAs(backConstraint) {
                                start.linkTo(parent.start, 16.dp)
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)

                                //width = Dimension.value(24.dp)
                                //height = 24.dp
                            }) {
                                Icon(Icons.Default.ArrowBack, "")
                            }

                            Box(Modifier.constrainAs(moreConstraint) {
                                end.linkTo(parent.end, 16.dp)
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)

                                //width = Dimension.value(24.dp)
                                //height = 24.dp
                            }) {
                                var expanded by remember { mutableStateOf(false) }

                                IconButton({
                                    expanded = true
                                }) {
                                    Icon(Icons.Default.MoreVert, "")
                                }

                                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem({
                                        screen.onPlayAlbumNext()
                                        expanded = false
                                    }) {
                                        Text("Play next")
                                    }
                                    DropdownMenuItem({
                                        screen.onAddAlbumToQueue()
                                        expanded = false
                                    }) {
                                        Text("Add to queue")
                                    }
                                }
                            }
                        }

                        val tracks = screen.tracks.collectAsState().value
                        if (tracks != null) {
                            var showDisc = false
                            for (track in tracks) {
                                if (track.second.disc != 1) {
                                    showDisc = true
                                    break
                                }
                            }

                            CompositionLocalProvider(LocalOverScrollConfiguration provides null) {
                                Column(Modifier.fillMaxSize().padding(top = redactedHeight).verticalScroll(scrollState)) {
                                    Spacer(Modifier.height(heightDifference))
                                    tracks.forEachIndexed { i, track ->
                                        TrackView(track.second, showDisc, { screen.onPlayNext(i) }, { screen.onAddToQueue(i) }, { screen.onGoToArtist(i) }, Modifier.clickable {
                                            setPlaylist(tracks.map { it.first }, i)
                                        })
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }

        val justOffScreen = with(LocalDensity.current) { 72.dp.roundToPx() }

        var extended by remember { mutableStateOf(false) }
        val extendTransition = updateTransition(extended, "")
        val height = extendTransition.animateDp(label = "label1") {
            if (it)
                maxHeight
            else
                playerHeight.value - swipeOffsetDp
        }

        transition.AnimatedVisibility({ it },
            enter = slideInVertically(tween()) { justOffScreen },
            exit = slideOutVertically(tween()) { justOffScreen },
            modifier = Modifier.align(Alignment.BottomCenter)) {
            val playlist by playerState.playlist.collectAsState()
            val current by playerState.current.collectAsState()
            val progress by playerState.progress.collectAsState()
            PlayerSheet(playingState,
                playlist,
                current,
                setCurrentItemIndex,
                { _, _ -> },
                progress,
                swipeState,
                onPrev,
                onPlayPause,
                onNext,
                onSeekStart,
                onSeek,
                extendTransition, {
                    extended = it
                }, Modifier.height(height.value))
        }
    }
}

@Composable
fun ArtistView(artist: Artist, onClick: () -> Unit) {
    ConstraintLayout(Modifier.clickable(onClick = onClick).height(48.dp).fillMaxWidth()) {
        val (constraint) = createRefs()

        SingleLineText(artist.name, Modifier.constrainAs(constraint) {
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start, 16.dp)
        }, style = MaterialTheme.typography.subtitle1)
    }
}

@Composable
fun AlbumView(album: Album, onClick: () -> Unit) {
    ConstraintLayout(Modifier.clickable(onClick = onClick).height(72.dp).fillMaxWidth()) {
        val (artConstraint, titleConstraint, artistConstraint) = createRefs()

        GlideImage(album.art, Modifier.constrainAs(artConstraint) {
            top.linkTo(parent.top)
            bottom.linkTo(parent.bottom)
            start.linkTo(parent.start, 16.dp)

            width = Dimension.value(40.dp)
            height = Dimension.value(40.dp)
        }, requestOptions = {
            RequestOptions().override(with(LocalDensity.current) { 40.dp.toPx().roundToInt() }).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        }, contentScale = ContentScale.Crop)

        SingleLineText(album.title, Modifier.paddingFromBaseline(28.dp).constrainAs(titleConstraint) {
            top.linkTo(parent.top)
            start.linkTo(artConstraint.end, 16.dp)
            end.linkTo(parent.end, 16.dp)

            width = Dimension.fillToConstraints
        }, style = MaterialTheme.typography.subtitle1)

        SingleLineText(album.artist, Modifier.paddingFromBaseline(48.dp).constrainAs(artistConstraint) {
            top.linkTo(parent.top)
            start.linkTo(artConstraint.end, 16.dp)
            end.linkTo(parent.end, 16.dp)

            width = Dimension.fillToConstraints
        }, style = MaterialTheme.typography.caption)
    }
}

@Composable
fun TrackView(track: Track, showDisc: Boolean, onPlayNext: () -> Unit, onAddToQueue: () -> Unit, onGoToArtist: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(88.dp)) {
        ConstraintLayout(modifier.fillMaxSize()) {
            val (trackConstraint, titleConstraint, artistConstraint, buttonConstraint) = createRefs()

            SingleLineText(if (showDisc) "${track.disc}-${track.track}" else track.track.toString(), Modifier.constrainAs(trackConstraint) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start, 16.dp)
            }, style = MaterialTheme.typography.subtitle2)

            SingleLineText(track.title, Modifier.paddingFromBaseline(40.dp).constrainAs(titleConstraint) {
                top.linkTo(parent.top)
                start.linkTo(trackConstraint.end, 16.dp)
                end.linkTo(buttonConstraint.start, 16.dp)

                width = Dimension.fillToConstraints
            }, style = MaterialTheme.typography.subtitle1)

            SingleLineText(track.artist, Modifier.paddingFromBaseline(60.dp).constrainAs(artistConstraint) {
                top.linkTo(parent.top)
                start.linkTo(trackConstraint.end, 16.dp)
                end.linkTo(buttonConstraint.start, 16.dp)

                width = Dimension.fillToConstraints
            }, style = MaterialTheme.typography.caption)

            Box(Modifier.constrainAs(buttonConstraint) {
                    top.linkTo(parent.top)
                    bottom.linkTo(parent.bottom)
                    end.linkTo(parent.end, 16.dp)
                }) {

                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = {
                    expanded = true
                }, ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More")
                }

                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem({
                        onPlayNext()
                        expanded = false
                    }) {
                        Text("Play next")
                    }
                    DropdownMenuItem({
                        onAddToQueue()
                        expanded = false
                    }) {
                        Text("Add to queue")
                    }
                    DropdownMenuItem({
                        onGoToArtist()
                        expanded = false
                    }) {
                        Text("Go to artist")
                    }
                }
            }
        }
    }
}

sealed interface Screen {
    // categories is a map that represents the main view window
    // The key is the mediaId of a root mediaItem e.g. "/artists"
    // The value is a pair where first is the name of the category e.g. "Artists"
    // and second is a list of composeables used to render the list e.g.
    // listOf(ArtistView("Beethoven"), ArtistView("The Beatles"))
    class MainScreen(val retryConnection: () -> Unit) : Screen {
        private val tabs_: MutableStateFlow<List<Tab>?> = MutableStateFlow(null)
        val tabs: StateFlow<List<Tab>?> = tabs_

        fun setTabs(tabs: List<Tab>?) {
            tabs_.value = tabs
        }

        sealed class Tab(val name: String) {

            class ArtistsTab(val onClick: ArtistsTab.(Int) -> Unit) : Tab("Artists") {
                private val artists_: MutableStateFlow<List<Pair<String, Artist>>?> = MutableStateFlow(null)
                val artists: StateFlow<List<Pair<String, Artist>>?> = artists_

                fun setArtists(artists: List<Pair<String, Artist>>) {
                    artists_.value = artists
                }                }

            class AlbumsTab(val onClick: AlbumsTab.(Int) -> Unit) : Tab("Albums") {
                private val albums_: MutableStateFlow<List<Pair<String, Album>>?> = MutableStateFlow(null)
                val albums: StateFlow<List<Pair<String, Album>>?> = albums_

                fun setAlbums(albums: List<Pair<String, Album>>) {
                    albums_.value = albums
                }
            }
        }
    }

    class ArtistScreen(val id: String, val artistName: String) : Screen {
        private val songs_: MutableStateFlow<List<Pair<String, Song>>?> = MutableStateFlow(null)
        val songs: StateFlow<List<Pair<String, Song>>?> = songs_

        private val albums_: MutableStateFlow<List<Pair<String, Album>>?> = MutableStateFlow(null)
        val albums: StateFlow<List<Pair<String, Album>>?> = albums_

        fun setSongs(tracks: List<Pair<String, Song>>?) {
            songs_.value = tracks
        }

        fun setAlbums(albums: List<Pair<String, Album>>?) {
            albums_.value = albums
        }
    }

    class AlbumScreen(val id: String, val albumTitle: String, val art: String?, val onPlayAlbumNext: () -> Unit, val onAddAlbumToQueue: () -> Unit, val onPlayNext: (Int) -> Unit, val onAddToQueue: (Int) -> Unit, val onGoToArtist: (Int) -> Unit) : Screen {
        private val tracks_: MutableStateFlow<List<Pair<String, Track>>?> = MutableStateFlow(null)
        val tracks: StateFlow<List<Pair<String, Track>>?> = tracks_

        fun setTracks(tracks: List<Pair<String, Track>>?) {
            tracks_.value = tracks
        }
    }
}

class PlayerState {
    private val state_: MutableStateFlow<Int> = MutableStateFlow(SessionPlayer.PLAYER_STATE_IDLE)
    val state: StateFlow<Int> = state_

    private val playlist_: MutableStateFlow<List<Pair<String, Song>>> = MutableStateFlow(listOf())
    val playlist: StateFlow<List<Pair<String, Song>>> = playlist_

    private val current_ = MutableStateFlow(-1)
    val current: StateFlow<Int> = current_

    private val progress_ = MutableStateFlow(0L)
    val progress: StateFlow<Long> = progress_

    private val buffered_ = MutableStateFlow(0L)
    val buffered: StateFlow<Long> = buffered_

    fun setState(state: Int) {
        state_.value = state
    }

    fun setPlaylist(playlist: List<Pair<String, Song>>) {
        playlist_.value = playlist
    }

    fun setCurrentIndex(index: Int) {
        current_.value = index
    }

    fun setProgress(progress: Long) {
        progress_.value = progress
    }

    fun setBufferedProgress(progress: Long) {
        buffered_.value = progress
    }
}