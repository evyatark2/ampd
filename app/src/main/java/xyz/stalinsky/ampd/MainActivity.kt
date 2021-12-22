package xyz.stalinsky.ampd

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media2.common.MediaItem
import androidx.media2.common.MediaMetadata
import androidx.media2.common.SessionPlayer
import androidx.media2.session.*
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.pagerTabIndicatorOffset
import com.google.accompanist.pager.rememberPagerState
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.ui.PlayerSheet
import xyz.stalinsky.ampd.ui.theme.AMPDTheme
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    //private val executor = { it: Runnable -> queue.put(Optional.of(it)) }

    private val playingState: MutableLiveData<Int> =
        MutableLiveData(SessionPlayer.PLAYER_STATE_IDLE)
    @ExperimentalMaterialApi
    private val playlistState: MutableLiveData<List<Pair<String, Song>>> = MutableLiveData(listOf())
    private val currentItemState: MutableLiveData<Pair<Int, Bitmap?>> =
        MutableLiveData(Pair(-1, null))

    // This is a map that represents the main view window
    // The key is the mediaId of a root mediaItem e.g. "/artists"
    // The value is a pair where first is the name of the category e.g. "Artists"
    // and second is a list of composeables used to render the list e.g.
    // listOf(ArtistView(), ArtistView())
    private var categories: MutableLiveData<Map<String, Pair<String, MutableLiveData<List<@Composable () -> Unit>?>>>?> =
        MutableLiveData(null)

    private lateinit var controller: MediaBrowser

    private val connectionState = MutableLiveData(ConnectionState.CONNECTING)

    private val backstack = java.util.Stack<Screen>()
    private val screenState: MutableLiveData<Screen> =
        MutableLiveData(Screen.MainScreen(categories))

    private var mpdHost = ""
    private var mpdPort = -1
    private var mediaLibrary = ""

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            val onValueChanged = {
                Futures.addCallback(controller.sendCustomCommand(
                    MusicService.COMMAND_MPD_CONNECT, Bundle().apply {
                        putString(MusicService.COMMAND_ARG_MPD_HOST, mpdHost)
                        putInt(MusicService.COMMAND_ARG_MPD_PORT, mpdPort)
                    }), object : FutureCallback<SessionResult> {
                    override fun onSuccess(result: SessionResult?) {
                        if (result != null && result.resultCode == SessionResult.RESULT_SUCCESS) {
                            connectionState.postValue(ConnectionState.CONNECTED)
                            val params = MediaLibraryService.LibraryParams.Builder().build()
                            controller.subscribe(
                                controller.getLibraryRoot(params)
                                    .get().mediaItem?.metadata?.mediaId!!, params
                            )
                        } else {
                            connectionState.postValue(ConnectionState.ERROR)
                        }
                    }

                    override fun onFailure(t: Throwable) {
                        t.printStackTrace()
                    }
                }, executor
                )
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
                    controller.sendCustomCommand(
                        MusicService.COMMAND_SET_MEDIA_LIBRARY,
                        Bundle().apply {
                            putString(
                                MusicService.COMMAND_ARG_MEDIA_LIBRARY,
                                mediaLibrary
                            )
                        })
                }
            }
        }

    @OptIn(
        ExperimentalMaterialApi::class,
        ExperimentalPagerApi::class,
        ExperimentalAnimationApi::class
    )
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
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Main(
                        connectionState,
                        {
                            val screen = screenState.value
                            if (screen is Screen.ArtistScreen)
                                controller.unsubscribe(screen.id)

                            screenState.postValue(backstack.pop())
                        },
                        screenState,
                        PlayerState(playingState, playlistState, currentItemState),
                        { playlist, index ->
                            // Start playing the tapped item immediately and then add the rest of the playlist
                            controller.setPlaylist(playlist, null).addListener({
                                controller.skipToPlaylistItem(index).addListener({
                                    if (controller.playerState == SessionPlayer.PLAYER_STATE_IDLE)
                                        controller.prepare().addListener({
                                            controller.play()
                                        }, executor)
                                    else if (controller.playerState == SessionPlayer.PLAYER_STATE_PAUSED)
                                        controller.play()
                                }, executor)
                            }, executor)
                        },
                        {
                            if (playingState.value == SessionPlayer.PLAYER_STATE_PAUSED)
                                controller.play()
                            else if (playingState.value == SessionPlayer.PLAYER_STATE_PLAYING)
                                controller.pause()
                        },
                        { controller.skipToPreviousPlaylistItem() },
                        { controller.skipToNextPlaylistItem() }) {
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

        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(listener)
    }

    inner class Callback : MediaBrowser.BrowserCallback() {
        @OptIn(ExperimentalMaterialApi::class)
        override fun onConnected(
            controller: MediaController,
            allowedCommands: SessionCommandGroup
        ) {
            playingState.postValue(controller.playerState)
            playlistState.postValue(controller.playlist?.map {
                Pair(
                    it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
                    )
                )
            })

            currentItemState.postValue(
                Pair(
                    controller.currentMediaItemIndex,
                    null
                )
            ) // TODO: Use Glide to load the current item's art

            controller.sendCustomCommand(
                MusicService.COMMAND_SET_MEDIA_LIBRARY,
                Bundle().apply { putString(MusicService.COMMAND_ARG_MEDIA_LIBRARY, mediaLibrary) })
                .addListener({

                    Futures.addCallback(controller.sendCustomCommand(
                        MusicService.COMMAND_MPD_CONNECT, Bundle().apply {
                            putString(MusicService.COMMAND_ARG_MPD_HOST, mpdHost)
                            putInt(MusicService.COMMAND_ARG_MPD_PORT, mpdPort)
                        }), object : FutureCallback<SessionResult> {
                        override fun onSuccess(result: SessionResult?) {
                            if (result != null && result.resultCode == SessionResult.RESULT_SUCCESS) {
                                connectionState.postValue(ConnectionState.CONNECTED)
                                val params = MediaLibraryService.LibraryParams.Builder().build()
                                (controller as MediaBrowser).subscribe(
                                    controller.getLibraryRoot(params)
                                        .get().mediaItem?.metadata?.mediaId!!, params
                                )
                            } else {
                                connectionState.postValue(ConnectionState.ERROR)
                            }
                        }

                        override fun onFailure(t: Throwable) {
                            t.printStackTrace()
                        }
                    }, executor
                    )
                }, executor)
        }

        override fun onDisconnected(controller: MediaController) {
            Log.i("MainActivity.Callback", "Disconnected")
        }

        override fun onCurrentMediaItemChanged(controller: MediaController, item: MediaItem?) {
            currentItemState.postValue(Pair(controller.currentMediaItemIndex, null))

            val uri = item?.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)

            Glide.with(this@MainActivity).asBitmap().load(if (uri != null) Uri.parse(uri) else null)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: Transition<in Bitmap>?
                    ) {
                        currentItemState.postValue(Pair(currentItemState.value!!.first, resource))
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        TODO("Not yet implemented")
                    }
                })
        }

        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle?
        ): SessionResult {
            controller.close()
            Log.i("MainActivity.Callback", "closed")
            return SessionResult(SessionResult.RESULT_SUCCESS, null)
        }

        @OptIn(ExperimentalMaterialApi::class)
        override fun onPlaylistChanged(
            controller: MediaController,
            list: MutableList<MediaItem>?,
            metadata: MediaMetadata?
        ) {
            currentItemState.postValue(Pair(controller.currentMediaItemIndex, null))

            playlistState.postValue(list?.map {
                Pair(
                    it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                    Song(
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                        it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                        it.metadata?.getString((MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
                    )
                )
            })
        }

        override fun onPlayerStateChanged(controller: MediaController, state: Int) {
            playingState.postValue(state)
        }

        override fun onChildrenChanged(
            browser: MediaBrowser,
            parentId: String,
            itemCount: Int,
            params: MediaLibraryService.LibraryParams?
        ) {
            when {
                parentId == "/" -> {
                    val children = browser.getChildren("/", 0, 3, params).get().mediaItems
                    categories.postValue(children?.associateBy({
                        it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                    }, {
                        Pair(
                            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                            MutableLiveData(null)
                        )
                    }))
                    for (child in children!!) {
                        val id = child.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!
                        browser.subscribe(id, params)
                    }
                }

                parentId == "/artists" -> {
                    val children = browser.getChildren("/artists", 0, Int.MAX_VALUE, params)
                        .get().mediaItems?.map {
                            // Couldn't find how to return a @Composable expression directly
                            val composable: @Composable () -> Unit = {
                                ArtistView(
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                                ) {
                                    backstack.push(screenState.value)
                                    screenState.postValue(
                                        Screen.ArtistScreen(
                                            it.metadata?.getString(
                                                MediaMetadata.METADATA_KEY_MEDIA_ID
                                            )!!,
                                            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                            MutableLiveData(null)
                                        )
                                    )
                                    browser.subscribe(
                                        it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                                        params
                                    )
                                }
                            }

                            composable
                        }

                    categories.value?.get("/artists")?.second?.postValue(children)
                }

                parentId == "/albums" -> {
                    val children = browser.getChildren("/albums", 0, Int.MAX_VALUE, params)
                        .get().mediaItems?.map {
                            // Couldn't find how to return a @Composable expression directly
                            val composable: @Composable () -> Unit = {
                                AlbumView(
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                                ) {
                                    Log.i("MainActivity", "Album clicked")
                                    //backstack.push(screenState.value)
                                    //screenState.postValue(Screen.ArtistScreen(it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!, MutableLiveData(null)))
                                    //browser.subscribe(it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!, params)
                                }
                            }

                            composable
                        }

                    categories.value?.get("/albums")?.second?.postValue(children)
                }

                parentId.startsWith("/artists") -> {
                    val children = browser.getChildren(parentId, 0, Int.MAX_VALUE, params)
                        .get().mediaItems?.map {
                            Pair(
                                it.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)!!,
                                Song(
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)!!,
                                    it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ARTIST_ID)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)!!,
                                    it.metadata?.extras?.getString(MusicService.METADATA_EXTRA_ALBUM_ID)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)!!,
                                    it.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                                )
                            )
                        }

                    val screen = screenState.value
                    if (screen is Screen.ArtistScreen)
                        (screen.songs as MutableLiveData).postValue(children)
                }
            }
        }
    }
}

