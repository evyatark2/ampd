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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.media2.common.SessionPlayer
import androidx.palette.graphics.Palette
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.R
import xyz.stalinsky.ampd.Song
import kotlin.math.roundToLong

@ExperimentalMaterialApi
@Composable
fun PlayerSheet(enabled: Boolean,
                state: Int,
                playlist: List<Pair<String, Song>>,
                currentItem: Pair<Int, Bitmap?>,
                progress: Long,
                swipeState: SwipeableState<Boolean>,
                onPrev: () -> Unit,
                onPlayPause: () -> Unit,
                onNext: () -> Unit,
                onSeek: (Long) -> Unit,
                modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val threeHundredDp = with(LocalDensity.current) { 300.dp.toPx() }

    if (swipeState.direction < 0f || (swipeState.currentValue && swipeState.direction == 0f)) {
        BackHandler {
            scope.launch {
                swipeState.animateTo(false)
            }
        }
    }

    Surface(modifier.swipeable(swipeState, mapOf(0f to false, -threeHundredDp to true), Orientation.Vertical, enabled).clickable(enabled) {
        scope.launch {
            swipeState.animateTo(!swipeState.currentValue)
        }
    }, elevation = 4.dp) {
        Box(Modifier.fillMaxSize()) {
            val palette = currentItem.second?.let {
                Palette.from(it).generate()
            }

            val currentItemIndex = currentItem.first
            val title = if (currentItemIndex != -1) playlist[currentItemIndex].second.title else ""
            val artist = if (currentItemIndex != -1) playlist[currentItemIndex].second.title else ""
            val duration = if (currentItemIndex != -1) playlist[currentItemIndex].second.duration else 0

            if (swipeState.direction != 0f || !swipeState.currentValue) {
                RedactedPlayer(title, artist, 1 + swipeState.offset.value / threeHundredDp, state, Color(palette?.getDarkVibrantColor(0) ?: 0), onPlayPause)
            }

            if (swipeState.direction != 0f || swipeState.currentValue) {
                ExpandedPlayer(title,
                    artist,
                    currentItem.second,
                    -swipeState.offset.value / threeHundredDp,
                    state,
                    progress,
                    duration,
                    onPrev,
                    onPlayPause,
                    onNext,
                    onSeek)
            }
        }
    }
}

@Composable
fun RedactedPlayer(title: String, artist: String, alpha: Float, playerState: Int, color: Color?, onPlayPause: () -> Unit) {
    if (alpha > 0f) {
        val modifier = if (color != null) Modifier.background(color) else Modifier
        Box(modifier.height(372.dp)) {
            ConstraintLayout(Modifier.fillMaxWidth().height(72.dp).alpha(alpha)) {
                val (titleConstraint, artistConstraint, buttonConstraint) = createRefs()

                SingleLineText(title, Modifier.paddingFromBaseline(28.dp).constrainAs(titleConstraint) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start, 16.dp)
                    end.linkTo(buttonConstraint.start, 28.dp)
                    width = Dimension.fillToConstraints
                }, style = MaterialTheme.typography.subtitle1)

                SingleLineText(artist, Modifier.paddingFromBaseline(48.dp).constrainAs(artistConstraint) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start, 16.dp)
                    end.linkTo(buttonConstraint.start, 28.dp)
                    width = Dimension.fillToConstraints
                }, style = MaterialTheme.typography.caption)

                IconButton(onClick = onPlayPause, Modifier.constrainAs(buttonConstraint) {
                    end.linkTo(parent.end, 16.dp)
                    top.linkTo(parent.top, 24.dp)

                    width = Dimension.value(24.dp)
                    height = Dimension.value(24.dp)
                }) {
                    if (playerState == SessionPlayer.PLAYER_STATE_PAUSED) Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
                    else Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")

                }
            }
        }
    }
}

@Composable
fun ExpandedPlayer(title: String,
                   artist: String,
                   art: Bitmap?,
                   alpha: Float,
                   playerState: Int,
                   progress: Long,
                   duration: Long,
                   onPrev: () -> Unit,
                   onPlayPause: () -> Unit,
                   onNext: () -> Unit,
                   onSeek: (Long) -> Unit) {
    if (alpha > 0f) {
        ConstraintLayout(Modifier.fillMaxWidth().height(372.dp).alpha(alpha)) {
            val (imageConstraint, titleConstraint, artistConstraint, seekBarConstraint, playlistButtonConstraint, backConstraint, playConstraint, forwardConstraint) = createRefs()

            val painter = if (art != null) {
                BitmapPainter(art.asImageBitmap())
            } else {
                painterResource(R.drawable.ic_launcher_foreground)
            }

            Image(painter, "", Modifier.constrainAs(imageConstraint) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)

                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }, contentScale = ContentScale.Crop)

            SingleLineText(title, Modifier.constrainAs(titleConstraint) {
                bottom.linkTo(artistConstraint.top, 16.dp)
                start.linkTo(parent.start, 16.dp)
                end.linkTo(parent.end, 16.dp)
            }, style = MaterialTheme.typography.h6)

            SingleLineText(artist, Modifier.constrainAs(artistConstraint) {
                bottom.linkTo(seekBarConstraint.top, 16.dp)
                start.linkTo(parent.start, 16.dp)
                end.linkTo(parent.end, 16.dp)
            }, style = MaterialTheme.typography.subtitle1)

            SeekBar(progress = progress, buffered = 0, max = duration, onSeek = onSeek, Modifier.constrainAs(seekBarConstraint) {
                bottom.linkTo(playConstraint.top, 16.dp)
                start.linkTo(parent.start, 16.dp)
                end.linkTo(parent.end, 16.dp)

                width = Dimension.fillToConstraints
            })

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
                if (playerState == SessionPlayer.PLAYER_STATE_PAUSED) Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
                else Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")
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


/**
 * @param progress The current progress of the bar
 * @param buffered The current buffered progress of the bar
 * @param max The maximum value the bar can hold
 * @param onSeek Will be called when the user requests a seek
 */
@Composable
fun SeekBar(progress: Long, buffered: Long, max: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var isSeeking by remember { mutableStateOf(false) }
    var tapProgress by remember { mutableStateOf(0f) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SingleLineText("${((if (isSeeking) (tapProgress * max).toLong() else progress) / 1000) / 60}:${(((if (isSeeking) (tapProgress * max).toLong() else progress) / 1000) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.subtitle1)

        Slider(value = if (!isSeeking) progress.toFloat() / max else tapProgress, onValueChange = {
            if (!isSeeking) {
                isSeeking = true
            }

            tapProgress = it
        }, onValueChangeFinished = {
            onSeek((tapProgress * max).roundToLong())
            isSeeking = false
        }, modifier = Modifier.weight(1f))

        SingleLineText("${(max / 1000) / 60}:${((max / 1000) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.subtitle1)
    }
}