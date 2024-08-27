package xyz.stalinsky.ampd.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.EventListener
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.Options
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import xyz.stalinsky.ampd.R
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.model.Track
import xyz.stalinsky.ampd.ui.utils.SingleLineText
import xyz.stalinsky.ampd.ui.viewmodel.AlbumViewModel
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(viewModel: MainViewModel = hiltViewModel()) {
    val state = rememberPlayerSheetScaffoldState()
    val navController = rememberNavController()

    val loading by viewModel.loading.collectAsState()
    val playing by viewModel.playing.collectAsState()
    val currentItem by viewModel.currentItem.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()
    val tls by viewModel.mpdTls.collectAsState()
    var force by remember { mutableStateOf(false) }

    var connectionState: MpdConnectionState<Unit> by remember { mutableStateOf(MpdConnectionState.Loading()) }

    if (!force) {
        LaunchedEffect(host, port, tls) {
            try {
                val addr = withContext(Dispatchers.IO) {
                    InetSocketAddress(host, port)
                }
                viewModel.connect(addr, tls)
                connectionState = MpdConnectionState.Ok(Unit)
            } catch (e: Throwable) {
                connectionState = MpdConnectionState.Error(e)
            }
        }
    } else {
        connectionState = MpdConnectionState.Loading()
        force = false
    }

    var innerTitle by remember { mutableStateOf("") }
    val route by navController.currentBackStackEntryAsState()
    PlayerSheetScaffold(state,
            Modifier,
            (route?.destination?.route == "main" || route?.destination?.route == "artist/{id}" || route?.destination?.route == "album/{id}") && queue != null,
            {
                Player(state.playerState, loading, playing, if (currentItem != -1) {
                    queue?.run { Pair(this, currentItem) }
                } else {
                    null
                }, {
                    viewModel.progress()
                }, duration, {
                    viewModel.play()
                }, {
                    viewModel.pause()
                }, {
                    viewModel.seek(it)
                }, {
                    viewModel.next()
                }, {
                    viewModel.prev()
                }, {
                    viewModel.skipTo(it)
                })
            },
            {
                val title = when (route?.destination?.route) {
                    "main"     -> stringResource(R.string.app_name)
                    "settings" -> stringResource(R.string.settings)
                    "tabs"     -> stringResource(R.string.tabs)
                    else       -> innerTitle
                }

                TopAppBar({ Text(title) }, actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, "")
                    }
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "")
                    }
                })
            }) {
        NavHost(navController, "main", Modifier.fillMaxSize()) {
            composable("main") {
                innerTitle = ""
                MainScreen(connectionState, { force = true }, navController)
            }

            composable("settings") {
                innerTitle = ""
                SettingsScreen(navController)
            }

            composable("tabs") {
                innerTitle = ""
                TabsSettingScreen()
            }

            composable("artist/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                ArtistScreen(connectionState, { force = true }, { innerTitle = it })
            }

            composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                AlbumScreen(connectionState, { force = true }, { innerTitle = it })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
        connectionState: MpdConnectionState<Unit>,
        onRetry: () -> Unit,
        nav: NavController,
        viewModel: MainViewModel = hiltViewModel()) {
    val tabs by viewModel.tabs.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

    Column(Modifier) {
        val pagerState = key(defaultTab) {
            rememberPagerState(initialPage = defaultTab) {
                tabs.size
            }
        }

        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { i, tab ->
                if (tab.enabled) {
                    when (tab.type) {
                        Settings.TabType.TAB_TYPE_ARTISTS -> Tab(selected = i == pagerState.currentPage, onClick = {
                            viewModel.viewModelScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        }, text = { Text(stringResource(R.string.artists)) })

                        Settings.TabType.TAB_TYPE_ALBUMS  -> Tab(selected = i == pagerState.currentPage, onClick = {
                            viewModel.viewModelScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        }, text = { Text(stringResource(R.string.albums)) })

                        Settings.TabType.TAB_TYPE_GENRES  -> Tab(selected = i == pagerState.currentPage, onClick = {
                            viewModel.viewModelScope.launch {
                                pagerState.animateScrollToPage(i)
                            }
                        }, text = { Text(stringResource(R.string.genres)) })

                        Settings.TabType.UNRECOGNIZED     -> TODO()
                        null                              -> TODO()
                    }
                }
            }
        }

        HorizontalPager(pagerState, Modifier.fillMaxSize()) {
            val tab = tabs[it]
            if (tab.enabled) {
                when (tab.type) {
                    Settings.TabType.TAB_TYPE_ARTISTS -> {
                        ArtistsScreen(connectionState, onRetry, {
                            nav.navigate("artist/${it}")
                        })
                    }

                    Settings.TabType.TAB_TYPE_ALBUMS  -> {
                        AlbumsScreen(connectionState, onRetry, {
                            nav.navigate("album/${it}")
                        }, {
                            nav.navigate("artist/${it}")
                        })
                    }

                    Settings.TabType.TAB_TYPE_GENRES  -> TODO()
                    Settings.TabType.UNRECOGNIZED     -> TODO()
                    null                              -> TODO()
                }
            }
        }
    }
}

