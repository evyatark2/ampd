package xyz.stalinsky.ampd

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

    private val playingState = MutableStateFlow(SessionPlayer.PLAYER_STATE_IDLE)

    private var updateSongProgressJob: Job? = null
    private val progressState = MutableStateFlow(0L)

    private var updateBufferedProgressJob: Job? = null
    private val bufferedState = MutableStateFlow(0L)

    private val playlistState: MutableStateFlow<List<Pair<String, Song>>> = MutableStateFlow(listOf())
    private val currentItemState: MutableStateFlow<Int> = MutableStateFlow(-1)

    // This is a map that represents the main view window
    // The key is the mediaId of a root mediaItem e.g. "/artists"
    // The value is a pair where first is the name of the category e.g. "Artists"
    // and second is a list of composeables used to render the list e.g.
    // listOf(ArtistView("Beethoven"), ArtistView("The Beatles"))
    private var categories: MutableStateFlow<Map<String, Pair<String, MutableStateFlow<List<@Composable () -> Unit>?>>>?> = MutableStateFlow(null)

    private lateinit var controller: MediaBrowser

    private val connectionState = MutableStateFlow(MusicService.ConnectionState.DISCONNECTED)

    private val backstack = Stack<Screen>()
    private val screenState: MutableStateFlow<Screen> = MutableStateFlow(Screen.MainScreen(categories))

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
                        if (screen is Screen.ArtistScreen) controller.unsubscribe(screen.id)
                        if (screen is Screen.AlbumScreen) controller.unsubscribe(screen.id)

                        screenState.value = backstack.pop()
                    }, screenState, PlayerState(playingState, playlistState, currentItemState, progressState, bufferedState), { playlist, index ->
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
                        if (playingState.value == SessionPlayer.PLAYER_STATE_PAUSED)
                            controller.play()
                        else if (playingState.value == SessionPlayer.PLAYER_STATE_PLAYING)
                            controller.pause()
                    }, { controller.skipToPreviousPlaylistItem() }, { controller.skipToNextPlaylistItem() }, {
                        updateSongProgressJob?.cancel()
                        updateSongProgressJob = null
                        progressState.value = it
                        controller.seekTo(it).addListener({
                            updateSongProgressJob = lifecycle.coroutineScope.launch {
                                while (true) {
                                    // 24 fps
                                    delay(42)
                                    progressState.value = controller.currentPosition
                                }
                            }
                        }, executor)
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
            playingState.value = controller.playerState
            playlistState.value = controller.playlist?.map {
                Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))))
            } ?: listOf()

            currentItemState.value = controller.currentMediaItemIndex

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
            currentItemState.value = controller.currentMediaItemIndex
        }

        override fun onCustomCommand(controller: MediaController, command: SessionCommand, args: Bundle?): SessionResult {
            when (command) {
                MusicService.COMMAND_MPD_CONNECTION_STATUS_CHANGED -> {
                    when (args!!.getParcelable<MusicService.ConnectionState>(MusicService.COMMAND_ARG_MPD_CONNECTION_STATUS)) {
                        MusicService.ConnectionState.CONNECTED -> {
                            connectionState.value = MusicService.ConnectionState.CONNECTED
                            val params = MediaLibraryService.LibraryParams.Builder().build()
                            (controller as MediaBrowser).subscribe(controller.getLibraryRoot(params).get().mediaItem?.metadata?.mediaId!!, params)
                        }

                        MusicService.ConnectionState.CONNECTING -> {
                            connectionState.value = MusicService.ConnectionState.CONNECTING
                        }

                        MusicService.ConnectionState.DISCONNECTED -> {
                            connectionState.value = MusicService.ConnectionState.DISCONNECTED
                        }
                    }
                }
            }

            return SessionResult(SessionResult.RESULT_SUCCESS, null)
        }

        override fun onPlaylistChanged(controller: MediaController, list: MutableList<MediaItem>?, metadata: MediaMetadata?) {
            playlistState.value = list?.map {
                Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))))
            } ?: listOf()
        }

        override fun onPlayerStateChanged(controller: MediaController, state: Int) {
            when (state) {
                SessionPlayer.PLAYER_STATE_PAUSED -> {
                    if (playingState.value == SessionPlayer.PLAYER_STATE_PLAYING) {
                        updateSongProgressJob?.cancel()
                        updateSongProgressJob = null
                    }
                }

                SessionPlayer.PLAYER_STATE_PLAYING -> {
                    updateSongProgressJob = lifecycle.coroutineScope.launch {
                        while (true) {
                            // 24 fps
                            delay(42)
                            progressState.value = controller.currentPosition
                        }
                    }
                }
            }

            playingState.value = state
        }

        override fun onBufferingStateChanged(controller: MediaController, item: MediaItem, state: Int) {
            if ((state == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE || state == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_STARVED)) {
                updateBufferedProgressJob = lifecycle.coroutineScope.launch {
                    while (true) {
                        // 24 fps
                        delay(42)
                        bufferedState.value = controller.bufferedPosition
                    }
                }
            } else if (state == SessionPlayer.BUFFERING_STATE_COMPLETE) {
                updateBufferedProgressJob?.cancel()
                bufferedState.value = controller.bufferedPosition
                updateBufferedProgressJob = null
            }
        }

        override fun onChildrenChanged(browser: MediaBrowser, parentId: String, itemCount: Int, params: MediaLibraryService.LibraryParams?) {
            when {
                parentId == "/" -> {
                    val children = browser.getChildren("/", 0, 3, params).get().mediaItems
                    categories.value = children?.associateBy({
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                    }, {
                        Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!, MutableStateFlow(null))
                    })
                    for (child in children!!) {
                        val id = child.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                        browser.subscribe(id, params)
                    }
                }

                parentId == "/artists" -> {
                    val children = browser.getChildren("/artists", 0, Int.MAX_VALUE, params).get().mediaItems?.map {
                        // Couldn't find how to return a @Composable expression directly
                        val composable: @Composable () -> Unit = {
                            ArtistView(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "") {
                                backstack.push(screenState.value)
                                screenState.value = Screen.ArtistScreen(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                    MutableStateFlow(null),
                                    MutableStateFlow(null))
                                browser.subscribe(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!, params)
                            }
                        }

                        composable
                    }

                    categories.value?.get("/artists")?.second?.value = children
                }

                parentId == "/albums" -> {
                    val children = browser.getChildren("/albums", 0, Int.MAX_VALUE, params).get().mediaItems?.map {
                        // Couldn't find how to return a @Composable expression directly
                        val composable: @Composable () -> Unit = {
                            AlbumView(Album(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))) {
                                backstack.push(screenState.value)
                                screenState.value = Screen.AlbumScreen(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                                    MutableStateFlow(null), {
                                        val current = browser.currentMediaItemIndex
                                        browser.addPlaylistItem(current + 1, (screenState.value as Screen.AlbumScreen).tracks.value!![it].first).addListener({
                                            if (current == -1)
                                                browser.play().get()
                                        }, executor)
                                    }, {
                                        browser.addPlaylistItem(browser.playlist?.size ?: 0, (screenState.value as Screen.AlbumScreen).tracks.value!![it].first).get()
                                    }, {
                                    })
                                browser.subscribe(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!, params)
                            }
                        }

                        composable
                    }

                    categories.value?.get("/albums")?.second?.value = children
                }

                parentId.startsWith("/artists") -> {
                    val children = browser.getChildren(parentId, 0, Int.MAX_VALUE, params).get().mediaItems?.map {
                        Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                            Song(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)))
                    }

                    val screen = screenState.value
                    if (screen is Screen.ArtistScreen) (screen.songs as MutableStateFlow).value = children
                }

                parentId.startsWith("/albums") -> {
                    val children = browser.getChildren(parentId, 0, Int.MAX_VALUE, params).get().mediaItems?.map {
                        Pair(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                            Track(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_DISC_NUMBER)!!.toInt(),
                                it.metadata?.getLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER)!!.toInt()))
                    }

                    val screen = screenState.value
                    if (screen is Screen.AlbumScreen)
                            (screen.tracks as MutableStateFlow).value = children
                }
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class, ExperimentalMaterialApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class)
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
         onSeek: (Long) -> Unit,
         onSettings: () -> Unit) {
    val screen = screenFlow.collectAsState().value
    if (screen !is Screen.MainScreen) BackHandler(true, onBackPressed)

    val connectionState by connectionFlow.collectAsState()
    if (connectionState == MusicService.ConnectionState.DISCONNECTED) {
        Text("MPD CONNECTION FAILED")
    } else {
        val playingState by playerState.state.collectAsState()
        val playerVisible = playingState == SessionPlayer.PLAYER_STATE_PAUSED || playingState == SessionPlayer.PLAYER_STATE_PLAYING

        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState()
        val swipeState = rememberSwipeableState(false)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val transition = updateTransition(playerVisible, label = "")
            val playerHeight = transition.animateDp({ tween() }, label = "") {
                if (it) 72.dp
                else 0.dp
            }

            val swipeOffsetDp = with(LocalDensity.current) { swipeState.offset.value.toDp() }

            Box(Modifier.fillMaxSize().padding(bottom = playerHeight.value)) {
                when (screen) {
                    is Screen.MainScreen -> Column {
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

                        val categories = screen.categories.collectAsState().value
                        if (categories != null) {
                            TabRow(selectedTabIndex = pagerState.currentPage, indicator = {
                                TabRowDefaults.Indicator(Modifier.pagerTabIndicatorOffset(pagerState, it))
                            }) {
                                categories.toList().forEachIndexed { i, pair ->
                                    Tab(selected = i == pagerState.currentPage, onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(i)
                                        }
                                    }, text = { Text(pair.second.first) })
                                }
                            }

                            HorizontalPager(count = categories.size, state = pagerState) { page ->
                                val list = categories.toList()[page].second.second.collectAsState().value
                                if (list != null) {
                                    LazyColumn(Modifier.fillMaxSize()) {
                                        items(list.size) {
                                            list[it]()
                                        }
                                    }
                                } else {
                                    Text("null")
                                }
                            }
                        } else {
                            Text("Loading")
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
                            Text("null")
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

                            IconButton(onBackPressed, Modifier.padding(16.dp).size(24.dp)) {
                                Icon(Icons.Default.ArrowBack, "")
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
                                Text("null")
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
                    onSeek,
                    extendTransition, {
                        extended = it
                    }, Modifier.height(height.value))
            }
        }
    }
}

