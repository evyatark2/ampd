package xyz.stalinsky.ampd.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.Settings
import xyz.stalinsky.ampd.data.AlbumsRepository
import xyz.stalinsky.ampd.data.ArtistsRepository
import xyz.stalinsky.ampd.datasource.MpdRemoteDataSource
import xyz.stalinsky.ampd.model.Album
import xyz.stalinsky.ampd.model.Artist
import xyz.stalinsky.ampd.ui.viewmodel.AlbumsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.MainViewModel
import xyz.stalinsky.ampd.ui.viewmodel.ArtistsViewModel
import xyz.stalinsky.ampd.ui.viewmodel.PlayerViewModel

@Composable
fun TopScreen(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "main") {
        composable("main") {
            MainScreen(viewModel, navController)
        }

        composable("settings") {
            SettingsScreen(SettingsViewModel(viewModel.settings, viewModel.config), navController)
        }

        composable("tabs") {
            TabsSettingScreen(TabsSettingViewModel(viewModel.config))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, nav: NavController) {
    val tabs by viewModel.tabs.collectAsState()
    val defaultTab by viewModel.defaultTab.collectAsState()

    val host by viewModel.mpdHost.collectAsState()
    val port by viewModel.mpdPort.collectAsState()

    val mpdDataSource = MpdRemoteDataSource(host, port, viewModel.viewModelScope)

    val state = rememberBottomSheetScaffoldState(rememberStandardBottomSheetState(SheetValue.Hidden, skipHiddenState = false))

    val scope = rememberCoroutineScope()

    BottomSheetScaffold({
        Player(state.bottomSheetState.currentValue, { scope.launch {
            state.bottomSheetState.show()
        }}, { scope.launch {
            state.bottomSheetState.expand()
        }}, { scope.launch {
            state.bottomSheetState.hide()
        }}, PlayerViewModel(viewModel.player))
    }, scaffoldState = state, topBar = {
            TopAppBar(title = { Text("AMPD") }, actions = {
                IconButton(onClick = {  }) {
                    Icon(Icons.Default.Search, "")
                }
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Default.Settings, "")
                }
            })
        }) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {

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
                            ArtistsScreen(ArtistsViewModel(ArtistsRepository(mpdDataSource)))
                        }

                        Settings.TabType.TAB_TYPE_ALBUMS -> {
                            AlbumsScreen(AlbumsViewModel(AlbumsRepository(mpdDataSource)))
                        }

                        Settings.TabType.TAB_TYPE_SONGS -> TODO()
                        Settings.TabType.TAB_TYPE_GENRES -> TODO()
                        Settings.TabType.UNRECOGNIZED -> TODO()
                    }
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
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") })
    }) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
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
fun ArtistsScreen(viewModel: ArtistsViewModel) {
    val _artists by viewModel.artists.collectAsState()
    val artists = _artists
    if (artists == null) {

    } else {
        Artists(artists, { })
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
fun AlbumsScreen(viewModel: AlbumsViewModel) {
    val albums_ by viewModel.albums.collectAsState()
    val albums = albums_
    if (albums == null) {

    } else {
        Albums(albums, { })
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