enum class Dialog {
    LIBRARY_HOST_DIALOG, LIBRARY_PORT_DIALOG, MPD_HOST_DIALOG, MPD_PORT_DIALOG
}

@Composable
fun SettingsScreen(nav: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    Column {
        var showDialog by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(Dialog.LIBRARY_HOST_DIALOG) }

        val libraryHost by viewModel.libraryHost.collectAsState()
        val libraryPort by viewModel.libraryPort.collectAsState()
        val mpdTls by viewModel.mpdTls.collectAsState()
        val mpdHost by viewModel.mpdHost.collectAsState()
        val mpdPort by viewModel.mpdPort.collectAsState()

        if (showDialog) {
            var value by remember {
                mutableStateOf(when (dialog) {
                    Dialog.LIBRARY_HOST_DIALOG -> libraryHost
                    Dialog.LIBRARY_PORT_DIALOG -> libraryPort.toString()
                    Dialog.MPD_HOST_DIALOG     -> mpdHost
                    Dialog.MPD_PORT_DIALOG     -> mpdPort.toString()
                })
            }

            Dialog(onDismissRequest = {
                viewModel.viewModelScope.launch {
                    when (dialog) {
                        Dialog.LIBRARY_HOST_DIALOG -> viewModel.setLibraryHost(value)
                        Dialog.LIBRARY_PORT_DIALOG -> viewModel.setLibraryPort(value.toInt())
                        Dialog.MPD_HOST_DIALOG     -> viewModel.setMpdHost(value)
                        Dialog.MPD_PORT_DIALOG     -> viewModel.setMpdPort(value.toInt())
                    }
                    showDialog = false
                }
            }) {
                val options = KeyboardOptions(autoCorrect = false, keyboardType = when (dialog) {
                    Dialog.LIBRARY_HOST_DIALOG, Dialog.MPD_HOST_DIALOG -> KeyboardType.Uri
                    Dialog.LIBRARY_PORT_DIALOG, Dialog.MPD_PORT_DIALOG -> KeyboardType.Number
                })

                TextField(value, { value = it }, keyboardOptions = options)
            }
        }

        ListItem(headlineContent = { Text(stringResource(R.string.tabs)) }, modifier = Modifier.clickable {
            nav.navigate("tabs")
        })
        ListItem({ Text(stringResource(R.string.music_library_host)) }, modifier = Modifier.clickable {
            dialog = Dialog.LIBRARY_HOST_DIALOG
            showDialog = true
        })
        ListItem({ Text(stringResource(R.string.music_library_port)) }, modifier = Modifier.clickable {
            dialog = Dialog.LIBRARY_PORT_DIALOG
            showDialog = true
        })
        ListItem({
            Text(stringResource(R.string.use_tls_with_mpd))
        }, trailingContent = {
            Checkbox(checked = mpdTls, onCheckedChange = {
                viewModel.viewModelScope.launch {
                    viewModel.toggleMpdTls()
                }
            })
        })
        ListItem({ Text(stringResource(R.string.mpd_host)) }, modifier = Modifier.clickable {
            dialog = Dialog.MPD_HOST_DIALOG
            showDialog = true
        })
        ListItem({ Text(stringResource(R.string.mpd_port)) }, modifier = Modifier.clickable {
            dialog = Dialog.MPD_PORT_DIALOG
            showDialog = true
        })
    }
}

@Composable
fun TabsSettingScreen(viewModel: TabsSettingViewModel = hiltViewModel()) {
    Column {
        val tabs by viewModel.tabs.collectAsState()
        val defaultTab by viewModel.defaultTab.collectAsState()
        tabs.forEachIndexed { i, tab ->
            ListItem({ Text(tab.type.name) }, leadingContent = {
                Checkbox(checked = tab.enabled, onCheckedChange = {
                    viewModel.viewModelScope.launch {
                        viewModel.toggleTab(i)
                    }
                })
            }, trailingContent = {
                RadioButton(selected = i == defaultTab, onClick = {
                    viewModel.viewModelScope.launch {
                        viewModel.setDefaultTab(i)
                    }
                })
            })
        }
    }
}

