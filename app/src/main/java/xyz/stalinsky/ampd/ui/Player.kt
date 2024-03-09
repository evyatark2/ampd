package xyz.stalinsky.ampd.ui

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PlayerValue {
    PartiallyExpanded,
    Expanded,
}

@OptIn(ExperimentalFoundationApi::class)
class PlayerState(
    public val show: MutableTransitionState<Boolean>,
    public val drag: AnchoredDraggableState<Boolean>
) {
    suspend fun show() {
    }

    suspend fun expand() {
        drag.anchoredDrag(true) { anchors, target -> }
    }

    suspend fun collapse() {
        drag.anchoredDrag(false) { anchors, target -> }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun Player(
    visible: Boolean,
    state: PlayerState,
    isLoading: Boolean,
    isPlaying: Boolean,
    queue: Pair<List<MediaItem>, Int>?,
    getProgress: suspend () -> Float,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    if (visible && queue != null) {
        state.show.targetState = true
    } else {
        state.show.targetState = false
    }

    val scope = rememberCoroutineScope()

    if (queue != null) {
        Surface(Modifier
            .fillMaxSize()
            .background(Color.Red)
            .clickable {
                scope.launch {
                    state.drag.anchoredDrag(!state.drag.currentValue) { anchors, target ->
                        dragTo(
                            anchors.positionOf(target)
                        )
                    }
                }
            }
            .anchoredDraggable(state.drag, Orientation.Vertical),
            RoundedCornerShape(28.dp),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp) {
            if (!state.drag.targetValue || !state.drag.currentValue) {
                val maxOffset = state.drag.anchors.positionOf(true)
                Box(
                    Modifier
                        .alpha((state.drag.offset - maxOffset) / -maxOffset)
                        .fillMaxSize()
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
                        if (isLoading) {
                            CircularProgressIndicator()
                        } else {
                            IconButton(onClick = {
                                if (isPlaying) {
                                    onPause()
                                } else {
                                    onPlay()
                                }
                            }) {
                                if (isPlaying) {
                                    Icon(Icons.Default.Pause, "")
                                } else {
                                    Icon(Icons.Default.PlayArrow, "")
                                }
                            }
                        }
                    })
                }
            }

            if (state.drag.targetValue || state.drag.currentValue) {
                var progress by remember { mutableFloatStateOf(0f) }
                if (isPlaying) {
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
                        .alpha((state.drag.offset) / maxOffset)
                        .fillMaxSize()

                ) {
                    val (titleRef, albumRef, artistRef, backRef, progressRef, forwardRef) = createRefs()

                    IconButton({ onPrev() }, Modifier.constrainAs(backRef) {
                        start.linkTo(parent.start)
                        end.linkTo(progressRef.start)
                        bottom.linkTo(parent.bottom, 16.dp)

                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }) {
                        Icon(Icons.Default.SkipPrevious, "")
                    }

                    Slider(value = progress, onValueChange = {}, Modifier.constrainAs(progressRef) {
                        start.linkTo(backRef.end)
                        end.linkTo(forwardRef.start)
                        bottom.linkTo(parent.bottom, 16.dp)

                        width = Dimension.fillToConstraints
                        height = Dimension.wrapContent
                    })

                    IconButton({ onNext() }, Modifier.constrainAs(forwardRef) {
                        start.linkTo(progressRef.end)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom, 16.dp)

                        width = Dimension.wrapContent
                        height = Dimension.wrapContent
                    }) {
                        Icon(Icons.Default.SkipNext, "")
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
    val anchors = DraggableAnchors<Boolean> {
        true at with(density) { -300.dp.toPx() }
        false at 0f
    }
    return remember {
        PlayerState(
            MutableTransitionState(false),
            AnchoredDraggableState(false, anchors, { it / 2 }, { 0f }, tween())
        )
    }
}