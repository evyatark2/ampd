package xyz.stalinsky.ampd.ui

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateIntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PlayerSheetScaffoldState(
    val playerState: PlayerState,
    val snackbarHostState: SnackbarHostState,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerSheetScaffold(state: PlayerSheetScaffoldState, modifier: Modifier = Modifier, sheetContent: @Composable () -> Unit, topBarContent: @Composable () -> Unit, content: @Composable () -> Unit) {
    val showTransition = updateTransition(state.playerState.show, label = "")
    val showOffset by showTransition.animateDp({ tween() }, label = "") {
        if (it)
            72.dp
        else
            0.dp
    }

    Surface(Modifier) {
        SubcomposeLayout(Modifier) { c ->
            val placeable = subcompose(1) {
                Column(Modifier.fillMaxSize()) {
                    topBarContent()
                    content()
                }
            }.first().measure(Constraints(0, c.maxWidth, 0, c.maxHeight - showOffset.toPx().toInt()))

            val sheetPlaceable = subcompose(2) {
                val transition = updateTransition(state.playerState.expand, "")
                val offset by transition.animateIntOffset({ tween() }, "") {
                    if (it)
                        IntOffset(0, 0)
                    else
                        IntOffset(
                            0,
                            placeable.height + state.playerState.drag.offset.roundToInt()
                        )
                }

                Box(Modifier.offset { offset }) {
                    sheetContent()
                }
            }.first().measure(c)

            layout(c.maxWidth, c.maxHeight) {
                placeable.placeRelative(0, 0)
                sheetPlaceable.placeRelative(0, 0)
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