@Composable
fun ArtistsScreen(
        connectionState: MpdConnectionState<Unit>,
        onRetry: () -> Unit,
        onClick: (String) -> Unit,
        viewModel: ArtistsViewModel = hiltViewModel()) {
    var artistsState: Result<List<Artist>>? by remember { mutableStateOf(null) }

    var force by remember { mutableStateOf(false) }

    if (!force) {
        LaunchedEffect(connectionState) {
            artistsState = when (connectionState) {
                is MpdConnectionState.Ok      -> viewModel.getArtists()
                is MpdConnectionState.Error   -> Result.failure(connectionState.err)
                is MpdConnectionState.Loading -> null
            }
        }
    } else {
        force = false
    }

    ConnectionScreen(artistsState, onRetry) { artists ->
        Artists(artists, {
            onClick(artists[it].id)
        }, {
            viewModel.viewModelScope.launch {
                viewModel.addToQueue(artists[it].id)
            }
        }, {
            viewModel.viewModelScope.launch {
                viewModel.playNext(artists[it].id)
            }
        }) {
            force = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Artists(
        artists: List<Artist>,
        onClick: (Int) -> Unit,
        onAddToQueue: (Int) -> Unit,
        onPlayNext: (Int) -> Unit,
        onRefresh: () -> Unit) {
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        onRefresh()
        state.endRefresh()
    }
    Box(Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection)
            .clipToBounds()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
            if (!state.isRefreshing) {
                itemsIndexed(artists) { i, artist ->
                    Artist(artist.name, {
                        onClick(i)
                    }, {
                        onAddToQueue(i)
                    }) {
                        onPlayNext(i)
                    }
                }
            }
        }
        PullToRefreshContainer(state, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun Artist(name: String, onClick: () -> Unit, onAddToQueue: () -> Unit, onPlayNext: () -> Unit) {
    ListItem(headlineContent = {
        SingleLineText(name)
    }, Modifier.clickable {
        onClick()
    }, trailingContent = {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = {
            expanded = true
        }) {
            Icon(Icons.Default.MoreVert, "")
        }

        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.add_to_queue)) }, {
                expanded = false
                onAddToQueue()
            })
            DropdownMenuItem({ Text(stringResource(R.string.play_next)) }, {
                expanded = false
                onPlayNext()
            })
        }
    })
}

@Composable
fun AlbumsScreen(
        connectionState: MpdConnectionState<Unit>,
        onRetry: () -> Unit,
        onClick: (String) -> Unit,
        onGoToArtist: (String) -> Unit,
        viewModel: AlbumsViewModel = hiltViewModel()) {
    var albumsState: Result<List<Album>>? by remember { mutableStateOf(null) }

    var force by remember { mutableStateOf(false) }

    if (!force) {
        LaunchedEffect(connectionState) {
            albumsState = when (connectionState) {
                is MpdConnectionState.Ok      -> viewModel.getAlbums()
                is MpdConnectionState.Error   -> Result.failure(connectionState.err)
                is MpdConnectionState.Loading -> null
            }
        }
    } else {
        force = false
    }

    ConnectionScreen(albumsState, onRetry) { albums ->
        val context = LocalContext.current
        val loader = remember {
            ImageLoader.Builder(context).components {
                add(object : Fetcher.Factory<Uri> {
                    val partials = mutableMapOf<Uri, Buffer>()

                    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
                        val buf = partials.getOrElse(data) {
                            val buf = Buffer()
                            partials[data] = buf
                            buf
                        }
                        return Fetcher {
                            withContext(Dispatchers.Default) {
                                var size = viewModel.getAlbumArt(data.toString(), buf.size, buf).getOrThrow()
                                while (buf.size < size) {
                                    val newSize = viewModel.getAlbumArt(data.toString(), buf.size, buf).getOrThrow()
                                    if (newSize != size) {
                                        buf.clear()
                                        size = newSize
                                    }
                                }
                                partials.remove(data)
                                SourceResult(ImageSource(buf, options.context), null, DataSource.NETWORK)
                            }
                        }
                    }
                })
            }.eventListener(object : EventListener {
                override fun onError(request: ImageRequest, result: ErrorResult) {
                    result.throwable.printStackTrace()
                }
            }).build()
        }
        Albums(albums, loader, {
            onClick(albums[it].id)
        }, {
            viewModel.viewModelScope.launch {
                viewModel.addToQueue(albums[it].id)
            }
        }, {
            viewModel.viewModelScope.launch {
                viewModel.playNext(albums[it].id)
            }
        }, {
            onGoToArtist(albums[it].artistId)
        }) {
            force = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Albums(
        albums: List<Album>,
        loader: ImageLoader,
        onClick: (Int) -> Unit,
        onAddToQueue: (Int) -> Unit,
        onPlayNext: (Int) -> Unit,
        onGoToArtist: (Int) -> Unit,
        onRefresh: () -> Unit) {
    val state = rememberPullToRefreshState()
    if (state.isRefreshing) {
        onRefresh()
        state.endRefresh()
    }
    Box(Modifier
            .fillMaxSize()
            .nestedScroll(state.nestedScrollConnection)
            .clipToBounds()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
            itemsIndexed(albums) { i, album ->
                Album({
                    AsyncImage(album.art, "", loader, Modifier.size(56.dp))
                }, album.title, album.artist, {
                    onClick(i)
                }, {
                    onAddToQueue(i)
                }, {
                    onPlayNext(i)
                }) {
                    onGoToArtist(i)
                }
            }
        }
        PullToRefreshContainer(state, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
fun Album(
        art: @Composable () -> Unit,
        title: String,
        artist: String,
        onClick: () -> Unit,
        onAddToQueue: () -> Unit,
        onPlayNext: () -> Unit,
        onGoToArtist: () -> Unit) {
    ListItem({
        SingleLineText(title)
    }, Modifier.clickable {
        onClick()
    }, supportingContent = {
        SingleLineText(artist)
    }, leadingContent = art, trailingContent = {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = {
            expanded = true
        }) {
            Icon(Icons.Default.MoreVert, "")
        }

        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem({ Text(stringResource(R.string.add_to_queue)) }, {
                expanded = false
                onAddToQueue()
            })
            DropdownMenuItem({ Text(stringResource(R.string.play_next)) }, {
                expanded = false
                onPlayNext()
            })
            DropdownMenuItem({ Text(stringResource(R.string.go_to_artist)) }, {
                expanded = false
                onGoToArtist()
            })
        }
    })
}

@Composable
fun ArtistScreen(
        connectionState: MpdConnectionState<Unit>,
        onRetry: () -> Unit,
        setTitle: (String) -> Unit,
        viewModel: ArtistViewModel = hiltViewModel()) {
    var songs: Result<List<Song>>? by remember { mutableStateOf(null) }
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is MpdConnectionState.Ok      -> {
                songs = viewModel.getSongs()
                setTitle(viewModel.getName().getOrDefault(""))
            }

            is MpdConnectionState.Error   -> {
                songs = Result.failure(connectionState.err)
            }

            is MpdConnectionState.Loading -> {
                songs = null
            }
        }
    }

    ConnectionScreen(songs, onRetry) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
            itemsIndexed(it) { i, song ->
                ListItem({
                    SingleLineText(song.title)
                }, Modifier.clickable {
                    viewModel.viewModelScope.launch {
                        viewModel.setQueue(it.map {
                            val metadata = MediaMetadata.Builder()
                                    .setTitle(it.title)
                                    .setArtist(it.artist)
                                    .setAlbumTitle(it.album)
                                    .build()
                            Pair(it.file, MediaItem.Builder().setMediaId(it.id).setMediaMetadata(metadata))
                        }, i)
                    }
                }, supportingContent = {
                    SingleLineText(song.artist)
                })
            }
        }
    }
}