@Composable
fun ArtistView(name: String, onClick: () -> Unit) {
    ConstraintLayout(Modifier.clickable(onClick = onClick).height(48.dp).fillMaxWidth()) {
        val (constraint) = createRefs()

        SingleLineText(name, Modifier.constrainAs(constraint) {
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
                        Text("Go to album")
                    }
                }
            }
        }
    }
}

sealed interface Screen {
    class MainScreen(val categories: StateFlow<Map<String, Pair<String, StateFlow<List<@Composable () -> Unit>?>>>?>) : Screen

    class ArtistScreen(val id: String,
                       val artistName: String,
                       val albums: StateFlow<List<Pair<String, Album>>?>,
                       val songs: StateFlow<List<Pair<String, Song>>?>) : Screen

    class AlbumScreen(val id: String, val albumTitle: String, val art: String?, val tracks: StateFlow<List<Pair<String, Track>>?>, val onPlayNext: (Int) -> Unit, val onAddToQueue: (Int) -> Unit, val onGoToArtist: (Int) -> Unit) : Screen
}

data class PlayerState(val state: StateFlow<Int>,
                       val playlist: StateFlow<List<Pair<String, Song>>>,
                       val current: StateFlow<Int>,
                       val progress: StateFlow<Long>,
                       val buffered: StateFlow<Long>)