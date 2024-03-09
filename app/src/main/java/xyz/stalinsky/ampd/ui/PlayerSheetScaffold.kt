package xyz.stalinsky.ampd.ui

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PlayerSheetScaffoldState @OptIn(ExperimentalFoundationApi::class) constructor(
    val playerState: PlayerState,
    val snackbarHostState: SnackbarHostState,
) {
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetScaffold(sheetContent: @Composable () -> Unit, modifier: Modifier = Modifier, topBar: @Composable () -> Unit, state: PlayerSheetScaffoldState, content: @Composable () -> Unit) {
    val transition = updateTransition(state.playerState.show)
    val offset by transition.animateDp({ tween() }) {
        if (it)
            72.dp
        else
            0.dp
    }

    Scaffold {
        Layout({
            Box {
                topBar()
            }
            Box(Modifier.fillMaxSize().padding(it)) {
                content()
            }
            Box {
                sheetContent()
            }
        }, Modifier) { measurables, constrains ->
            val topBar = measurables[0]
            val content = measurables[1]
            val sheet = measurables[2]

            val topBarPlaceable = topBar.measure(constrains)
            val placeable = content.measure(
                constrains.copy(
                    maxHeight = constrains.maxHeight - topBarPlaceable.height - offset.toPx().roundToInt()
                )
            )
            val sheetPlaceable = sheet.measure(constrains.copy(maxHeight = 372.dp.toPx().roundToInt()))

            layout(constrains.maxWidth, constrains.maxHeight) {
                topBarPlaceable.placeRelative(0, 0)
                placeable.placeRelative(0, topBarPlaceable.height)
                sheetPlaceable.placeRelative(0, topBarPlaceable.height + placeable.height + state.playerState.drag.offset.roundToInt())
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