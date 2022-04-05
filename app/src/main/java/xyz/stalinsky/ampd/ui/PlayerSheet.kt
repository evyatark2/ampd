package xyz.stalinsky.ampd.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.core.graphics.drawable.toBitmap
import androidx.media2.common.SessionPlayer
import androidx.palette.graphics.Palette
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.skydoves.landscapist.glide.GlideImage
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.R
import xyz.stalinsky.ampd.Song
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Terminology:
// Redacted: The lowest level of player consisting of only the title, the artist and a play/pause button; Can be swiped to expanded state
// Expanded: The widget that has the seek bar; Can be swiped to redacted; Can be extended with a click on a button
// Extended: The highest level; should be full screen, consists of the playlist; Can be shrinked with a click on a button
@ExperimentalMaterialApi
@Composable
fun PlayerSheet(state: Int,
                playlist: List<Pair<String, Song>>,
                currentItemIndex: Int,
                onChangeCurrentItemIndex: (Int) -> Unit,
                onMovePlaylistItem: (Int, Int) -> Unit,
                progress: Long,
                swipeState: SwipeableState<Boolean>,
                onPrev: () -> Unit,
                onPlayPause: () -> Unit,
                onNext: () -> Unit,
                onSeekStart: () -> Unit,
                onSeek: (Long) -> Unit,
                transition: Transition<Boolean>,
                onExtend: (Boolean) -> Unit,
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

    Surface(modifier, elevation = 4.dp) {
        val alpha = transition.animateFloat(label = "") {
            if (it)
                1.0f
            else
                0.0f
        }

        if (transition.currentState || transition.isRunning) {
            ConstraintLayout(Modifier.fillMaxSize()) {
                val (closeConstraint, playlistConstraint) = createRefs()

                IconButton({
                    onExtend(false)
                }, Modifier.constrainAs(closeConstraint) {
                    top.linkTo(parent.top, 24.dp)
                    end.linkTo(parent.end, 16.dp)
                    width = Dimension.value(24.dp)
                    height = Dimension.value(24.dp)
                }) {
                    Icon(Icons.Default.Close, "Close playlist")
                }

                LazyColumn(Modifier.alpha(alpha.value).constrainAs(playlistConstraint) {
                    top.linkTo(closeConstraint.bottom)
                    bottom.linkTo(parent.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)

                    height = Dimension.fillToConstraints
                    width = Dimension.fillToConstraints
                }, rememberLazyListState(currentItemIndex)) {
                    items(playlist.size) {
                        ConstraintLayout(Modifier.height(72.dp).fillMaxWidth().clickable { onChangeCurrentItemIndex(it) }) {
                            val (artConstraint, titleConstraint, artistConstraint) = createRefs()

                            val item = playlist[it].second

                            GlideImage(item.art, Modifier.constrainAs(artConstraint) {
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                                start.linkTo(parent.start, 16.dp)

                                width = Dimension.value(40.dp)
                                height = Dimension.value(40.dp)
                            }, requestOptions = {
                                RequestOptions().override(with(LocalDensity.current) { 40.dp.toPx().roundToInt() }).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            })

                            SingleLineText(item.title, Modifier
                                .paddingFromBaseline(28.dp)
                                .constrainAs(titleConstraint) {
                                    top.linkTo(parent.top)
                                    start.linkTo(artConstraint.end, 16.dp)
                                    end.linkTo(parent.end, 16.dp)
                                    width = Dimension.fillToConstraints
                            })

                            SingleLineText(item.artist, Modifier.paddingFromBaseline(48.dp).constrainAs(artistConstraint) {
                                top.linkTo(parent.top)
                                start.linkTo(artConstraint.end, 16.dp)
                                end.linkTo(parent.end, 16.dp)

                                width = Dimension.fillToConstraints
                            })
                        }
                    }
                }
            }
        }

        if (!transition.currentState || transition.isRunning) {
            // The second condition can fail when the playlist is still being updated but the index already reflects the new playlist
            val currentItem = if (currentItemIndex != -1 && currentItemIndex < playlist.size)
                playlist[currentItemIndex]
            else
                Pair("", Song("", "", "", "" ,"" ,0, null))

            val imageHeight = with(LocalDensity.current) { 372.dp.toPx().roundToInt() }

            BoxWithConstraints {
                val imageWidth = with(LocalDensity.current) { maxWidth.toPx().roundToInt() }
                GlideImage(currentItem.second.art, Modifier.fillMaxSize().swipeable(swipeState, mapOf(0f to false, -threeHundredDp to true), Orientation.Vertical).clickable {
                    scope.launch {
                        swipeState.animateTo(!swipeState.currentValue)
                    }
                }, requestOptions = {
                    RequestOptions().override(imageWidth, imageHeight).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                }, success = { art ->
                    val bitmap = art.drawable!!.toBitmap()
                    val palette = Palette.from(bitmap).generate()

                    val title = currentItem.second.title
                    val artist = currentItem.second.title
                    val duration = currentItem.second.duration

                    // TODO: For some reason swipeState.direction doesn't get reset to 0f when there is no swiping/animation causing
                    // TODO: ExpandedPlayer to react to user interaction when it should be hidden
                    if (swipeState.direction != 0f || !swipeState.currentValue) {
                        RedactedPlayer(title, artist, state, onPlayPause, Modifier.fillMaxWidth().height(372.dp).background(Color(palette.getDarkVibrantColor(0))).alpha(1 + swipeState.offset.value / threeHundredDp))
                    }

                    if (swipeState.direction != 0f || swipeState.currentValue) {
                        ExpandedPlayer(title,
                            artist,
                            bitmap,
                            state,
                            progress,
                            duration,
                            onPrev,
                            onPlayPause,
                            onNext,
                            onSeekStart,
                            onSeek, {
                                onExtend(true)
                            }, Modifier.fillMaxWidth().height(372.dp).alpha(-swipeState.offset.value / threeHundredDp - alpha.value))
                    }
                })
            }
        }
    }
}