@Composable
fun AlbumScreen(
        connectionState: MpdConnectionState<Unit>,
        onRetry: () -> Unit,
        setTitle: (String) -> Unit,
        viewModel: AlbumViewModel = hiltViewModel()) {
    var tracks: Result<List<Track>>? by remember { mutableStateOf(null) }
    LaunchedEffect(connectionState) {
        when (connectionState) {
            is MpdConnectionState.Ok      -> {
                tracks = viewModel.getTracks()
                setTitle(viewModel.getTitle().getOrDefault(""))
            }

            is MpdConnectionState.Error   -> {
                tracks = Result.failure(connectionState.err)
            }

            is MpdConnectionState.Loading -> {
                tracks = null
            }
        }
    }

    ConnectionScreen(tracks, onRetry) {
        LazyColumn(Modifier.fillMaxSize(1f), contentPadding = PaddingValues(0.dp, 8.dp)) {
            var multiside = false
            for (track in it) {
                if (track.side != 1) {
                    multiside = true
                    break
                }
            }

            itemsIndexed(it) { i, track ->
                ListItem({
                    SingleLineText(track.title)
                }, Modifier.clickable {
                    viewModel.viewModelScope.launch {
                        viewModel.setQueue(it.map {
                            val metadata = MediaMetadata.Builder()
                                    .setTitle(it.title)
                                    .setArtist(it.artist)
                                    .setAlbumTitle(it.album)
                                    .build()
                            Pair(it.file, MediaItem.Builder().setMediaId(it.id).setMediaMetadata(metadata))
                        }, i)
                    }
                }, supportingContent = {
                    SingleLineText(track.artist)
                }, leadingContent = {
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        SingleLineText("${if (multiside) "${track.side}-" else ""}${track.track}")
                    }
                })
            }
        }
    }
}

@Composable
fun <T> ConnectionScreen(state: Result<T>?, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    if (state != null) {
        state.onSuccess {
            content(it)
        }.onFailure {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.Center)) {
                    Text(it.message ?: "", Modifier.align(Alignment.CenterHorizontally))
                    Button({
                        onRetry()
                    }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Retry")
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}