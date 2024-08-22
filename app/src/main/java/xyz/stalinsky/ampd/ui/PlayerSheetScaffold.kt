package xyz.stalinsky.ampd.ui

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetScaffold(
        state: PlayerSheetScaffoldState,
        modifier: Modifier = Modifier,
        sheetVisible: Boolean,
        sheetContent: @Composable () -> Unit,
        topBarContent: @Composable () -> Unit,
        content: @Composable () -> Unit) {
    val showTransition = updateTransition(sheetVisible, label = "")
    val showOffset by showTransition.animateFloat({ tween() }, label = "") {
        if (it) {
            1f
        } else {
            0f
        }
    }

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
        val placeable = measurables[0].measure(c.copy(minWidth = 0, minHeight = 0, maxHeight = c.maxHeight - (72.dp.toPx() * showOffset).toInt()))

        layout(c.maxWidth, c.maxHeight) {
            placeable.placeRelative(0, 0)
            if (state.playerState.drag.currentValue && state.playerState.drag.targetValue) {
                sheetPlaceable.placeRelative(0, c.maxHeight - (sheetPlaceable.height * showOffset).toInt())
            } else {
                sheetPlaceable.placeRelative(0, c.maxHeight - (72.dp.toPx() * showOffset).toInt() + state.playerState.drag.offset.roundToInt())
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