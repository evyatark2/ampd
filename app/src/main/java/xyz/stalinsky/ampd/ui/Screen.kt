package xyz.stalinsky.ampd.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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

    val route by navController.currentBackStackEntryAsState()
    PlayerSheetScaffold(state, Modifier, {
        Player(route?.destination?.route == "main" || route?.destination?.route == "artist/{id}" || route?.destination?.route == "album/{id}",
            state.playerState,
            loading,
            playing,
            if (currentItem != -1) { queue?.run { Pair(this, currentItem) } } else null,
            {
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
                MainScreen({ force = true }, navController)
            }

            composable("settings") {
                SettingsScreen(navController)
            }

            composable("tabs") {
                TabsSettingScreen()
            }

            composable("artist/{id}", listOf(navArgument("id") {type = NavType.StringType })) {
                val id = it.arguments!!.getString("id")
                ArtistScreen(id!!, { force = true }, navController)
            }

            composable("album/{id}", listOf(navArgument("id") { type = NavType.StringType })) {
                val id = it.arguments!!.getString("id")
                AlbumScreen(id!!, { force = true }, navController)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onRetry: () -> Unit,
    nav: NavController,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val tabs by viewModel.tabs.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

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
                        ArtistsScreen(onRetry, {
                            nav.navigate("artist/${it}")
                        })
                    }

                    Settings.TabType.TAB_TYPE_ALBUMS -> {
                        AlbumsScreen(onRetry, {
                            nav.navigate("album/${it}")
                        })
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

    val scope = rememberCoroutineScope()

    ConnectionScreen(artistsState.value, onRetry) { artists ->
        Artists(artists, {
            onClick(artists[it].id)
        }, {
            scope.launch {
                viewModel.addToQueue(artists[it].id)
            }
        }) {
            scope.launch {
                viewModel.playNext(artists[it].id)
            }
        }
    }
}

@Composable
fun Artists(artists: List<Artist>,
            onClick: (Int) -> Unit,
            onAddToQueue: (Int) -> Unit,
            onPlayNext: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
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
fun AlbumsScreen(onRetry: () -> Unit, onClick: (String) -> Unit, viewModel: AlbumsViewModel = hiltViewModel()) {
    val albumsState = viewModel.albums.collectAsState()

    val scope = rememberCoroutineScope()

    ConnectionScreen(albumsState.value, onRetry) { albums ->
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
    }
}

@Composable
fun Albums(albums: List<Album>, onClick: (Int) -> Unit, onAddToQueue: (Int) -> Unit, onPlayNext: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
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
fun ArtistScreen(id: String, onRetry: () -> Unit, nav: NavController, viewModel: ArtistViewModel = hiltViewModel()) {
    var name: String? by remember { mutableStateOf(null) }
    val songs by viewModel.songs.collectAsState()
    LaunchedEffect(id) {
        name = viewModel.getName(id)
    }

    ConnectionScreen(songs, onRetry) {
        val scope = rememberCoroutineScope()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 8.dp)) {
            itemsIndexed(it) { i, song ->
                ListItem({
                    SingleLineText(song.title)
                }, Modifier.clickable {
                    scope.launch {
                        viewModel.setQueue(it.map {
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

@Composable
fun AlbumScreen(id: String, onRetry: () -> Unit, nav: NavController, viewModel: AlbumViewModel = hiltViewModel()) {
    var name: String? by remember { mutableStateOf(null) }
    val tracks by viewModel.trackList.collectAsState()
    LaunchedEffect(id) {
        name = viewModel.getName(id)
    }

    ConnectionScreen(tracks, onRetry) {
        val scope = rememberCoroutineScope()
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
                    scope.launch {
                        viewModel.setQueue(it.map {
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
                    ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                        SingleLineText(
                            "${if (multiside) "${track.side}-" else ""}${track.track}"
                        )
                    }
                })
            }
        }
    }
}

@Composable
fun <T> ConnectionScreen(state: MpdConnectionState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        is MpdConnectionState.Error -> {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.align(Alignment.Center)) {
                    Text(state.err.message ?: "", Modifier.align(Alignment.CenterHorizontally))
                    Button({
                        onRetry()
                    }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Retry")
                    }
                }
            }
        }
        is MpdConnectionState.Loading -> {
            Box(Modifier.fillMaxSize()) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
        is MpdConnectionState.Ok -> {
            content(state.res)
        }
    }
}