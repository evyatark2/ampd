package xyz.stalinsky.ampd.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel
import xyz.stalinsky.ampd.R
import xyz.stalinsky.ampd.ui.utils.SingleLineText
import xyz.stalinsky.ampd.ui.viewmodel.AlbumViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(main: MainViewModel = hiltViewModel()) {
    val state = rememberPlayerSheetScaffoldState()
    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val loading by main.loading.collectAsState()
    val playing by main.playing.collectAsState()
    val currentItem by main.currentItem.collectAsState()
    val queue by main.queue.collectAsState()
    val duration by main.duration.collectAsState()

    val graph = remember {
        navController.createGraph("main") {

        }
    }
    val route by navController.currentBackStackEntryAsState()
    PlayerSheetScaffold(state, Modifier, {
        Player(route?.destination?.route == "main" || route?.destination?.route == "artist/{id}" || route?.destination?.route == "album/{id}",
            state.playerState,
            loading,
            playing,
            if (currentItem != -1) { queue?.run { Pair(this, currentItem) } } else null,
            {
                main.progress()
            }, duration, {
                scope.launch {
                    main.play()
                }
            }, {
                scope.launch {
                    main.pause()
                }
            }, {
                scope.launch {
                    main.seek(it)
                }
            }, {
                scope.launch {
                    main.next()
                }
            }, {
                scope.launch {
                    main.prev()
                }
            }, {
                scope.launch {
                    main.skipTo(it)
                }
            })
    }, {
        val title = when (route?.destination?.route) {
            "main" -> stringResource(R.string.app_name)
            "settings" -> stringResource(R.string.settings)
            "tabs" -> stringResource(R.string.tabs)
            else -> ""
        }

        TopAppBar({ Text(title) }, actions = {
            IconButton(onClick = {  }) {
                Icon(Icons.Default.Search, "")
            }
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Default.Settings, "")
            }
        })
    }) {
        NavHost(navController, "main") {
            composable("main") {
                MainScreen(navController)
            }

            composable("settings") {
                SettingsScreen(navController)
            }

            composable("tabs") {
                TabsSettingScreen()
            }

            composable("artist/{id}", listOf(navArgument("id") {type = NavType.StringType })) {
                val id = it.arguments!!.getString("id")
                ArtistScreen(id!!, navController)
            }

            composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments!!.getString("id")
                AlbumScreen(id!!, navController)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    nav: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()
    val tls by viewModel.mpdTls.collectAsState()

    var force by remember { mutableStateOf(false) }

    if (!force) {
        LaunchedEffect(host, port, tls) {
            val addr = withContext(Dispatchers.IO) {
                try {
                    InetSocketAddress(host, port)
                } catch (e: Throwable) {
                    null
                }
            }
            viewModel.connect(addr, tls)
        }
    } else {
        force = false
    }

    Column(Modifier) {
        val scope = rememberCoroutineScope()

        val pagerState = key(defaultTab) {
            rememberPagerState(initialPage = defaultTab) {
                tabs.size
            }
        }

        TabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { i, tab ->
                if (tab.enabled) {
                    when (tab.type) {
                        Settings.TabType.TAB_TYPE_ARTISTS ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text(stringResource(R.string.artists)) })

                        Settings.TabType.TAB_TYPE_ALBUMS ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text(stringResource(R.string.albums)) })

                        Settings.TabType.TAB_TYPE_SONGS ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text(stringResource(R.string.songs)) })

                        Settings.TabType.TAB_TYPE_GENRES ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text(stringResource(R.string.genres)) })

                        Settings.TabType.UNRECOGNIZED -> TODO()
                        null -> TODO()
                    }
                }
            }
        }

        HorizontalPager(pagerState, Modifier.fillMaxSize()) {
            val tab = tabs[it]
            if (tab.enabled) {
                when (tab.type) {
                    Settings.TabType.TAB_TYPE_ARTISTS -> {
                        ArtistsScreen({
                            force = true
                        }, {
                            nav.navigate("artist/${it}")
                        })
                    }

                    Settings.TabType.TAB_TYPE_ALBUMS -> {
                        AlbumsScreen() {
                            nav.navigate("album/${it}")
                        }
                    }

                    Settings.TabType.TAB_TYPE_SONGS -> TODO()
                    Settings.TabType.TAB_TYPE_GENRES -> TODO()
                    Settings.TabType.UNRECOGNIZED -> TODO()
                    null -> TODO()
                }
            }
        }
    }
}

