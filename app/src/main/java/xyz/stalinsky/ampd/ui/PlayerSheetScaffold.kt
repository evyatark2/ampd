package xyz.stalinsky.ampd.ui

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateIntOffset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PlayerSheetScaffoldState @OptIn(ExperimentalFoundationApi::class) constructor(
    val playerState: PlayerState,
    val snackbarHostState: SnackbarHostState,
) {
}

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

    Scaffold { padding ->
        SubcomposeLayout(Modifier) { c ->
            val measurables = subcompose(1) {
                Box {
                    topBarContent()
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    content()
                }
            }

            val topBar = measurables[0]
            val content = measurables[1]
            //val sheet = measurables[2]

            val topBarPlaceable = topBar.measure(c)
            val placeable = content.measure(
                c.copy(
                    maxHeight = c.maxHeight - topBarPlaceable.height - showOffset.toPx()
                        .roundToInt()
                )
            )

            val sheet = subcompose(2) {
                val transition = updateTransition(state.playerState.expand, "")
                val offset by transition.animateIntOffset({ tween() }, "") {
                    if (it)
                        IntOffset(0, 0)
                    else
                        IntOffset(0, topBarPlaceable.height + placeable.height + state.playerState.drag.offset.roundToInt())
                }

                Box(Modifier.offset { offset }) {
                    sheetContent()
                }
            }.first()
            val sheetPlaceable = sheet.measure(c)

            layout(c.maxWidth, c.maxHeight) {
                topBarPlaceable.placeRelative(0, 0)
                placeable.placeRelative(0, topBarPlaceable.height)
                sheetPlaceable.placeRelative( 0, 0)
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