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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import io.ktor.network.sockets.SocketAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.model.Song
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel
import xyz.stalinsky.ampd.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Main(main: MainViewModel, artists: ArtistsViewModel, albums: AlbumsViewModel, artist: ArtistViewModel, settingsViewModel: SettingsViewModel, tabsViewModel: TabsSettingViewModel) {
    val state = rememberPlayerSheetScaffoldState()
    val navController = rememberNavController()

    val scope = rememberCoroutineScope()

    val loading by main.loading.collectAsState()
    val playing by main.playing.collectAsState()
    val currentItem by main.currentItem.collectAsState()
    val queue by main.queue.collectAsState()
    val duration by main.duration.collectAsState()

    val route by navController.currentBackStackEntryAsState()
    PlayerSheetScaffold({
        Player(route?.destination?.route == "main" || route?.destination?.route == "artist/{id}", state.playerState, loading, playing, if (currentItem != -1) { queue?.run { Pair(this, currentItem) } } else null, {
            (main.progress().toFloat()) / (duration.toFloat())
        }, {
            scope.launch {
                main.play()
            }
        }, {
           scope.launch {
               main.pause()
           }
        }, {
            scope.launch {
                main.next()
            }
        }, {
            scope.launch {
                main.prev()
            }
        })
    }, Modifier, {
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
        })}, state) {
        NavHost(navController, startDestination = "main") {
            composable("main") {
                MainScreen(main, artists, albums, navController)
            }

            composable("settings") {
                SettingsScreen(settingsViewModel, navController)
            }

            composable("tabs") {
                TabsSettingScreen(tabsViewModel)
            }

            composable("artist/{id}", listOf(navArgument("id") {type = NavType.StringType })) {
                val id = it.arguments?.getString("id")
                if (id != null) {
                    val host by settingsViewModel.mpdHost.collectAsState()
                    val port by settingsViewModel.mpdPort.collectAsState()

                    var addr: SocketAddress? by remember { mutableStateOf(null) }
                    LaunchedEffect(host, port) {
                        withContext(Dispatchers.IO) {
                            addr = InetSocketAddress(host, port)
                        }
                    }
                    val myAddr = addr
                    if (myAddr != null) {
                        ArtistScreen(myAddr, id, artist, navController)
                    } else {
                        CircularProgressIndicator()
                    }
                } else {
                    // If an ID wasn't passed, just pop the backstack
                    navController.popBackStack()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, artists: ArtistsViewModel, albums: AlbumsViewModel, nav: NavController) {
    val tabs by viewModel.tabs.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()

    var addr: SocketAddress? by remember { mutableStateOf(null) }
    LaunchedEffect(host, port) {
        withContext(Dispatchers.IO) {
            if (host != "" && port > 0)
                addr = InetSocketAddress(host, port)
        }
    }
    val myAddr = addr

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
                                text = { Text("Artists") })

                        Settings.TabType.TAB_TYPE_ALBUMS ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text("Albums") })

                        Settings.TabType.TAB_TYPE_SONGS ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text("Songs") })

                        Settings.TabType.TAB_TYPE_GENRES ->
                            Tab(
                                selected = i == pagerState.currentPage,
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(i)
                                    }
                                },
                                text = { Text("Genres") })

                        Settings.TabType.UNRECOGNIZED -> TODO()
                    }
                }
            }
        }

        HorizontalPager(pagerState, Modifier.fillMaxSize()) {
            val tab = tabs[it]
            if (tab.enabled) {
                when (tab.type) {
                    Settings.TabType.TAB_TYPE_ARTISTS -> {
                        if (myAddr != null) {
                            ArtistsScreen(myAddr, artists) {
                                nav.navigate("artist/${it}")
                            }
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        }
                    }

                    Settings.TabType.TAB_TYPE_ALBUMS -> {
                        if (myAddr != null) {
                            AlbumsScreen(myAddr, albums)
                        } else {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        }
                    }

                    Settings.TabType.TAB_TYPE_SONGS -> TODO()
                    Settings.TabType.TAB_TYPE_GENRES -> TODO()
                    Settings.TabType.UNRECOGNIZED -> TODO()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, nav: NavController) {
    Column {
        var showDialog by remember { mutableStateOf(false) }
        var dialog by remember { mutableStateOf(Dialog.LIBRARY_HOST_DIALOG) }
        val scope = rememberCoroutineScope()

        val libraryHost by viewModel.libraryHost.collectAsState()
        val libraryPort by viewModel.libraryPort.collectAsState()
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
                val value = value
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

        ListItem(headlineContent = { Text("Tabs") }, modifier = Modifier.clickable {
            nav.navigate("tabs")
        })
        ListItem({ Text("Music Library Host") }, modifier = Modifier.clickable {
            dialog = Dialog.LIBRARY_HOST_DIALOG
            showDialog = true
        })
        ListItem({ Text("Music Library Port") }, modifier = Modifier.clickable {
            dialog = Dialog.LIBRARY_PORT_DIALOG
            showDialog = true
        })
        ListItem({ Text("MPD Host") }, modifier = Modifier.clickable {
            dialog = Dialog.MPD_HOST_DIALOG
            showDialog = true
        })
        ListItem({ Text("MPD Port") }, modifier = Modifier.clickable {
            dialog = Dialog.MPD_PORT_DIALOG
            showDialog = true
        })
    }
}

@Composable
fun TabsSettingScreen(viewModel: TabsSettingViewModel) {
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
fun ArtistsScreen(addr: SocketAddress, viewModel: ArtistsViewModel, onClick: (String) -> Unit) {
    var artists: List<Artist>? by remember { mutableStateOf(null) }
    LaunchedEffect(addr) {
        artists = viewModel.getArtists(addr)
    }

    val myArtists = artists
    if (myArtists != null) {
        Artists(myArtists) {
            onClick(myArtists[it].id)
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
fun Artists(artists: List<Artist>, onClick: (Int) -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)) {
        itemsIndexed(artists) {i, artist ->
            Artist(artist.name) {
                onClick(i)
            }
        }
    }
}

@Composable
fun Artist(name: String, onClick: () -> Unit) {
    ListItem(headlineContent = {
        Text(name)
    }, Modifier.clickable {
        onClick()
    })
}

@Composable
fun AlbumsScreen(addr: SocketAddress, viewModel: AlbumsViewModel) {
    var albums: List<Album>? by remember { mutableStateOf(null) }
    LaunchedEffect(addr) {
        albums = viewModel.getAlbums(addr)
    }

    val myAlbums = albums
    if (myAlbums == null) {
        Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    } else {
        Albums(myAlbums) { }
    }
}

@Composable
fun Albums(albums: List<Album>, onClick: (Int) -> Unit) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)) {
        itemsIndexed(albums) { i, album ->
            Album(album.title, album.artistId) {
                onClick(i)
            }
        }
    }
}

@Composable
fun Album(title: String, artist: String, onClick: () -> Unit) {
    ListItem(headlineContent = {
        Text(title)
    }, Modifier.clickable {
        onClick()
    }, supportingContent = {
        Text(artist)
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(addr: SocketAddress, id: String, viewModel: ArtistViewModel, nav: NavController) {
    var name: String? by remember { mutableStateOf(null) }
    var songs: List<Song>? by remember { mutableStateOf(null) }
    LaunchedEffect(addr, id) {
        name = viewModel.getName(addr, id)
    }

    LaunchedEffect(addr, id) {
        songs = viewModel.getSongs(addr, id)
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
                        Text(song.title)
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
                                    .setUri(Uri.parse("https://stalinsky.xyz/dav/${it.file}"))
                                    .build()
                            }, i)
                        }
                    }, supportingContent = {
                        Text(name ?: "")
                    })
                }
            }
        }
    }
}