enum class Dialog {
    LIBRARY_HOST_DIALOG,
    LIBRARY_PORT_DIALOG,
    MPD_HOST_DIALOG,
    MPD_PORT_DIALOG
}

@Composable
fun SettingsScreen(nav: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    Column {
        var showDialog by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(Dialog.LIBRARY_HOST_DIALOG) }
        val scope = rememberCoroutineScope()

        val libraryHost by viewModel.libraryHost.collectAsState()
        val libraryPort by viewModel.libraryPort.collectAsState()
        val mpdTls by viewModel.mpdTls.collectAsState()
        val mpdHost by viewModel.mpdHost.collectAsState()
        val mpdPort by viewModel.mpdPort.collectAsState()

        if (showDialog) {
            var value by remember { mutableStateOf(when (dialog) {
                Dialog.LIBRARY_HOST_DIALOG -> libraryHost
                Dialog.LIBRARY_PORT_DIALOG -> libraryPort.toString()
                Dialog.MPD_HOST_DIALOG -> mpdHost
                Dialog.MPD_PORT_DIALOG -> mpdPort.toString()
            })
            }

            Dialog(onDismissRequest = {
                scope.launch {
                    when (dialog) {
                        Dialog.LIBRARY_HOST_DIALOG -> viewModel.setLibraryHost(value)
                        Dialog.LIBRARY_PORT_DIALOG -> viewModel.setLibraryPort(value.toInt())
                        Dialog.MPD_HOST_DIALOG -> viewModel.setMpdHost(value)
                        Dialog.MPD_PORT_DIALOG -> viewModel.setMpdPort(value.toInt())
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
                scope.launch {
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
        val scope = rememberCoroutineScope()
        val tabs by viewModel.tabs.collectAsState()
        val defaultTab by viewModel.defaultTab.collectAsState()
        tabs.forEachIndexed { i, tab ->
            ListItem({ Text(tab.type.name) }, leadingContent = { Checkbox(
                checked = tab.enabled,
                onCheckedChange = {
                    scope.launch {
                        viewModel.toggleTab(i)
                    }
                }
            ) }, trailingContent = {
                RadioButton(selected = i == defaultTab, onClick = {
                    scope.launch {
                        viewModel.setDefaultTab(i)
                    }
                })
            })
        }
    }
}

@Composable
fun ArtistsScreen(onRetry: () -> Unit, onClick: (String) -> Unit, viewModel: ArtistsViewModel = hiltViewModel()) {

    val artistsState = viewModel.artists.collectAsStateWithLifecycle()
    val artists = artistsState.value

    val scope = rememberCoroutineScope()

    when (artists) {
        is MpdConnectionState.Loading -> {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }

        is MpdConnectionState.Ok -> {
            Artists(artists.res, {
                onClick(artists.res[it].id)
            }, {
                scope.launch {
                    viewModel.addToQueue(artists.res[it].id)
                }
            }) {
                scope.launch {
                    viewModel.playNext(artists.res[it].id)
                }
            }
        }

        is MpdConnectionState.Error -> {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.Center)) {
                    Text(artists.err.message ?: "")
                    Button({
                        onRetry()
                    }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
fun Artists(artists: List<Artist>,
            onClick: (Int) -> Unit,
            onAddToQueue: (Int) -> Unit,
            onPlayNext: (Int) -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)) {
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
            DropdownMenuItem({ Text(stringResource(R.string.add_to_queue)) }, { onAddToQueue() })
            DropdownMenuItem({ Text(stringResource(R.string.play_next)) }, { onPlayNext() })
        }
    })
}

@Composable
fun AlbumsScreen(viewModel: AlbumsViewModel = hiltViewModel(), onClick: (String) -> Unit) {
    val albumsState = viewModel.albums.collectAsState()
    val albums = albumsState.value

    val scope = rememberCoroutineScope()

    if (albums != null) {
        Albums(albums, {
            onClick(albums[it].id)
        }, {
            scope.launch {
                viewModel.addToQueue(albums[it].id)
            }
        }) {
            scope.launch {
                viewModel.playNext(albums[it].id)
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun Albums(albums: List<Album>, onClick: (Int) -> Unit, onAddToQueue: (Int) -> Unit, onPlayNext: (Int) -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)) {
        itemsIndexed(albums) { i, album ->
            Album(album.title, album.artistId, {
                onClick(i)
            }, {
                onAddToQueue(i)
            }) {
                onPlayNext(i)
            }
        }
    }
}

@Composable
fun Album(title: String, artist: String, onClick: () -> Unit, onAddToQueue: () -> Unit, onPlayNext: () -> Unit) {
    ListItem(headlineContent = {
        SingleLineText(title)
    }, Modifier.clickable {
        onClick()
    }, supportingContent = {
        SingleLineText(artist)
    })
}

@Composable
fun ArtistScreen(id: String, nav: NavController, viewModel: ArtistViewModel = hiltViewModel()) {
    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()
    val tls by viewModel.mpdTls.collectAsState()

    LaunchedEffect(host, port, tls) {
        val addr = withContext(Dispatchers.IO) {
            try {
                InetSocketAddress(host, port)
            } catch (e: Throwable) {
                null
            }
        }
        viewModel.connect(addr, tls)
    }

    var name: String? by remember { mutableStateOf(null) }
    val songs by viewModel.songs.collectAsState()
    LaunchedEffect(id) {
        name = viewModel.getName(id)
    }

    Column {
        val songs = songs
        if (songs != null) {
            val scope = rememberCoroutineScope()
            LazyColumn(
                Modifier
                    .weight(1f)
            ) {
                itemsIndexed(songs) { i, song ->
                    ListItem(headlineContent = {
                        SingleLineText(song.title)
                    }, Modifier.clickable {
                        scope.launch {
                            viewModel.player.setQueue(songs.map {
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(it.title)
                                    .setArtist(it.artistId)
                                    .setAlbumTitle(it.albumId)
                                    .build()
                                MediaItem.Builder()
                                    .setMediaId(it.id)
                                    .setMediaMetadata(metadata)
                                    .setUri(Uri.parse(""))
                                    .build()
                            }, i)
                        }
                    }, supportingContent = {
                        SingleLineText(name ?: "")
                    })
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(id: String, nav: NavController, viewModel: AlbumViewModel = hiltViewModel()) {
    val tls by viewModel.mpdTls.collectAsState()
    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()

    LaunchedEffect(host, port, tls) {
        val addr = withContext(Dispatchers.IO) {
            try {
                InetSocketAddress(host, port)
            } catch (e: Throwable) {
                null
            }
        }
        viewModel.connect(addr, tls)
    }

    var name: String? by remember { mutableStateOf(null) }
    val tracks by viewModel.trackList.collectAsState()
    LaunchedEffect(id) {
        name = viewModel.getName(id)
    }

    Column {
        val tracks = tracks
        if (tracks != null) {
            val scope = rememberCoroutineScope()
            LazyColumn(
                Modifier
                    .weight(1f)
            ) {
                itemsIndexed(tracks) { i, track ->
                    ListItem(headlineContent = {
                        SingleLineText(track.title)
                    }, Modifier.clickable {
                        scope.launch {
                            viewModel.player.setQueue(tracks.map {
                                val metadata = MediaMetadata.Builder()
                                    .setTitle(it.title)
                                    .setArtist(it.artistId)
                                    .setAlbumTitle(it.albumId)
                                    .build()
                                MediaItem.Builder()
                                    .setMediaId(it.id)
                                    .setMediaMetadata(metadata)
                                    .setUri(Uri.parse(""))
                                    .build()
                            }, i)
                        }
                    }, supportingContent = {
                        SingleLineText(name ?: "")
                    }, leadingContent = {
                        SingleLineText(track.track.toString())
                    })
                }
            }
        }
    }
}
