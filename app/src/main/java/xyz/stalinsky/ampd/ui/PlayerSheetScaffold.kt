package xyz.stalinsky.ampd.ui

import androidx.compose.animation.core.InternalAnimationApi
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.createDeferredAnimation
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PlayerSheetScaffoldState(
        val playerState: PlayerState,
        val snackbarHostState: SnackbarHostState,
)

@OptIn(ExperimentalFoundationApi::class, InternalAnimationApi::class)
@Composable
fun PlayerSheetScaffold(
        state: PlayerSheetScaffoldState,
        modifier: Modifier = Modifier,
        sheetVisible: Boolean,
        sheetContent: @Composable () -> Unit,
        topBarContent: @Composable () -> Unit,
        content: @Composable () -> Unit) {
    val showTransition = updateTransition(sheetVisible, label = "")
    val deferred = showTransition.createDeferredAnimation(Float.VectorConverter)

    Layout({
        Column {
            topBarContent()
            content()
        }
        Box {
            sheetContent()
        }
    }, modifier) { measurables, c ->
        val sheetPlaceable = measurables[1].measure(c.copy(minHeight = 0, minWidth = 0))

        val showOffset by deferred.animate({ tween() }) {
            if (it) {
                1f
            } else {
                0f
            }
        }

        val placeable = measurables[0].measure(c.copy(minWidth = 0,
                minHeight = 0,
                maxHeight = c.maxHeight - (72.dp.toPx() * showOffset).toInt()))

        layout(c.maxWidth, c.maxHeight) {
            placeable.placeRelative(0, 0)
            if (state.playerState.drag.currentValue && state.playerState.drag.targetValue) {
                sheetPlaceable.placeRelative(0, c.maxHeight - (sheetPlaceable.height * showOffset).toInt())
            } else {
                sheetPlaceable.placeRelative(0,
                        c.maxHeight - (72.dp.toPx() * showOffset).toInt() + state.playerState.drag.offset.roundToInt())
            }
        }
    }
}

@Composable
fun rememberPlayerSheetScaffoldState(): PlayerSheetScaffoldState {
    val playerState = rememberPlayerState()
    return remember(playerState) {
        PlayerSheetScaffoldState(
                playerState,
                SnackbarHostState(),
        )
    }
}