@ExperimentalAnimationApi
@ExperimentalMaterialApi
@ExperimentalPagerApi
@Composable
fun Main(
    connectionState: LiveData<ConnectionState>,
    onBackPressed: () -> Unit,
    screen: LiveData<Screen>,
    playerState: PlayerState,
    setPlaylist: (List<String>, Int) -> Unit,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(title = {
            Text("MMPD")

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
        val connectionState by connectionState.observeAsState()
        if (connectionState == ConnectionState.ERROR) {
            Text("MPD CONNECTION FAILED")
        } else {
            val playingState by playerState.state.observeAsState()
            val playerVisible =
                playingState == SessionPlayer.PLAYER_STATE_PAUSED || playingState == SessionPlayer.PLAYER_STATE_PLAYING

            val scope = rememberCoroutineScope()
            val pagerState = rememberPagerState()
            val swipeState = rememberSwipeableState(false)

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val screen by screen.observeAsState()
                if (screen !is Screen.MainScreen)
                    BackHandler(true, onBackPressed)

                val mainHeight = with(LocalDensity.current) { (maxHeight - 72.dp).toPx() }
                val transition = updateTransition(playerVisible, label = "")
                val mainViewHeight = transition.animateDp({ tween() }, label = "") {
                    if (it)
                        maxHeight - 72.dp
                    else
                        maxHeight
                }

                val swipeOffsetDp = with(LocalDensity.current) { swipeState.offset.value.toDp() }

                BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(mainViewHeight.value + swipeOffsetDp)
                ) {
                    when (screen) {
                        is Screen.MainScreen -> Column {
                            val categories by (screen as Screen.MainScreen).categories.observeAsState()
                            if (categories != null) {
                                TabRow(selectedTabIndex = pagerState.currentPage, indicator = {
                                    TabRowDefaults.Indicator(
                                        Modifier.pagerTabIndicatorOffset(
                                            pagerState,
                                            it
                                        )
                                    )
                                }) {
                                    categories!!.toList().forEachIndexed { i, pair ->
                                        Tab(selected = i == pagerState.currentPage, onClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(i)
                                            }
                                        }, text = { Text(pair.second.first) })
                                    }
                                }

                                HorizontalPager(
                                    count = categories!!.size,
                                    state = pagerState
                                ) { page ->
                                    val list by categories!!.toList()[page].second.second.observeAsState()
                                    if (list != null) {
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items((list as List<*>).size) {
                                                (list as List<@Composable () -> Unit>)[it]()
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

                        is Screen.ArtistScreen -> {
                            val songs by (screen as Screen.ArtistScreen).songs.observeAsState()
                            if (songs != null) {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(songs!!.size) {
                                        ConstraintLayout(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .clickable {
                                                    setPlaylist(songs!!.map { it.first }, it)
                                                }) {
                                            val (titleConstraint) = createRefs()

                                            Text(
                                                songs!![it].second.title,
                                                Modifier.constrainAs(titleConstraint) {
                                                    top.linkTo(parent.top)
                                                    bottom.linkTo(parent.bottom)
                                                    start.linkTo(parent.start, 16.dp)
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

                val justOffScreen = with(LocalDensity.current) { 72.dp.toPx() }.roundToInt()

                transition.AnimatedVisibility(
                    { it },
                    enter = slideInVertically(tween()) { justOffScreen },
                    exit = slideOutVertically(tween()) { justOffScreen },
                    modifier = Modifier.matchParentSize()
                ) {
                    val playlist by playerState.playlist.observeAsState()
                    val current by playerState.current.observeAsState()
                    PlayerSheet(playerVisible,
                        playingState ?: SessionPlayer.PLAYER_STATE_ERROR,
                        playlist ?: listOf(),
                        current!!,
                        swipeState,
                        onPrev,
                        onPlayPause,
                        onNext,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, mainHeight.roundToInt()) })
                }
            }
        }
    }
}

@Composable
fun ArtistView(name: String, onClick: () -> Unit) {
    ConstraintLayout(
        Modifier
            .clickable(onClick = onClick)
            .height(48.dp)
            .fillMaxWidth()
    ) {
        val (constraint) = createRefs()

        Text(
            name, Modifier.constrainAs(constraint) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start, 16.dp)
            },
            style = MaterialTheme.typography.subtitle1
        )
    }
}

@Composable
fun AlbumView(title: String, artist: String, art: String?, onClick: () -> Unit) {
    ConstraintLayout(
        Modifier
            .clickable(onClick = onClick)
            .height(64.dp)
            .fillMaxWidth()
    ) {
        val (artConstraint, titleConstraint, artistConstraint) = createRefs()

        GlideImage(art, Modifier.constrainAs(artConstraint) {
            top.linkTo(parent.top, 16.dp)
            bottom.linkTo(parent.bottom, 16.dp)
            start.linkTo(parent.start, 16.dp)

            width = Dimension.value(40.dp)
            height = Dimension.value(40.dp)
        }, requestOptions = {
            RequestOptions().diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        }, contentScale = ContentScale.Crop)

        Text(
            title,
            Modifier
                .paddingFromBaseline(28.dp)
                .constrainAs(titleConstraint) {
                    top.linkTo(parent.top)
                    start.linkTo(artConstraint.end, 16.dp)
                    end.linkTo(parent.end, 16.dp)

                    width = Dimension.fillToConstraints
                },
            style = MaterialTheme.typography.subtitle1
        )

        Text(
            artist,
            Modifier
                .paddingFromBaseline(48.dp)
                .constrainAs(artistConstraint) {
                    top.linkTo(parent.top)
                    start.linkTo(artConstraint.end, 16.dp)
                    end.linkTo(parent.end, 16.dp)

                    width = Dimension.fillToConstraints
                },
            style = MaterialTheme.typography.caption
        )
    }
}

sealed interface Screen {
    class MainScreen(val categories: LiveData<Map<String, Pair<String, MutableLiveData<List<@Composable () -> Unit>?>>>?>) :
        Screen

    class ArtistScreen(
        val id: String,
        val artistName: String,
        val songs: LiveData<List<Pair<String, Song>>?>
    ) :
        Screen
}

data class PlayerState(
    val state: LiveData<Int>,
    val playlist: LiveData<List<Pair<String, Song>>>,
    val current: LiveData<Pair<Int, Bitmap?>>
)

enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    ERROR
}