@Composable
fun RedactedPlayer(title: String, artist: String, playerState: Int, onPlayPause: () -> Unit, modifier: Modifier) {
    Box(modifier) {
        ConstraintLayout(Modifier.fillMaxWidth().height(72.dp)) {
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
                if (playerState != SessionPlayer.PLAYER_STATE_PLAYING)
                    Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
                else
                    Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")

            }
        }
    }
}

@Composable
fun ExpandedPlayer(title: String,
                   artist: String,
                   art: Bitmap,
                   playerState: Int,
                   progress: Long,
                   duration: Long,
                   onPrev: () -> Unit,
                   onPlayPause: () -> Unit,
                   onNext: () -> Unit,
                   onSeekStart: () -> Unit,
                   onSeek: (Long) -> Unit,
                   onExtend: () -> Unit,
                   modifier: Modifier) {
    ConstraintLayout(modifier) {
        val (imageConstraint, titleConstraint, artistConstraint, seekBarConstraint, playlistButtonConstraint, backConstraint, playConstraint, forwardConstraint) = createRefs()

        BoxWithConstraints(Modifier.constrainAs(imageConstraint) {
                start.linkTo(parent.start)
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)

                width = Dimension.fillToConstraints
                height = Dimension.fillToConstraints
            }) {
            Image(BitmapPainter(art.asImageBitmap()), "", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }

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

        SeekBar(progress, 0, duration, onSeekStart, onSeek, Modifier.constrainAs(seekBarConstraint) {
            bottom.linkTo(playConstraint.top, 16.dp)
            start.linkTo(parent.start, 16.dp)
            end.linkTo(parent.end, 16.dp)

            width = Dimension.fillToConstraints
        })

        IconButton(onExtend, Modifier.constrainAs(playlistButtonConstraint) {
            top.linkTo(parent.top, 24.dp)
            end.linkTo(parent.end, 16.dp)
            width = Dimension.value(24.dp)
            height = Dimension.value(24.dp)
        }) {
            Icon(Icons.Default.List, "Playlist")
        }

        IconButton(onPrev, Modifier.constrainAs(backConstraint) {
            start.linkTo(parent.start)
            end.linkTo(playConstraint.start)
            bottom.linkTo(parent.bottom, 24.dp)

            width = Dimension.value(24.dp)
            height = Dimension.value(24.dp)
        }) {
            Icon(ImageVector.vectorResource(R.drawable.baseline_skip_previous_black_24dp), "Previous")
        }

        IconButton(onPlayPause, Modifier.constrainAs(playConstraint) {
            start.linkTo(backConstraint.end)
            end.linkTo(forwardConstraint.start)
            bottom.linkTo(parent.bottom, 24.dp)
            width = Dimension.value(24.dp)
            height = Dimension.value(24.dp)
        }) {
            if (playerState != SessionPlayer.PLAYER_STATE_PLAYING)
                Icon(ImageVector.vectorResource(R.drawable.baseline_play_arrow_black_24dp), "Play")
            else
                Icon(ImageVector.vectorResource(R.drawable.baseline_pause_black_24dp), "Pause")
        }

        IconButton(onNext, Modifier.constrainAs(forwardConstraint) {
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


/**
 * @param progress The current progress of the bar
 * @param buffered The current buffered progress of the bar
 * @param max The maximum value the bar can hold
 * @param onSeek Will be called when the user requests a seek
 */
@Composable
fun SeekBar(progress: Long, buffered: Long, max: Long, onSeekStart: () -> Unit, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var isSeeking by remember { mutableStateOf(false) }
    var tapProgress by remember { mutableStateOf(0f) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SingleLineText("${((if (isSeeking) (tapProgress * max).toLong() else progress) / 1000) / 60}:${(((if (isSeeking) (tapProgress * max).toLong() else progress) / 1000) % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.subtitle1)

        Slider(value = if (!isSeeking) progress.toFloat() / max else tapProgress, onValueChange = {
            if (!isSeeking) {
                onSeekStart()
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