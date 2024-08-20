package xyz.stalinsky.ampd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.ui.utils.SingleLineText
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalFoundationApi::class)
class PlayerState(
    val show: MutableTransitionState<Boolean>,
    val drag: AnchoredDraggableState<Boolean>,
    val expand: MutableTransitionState<Boolean>
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Player(
    visible: Boolean,
    state: PlayerState,
    isLoading: Boolean,
    isPlaying: Boolean,
    queue: Pair<List<MediaItem>, Int>?,
    getProgress: suspend () -> Long,
    duration: Long,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onQueueItemClicked: (Int) -> Unit
) {
    if (queue != null) {
        state.show.targetState = visible

        val scope = rememberCoroutineScope()

        Surface(Modifier
            .fillMaxWidth(),
            RoundedCornerShape(28.dp),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp) {
            if (!state.drag.targetValue || !state.drag.currentValue) {
                val maxOffset = state.drag.anchors.positionOf(true)
                Box(
                    Modifier
                        .alpha((state.drag.offset - maxOffset) / -maxOffset)
                        .fillMaxSize()
                        .anchoredDraggable(
                            state.drag,
                            Orientation.Vertical,
                            !state.expand.currentState && !state.expand.targetState
                        )
                        .clickable(!state.expand.currentState && !state.expand.targetState) {
                            scope.launch {
                                state.drag.anchoredDrag(!state.drag.currentValue) { anchors, target ->
                                    dragTo(
                                        anchors.positionOf(target)
                                    )
                                }
                            }
                        }
                ) {
                    ListItem({
                        Text(
                            queue.first[queue.second].mediaMetadata.title.toString(),
                            Modifier,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }, Modifier.align(Alignment.TopCenter), supportingContent = {
                        Text(
                            queue.first[queue.second].mediaMetadata.artist.toString(),
                            Modifier,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }, trailingContent = {
                        IconButton(onClick = {
                            if (isPlaying) {
                                onPause()
                            } else {
                                onPlay()
                            }
                        }, Modifier, !isLoading) {
                            if (isPlaying) {
                                Icon(Icons.Default.Pause, "")
                            } else {
                                Icon(Icons.Default.PlayArrow, "")
                            }
                        }
                    })
                }
            }

            val transition = updateTransition(state.expand, "")
            val alpha by transition.animateFloat(label = "") {
                if (it)
                    1f
                else
                    0f
            }

            if ((state.drag.targetValue || state.drag.currentValue) && (!state.expand.targetState || !state.expand.currentState)) {
                var isSeeking by remember { mutableStateOf(false) }
                var progress by remember { mutableLongStateOf(0) }
                if (isPlaying && !isSeeking) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            withFrameNanos { }
                            progress = getProgress()
                        }
                    }
                }
                val maxOffset = state.drag.anchors.positionOf(true)
                ConstraintLayout(
                    Modifier
                        .alpha((state.drag.offset) / maxOffset - alpha)
                        .fillMaxWidth()
                        .height(372.dp)
                        .anchoredDraggable(
                            state.drag,
                            Orientation.Vertical,
                            !state.expand.currentState && !state.expand.targetState
                        )
                        .clickable(!state.expand.currentState && !state.expand.targetState) {
                            scope.launch {
                                state.drag.anchoredDrag(!state.drag.currentValue) { anchors, target ->
                                    dragTo(
                                        anchors.positionOf(target)
                                    )
                                }
                            }
                        }

                ) {
                    val current = queue.first[queue.second]
                    val (queueRef, titleRef, artistRef, posRef, currentTimeRef, progressRef, durationRef, backRef, playRef, forwardRef) = createRefs()

                    IconButton({
                        state.expand.targetState = true
                    }, Modifier.constrainAs(queueRef) {
                        end.linkTo(parent.end, 16.dp)
                        top.linkTo(parent.top, 16.dp)
                    }) {
                        Icon(Icons.AutoMirrored.Default.QueueMusic, "")
                    }

                    Text(current.mediaMetadata.title.toString(), Modifier.constrainAs(titleRef) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(artistRef.top, 16.dp)
                    })

                    Text(current.mediaMetadata.artist.toString(), Modifier.constrainAs(artistRef) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(progressRef.top, 16.dp)
                    })

                    val progressDur = progress.milliseconds
                    val (progressMin, progressSec) = progressDur.toComponents { min, sec, _ ->
                        Pair(min, sec)
                    }
                    val progressStr = String.format("%02d:%02d", progressMin, progressSec)

                    Text(progressStr, Modifier.constrainAs(currentTimeRef) {
                        start.linkTo(parent.start)
                        end.linkTo(progressRef.start)
                        bottom.linkTo(playRef.top, 16.dp)
                    })

                    Slider(progress.toFloat() / duration.toFloat(), {
                        isSeeking = true
                        progress = (it * duration.toFloat()).toLong()
                    }, Modifier.constrainAs(progressRef) {
                        start.linkTo(currentTimeRef.end)
                        end.linkTo(durationRef.start)
                        bottom.linkTo(playRef.top, 16.dp)

                        width = Dimension.fillToConstraints
                        height = Dimension.wrapContent
                    }, onValueChangeFinished = {
                        onSeek(progress)
                        isSeeking = false
                    })

                    val totalDuration = duration.milliseconds
                    val (totalMin, totalSec) = totalDuration.toComponents { min, sec, _ ->
                        Pair(min, sec)
                    }
                    val totalStr = String.format("%02d:%02d", totalMin, totalSec)

                    Text(totalStr, Modifier.constrainAs(durationRef) {
                        start.linkTo(progressRef.end)
                        end.linkTo(parent.end)
                        bottom.linkTo(playRef.top, 16.dp)
                    })

                    IconButton({ onPrev() }, Modifier.constrainAs(backRef) {
                        start.linkTo(parent.start)
                        end.linkTo(playRef.start)
                        top.linkTo(playRef.top)
                        bottom.linkTo(playRef.bottom)

                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }) {
                        Icon(Icons.Default.SkipPrevious, "")
                    }

                    IconButton({
                        if (isPlaying) {
                            onPause()
                        } else {
                            onPlay()
                        }
                    }, Modifier.constrainAs(playRef) {
                        start.linkTo(backRef.end)
                        end.linkTo(forwardRef.start)
                        bottom.linkTo(parent.bottom, 16.dp)

                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }, !isLoading) {
                        if (isPlaying) {
                            Icon(Icons.Default.Pause, "")
                        } else {
                            Icon(Icons.Default.PlayArrow, "")
                        }
                    }

                    IconButton({ onNext() }, Modifier.constrainAs(forwardRef) {
                        start.linkTo(playRef.end)
                        end.linkTo(parent.end)
                        top.linkTo(playRef.top)
                        bottom.linkTo(playRef.bottom)

                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }) {
                        Icon(Icons.Default.SkipNext, "")
                    }
                }
            }

            if (state.expand.targetState || state.expand.currentState) {
                if (state.expand.targetState) {
                    BackHandler {
                        state.expand.targetState = false
                    }
                }

                ConstraintLayout(
                    Modifier
                        .alpha(alpha)
                        .fillMaxSize()
                ) {
                    val (closeRef, queueRef) = createRefs()
                    IconButton({ state.expand.targetState = false }, Modifier.constrainAs(closeRef) {
                        end.linkTo(parent.end, 16.dp)
                        top.linkTo(parent.top, 16.dp)
                    }) {
                        Icon(Icons.Default.Close, "")
                    }

                    LazyColumn(Modifier.constrainAs(queueRef) {
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        top.linkTo(closeRef.bottom, 16.dp)
                        bottom.linkTo(parent.bottom)

                        width = Dimension.fillToConstraints
                        height = Dimension.fillToConstraints
                    }, contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(queue.first) { i, it ->
                            ListItem({ SingleLineText(it.mediaMetadata.title.toString()) }, Modifier.clickable {
                                onQueueItemClicked(i)
                            })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberPlayerState(): PlayerState {
    val density = LocalDensity.current
    val anchors = DraggableAnchors {
        true at with(density) { -300.dp.toPx() }
        false at 0f
    }
    return remember {
        PlayerState(
            MutableTransitionState(false),
            AnchoredDraggableState(false, anchors, { it / 2 }, { 0f }, tween()),
            MutableTransitionState(false)
        )
    }
}