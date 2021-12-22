package xyz.stalinsky.ampd.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.media2.common.SessionPlayer
import androidx.palette.graphics.Palette
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.R
import xyz.stalinsky.ampd.Song
import kotlin.math.roundToInt

@ExperimentalMaterialApi
@Composable
fun PlayerSheet(enabled: Boolean, state: Int, playlist: List<Pair<String, Song>>, currentItem: Pair<Int, Bitmap?>, swipeState: SwipeableState<Boolean>, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val threeHundredDp = with(LocalDensity.current) { 300.dp.toPx() }
    Surface(elevation = 4.dp, modifier = modifier
        .offset { IntOffset(0, swipeState.offset.value.roundToInt()) }
        .swipeable(
            swipeState,
            mapOf(0f to false, -threeHundredDp to true),
            Orientation.Vertical,
            enabled
        )
        .clickable(enabled) {
            scope.launch {
                swipeState.animateTo(!swipeState.currentValue)
            }
        }) {
        Box(Modifier.fillMaxSize()) {
            val palette = currentItem.second?.let { Palette.from(it).generate() }
            when {
                swipeState.direction != 0f -> {
                    if (currentItem.first != -1) {
                        RedactedPlayer(playlist[currentItem.first].second.title, playlist[currentItem.first].second.artist, 1 + swipeState.offset.value / threeHundredDp, state, Color(
                            palette?.getDarkVibrantColor(0) ?: 0
                        ), onPlayPause)
                        ExpandedPlayer(playlist[currentItem.first].second.title, playlist[currentItem.first].second.artist, currentItem.second, -swipeState.offset.value / threeHundredDp, state, onPrev, onPlayPause, onNext)
                    } else {
                        RedactedPlayer("", "", 1 + swipeState.offset.value / threeHundredDp, state, Color(
                            palette?.getDarkVibrantColor(0) ?: 0
                        ), onPlayPause)
                        ExpandedPlayer("", "", null, -swipeState.offset.value / threeHundredDp, state, onPrev, onPlayPause, onNext)
                    }
                }
                swipeState.currentValue -> {
                    BackHandler {
                        scope.launch {
                            swipeState.animateTo(false)
                        }
                    }

                    if (currentItem.first != -1) {
                        ExpandedPlayer(playlist[currentItem.first].second.title, playlist[currentItem.first].second.artist, currentItem.second, 1f, state, onPrev, onPlayPause, onNext)
                    } else {
                        ExpandedPlayer("", "", null, 1f, state, onPrev, onPlayPause, onNext)
                    }
                }
                else -> {
                    if (currentItem.first != -1) {
                        RedactedPlayer(playlist[currentItem.first].second.title, playlist[currentItem.first].second.artist, 1f, state, Color(palette?.getDarkVibrantColor(0) ?: 0), onPlayPause)
                    } else {
                        RedactedPlayer("", "", 1f, state, Color(palette?.getDarkVibrantColor(0) ?: 0), onPlayPause)
                    }
                }
            }
        }
    }
}

@Composable
fun RedactedPlayer(title: String, artist: String, alpha: Float, playerState: Int, color: Color?, onPlayPause: () -> Unit) {
    if (alpha > 0f) {
        val modifier = if (color != null) Modifier.background(color) else Modifier
        Box(modifier) {
            ConstraintLayout(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .alpha(alpha)) {
                val (titleConstraint, artistConstraint, buttonConstraint) = createRefs()

                Text(title,
                    Modifier
                        .paddingFromBaseline(28.dp)
                        .constrainAs(titleConstraint) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start, 16.dp)
                            end.linkTo(buttonConstraint.start, 28.dp)
                            width = Dimension.fillToConstraints
                        },
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                Text(artist,
                    Modifier
                        .paddingFromBaseline(48.dp)
                        .constrainAs(artistConstraint) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start, 16.dp)
                            end.linkTo(buttonConstraint.start, 28.dp)
                            width = Dimension.fillToConstraints
                        },
                    style = MaterialTheme.typography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)

                IconButton(onClick = onPlayPause, Modifier.constrainAs(buttonConstraint) {
                    end.linkTo(parent.end, 16.dp)
                    top.linkTo(parent.top, 24.dp)
                    bottom.linkTo(parent.bottom, 24.dp)

                    width = Dimension.value(24.dp)
                    height = Dimension.value(24.dp)
                }) {
                    if (playerState == SessionPlayer.PLAYER_STATE_PAUSED)
                        Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
                    else
                        Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")

                }
            }
        }
    }
}

@Composable
fun ExpandedPlayer(title: String, artist: String, art: Bitmap?, alpha: Float, playerState: Int, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    if (alpha > 0f) {
        ConstraintLayout(
            Modifier
                .fillMaxWidth()
                .height(372.dp)
                .alpha(alpha)) {
            val (imageConstraint, titleConstraint, artistConstraint, playlistButtonConstraint, backConstraint, playConstraint, forwardConstraint) = createRefs()

            Image(BitmapPainter(art?.asImageBitmap() ?: ImageBitmap.imageResource(R.drawable.ic_launcher_foreground)), "", Modifier.constrainAs(imageConstraint) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)

                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            },
            contentScale = ContentScale.Crop)

            Text(title, Modifier.constrainAs(titleConstraint) {
                bottom.linkTo(artistConstraint.top, 16.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            style = MaterialTheme.typography.h6)

            Text(artist, Modifier.constrainAs(artistConstraint) {
                bottom.linkTo(playConstraint.top, 16.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            style = MaterialTheme.typography.subtitle1)

            IconButton(onClick = { /*TODO*/ }, Modifier.constrainAs(playlistButtonConstraint) {
                top.linkTo(parent.top, 24.dp)
                end.linkTo(parent.end, 16.dp)
                width = Dimension.value(24.dp)
                height = Dimension.value(24.dp)
            }) {
                Icon(Icons.Default.List, "Playlist")
            }

            IconButton(onClick = onPrev, Modifier.constrainAs(backConstraint) {
                start.linkTo(parent.start)
                end.linkTo(playConstraint.start)
                bottom.linkTo(parent.bottom, 24.dp)

                width = Dimension.value(24.dp)
                height = Dimension.value(24.dp)
            }) {
                Icon(ImageVector.vectorResource(R.drawable.baseline_skip_previous_black_24dp), "Previous")
            }

            IconButton(onClick = onPlayPause, Modifier.constrainAs(playConstraint) {
                start.linkTo(backConstraint.end)
                end.linkTo(forwardConstraint.start)
                bottom.linkTo(parent.bottom, 24.dp)
                width = Dimension.value(24.dp)
                height = Dimension.value(24.dp)
            }) {
                if (playerState == SessionPlayer.PLAYER_STATE_PAUSED)
                    Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
                else
                    Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")
            }

            IconButton(onClick = onNext, Modifier.constrainAs(forwardConstraint) {
                start.linkTo(playConstraint.end)
                end.linkTo(parent.end)
                bottom.linkTo(parent.bottom, 24.dp)
                width = Dimension.value(24.dp)
                height = Dimension.value(24.dp)
            }) {
                Icon(ImageVector.vectorResource(R.drawable.baseline_skip_next_black_24dp), "Next")
            }
        }
    }
}


