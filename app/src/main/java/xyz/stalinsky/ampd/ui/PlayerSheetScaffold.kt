package xyz.stalinsky.ampd.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.InternalAnimationApi
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.createDeferredAnimation
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class PlayerSheetScaffoldState(
        val playerState: PlayerState,
        val snackbarHostState: SnackbarHostState,
)

@OptIn(ExperimentalFoundationApi::class, InternalAnimationApi::class)
@Composable
fun PlayerSheetScaffold(
        modifier: Modifier = Modifier,
        sheetVisible: Boolean,
        sheetContent: @Composable (PaddingValues) -> Unit,
        topBarContent: @Composable () -> Unit,
        content: @Composable (PaddingValues) -> Unit) {
    Scaffold(modifier, topBarContent) {
        Box(Modifier.fillMaxSize()) {
            content(object : PaddingValues {
                override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
                        it.calculateLeftPadding(layoutDirection)

                override fun calculateTopPadding() = it.calculateTopPadding()

                override fun calculateRightPadding(layoutDirection: LayoutDirection) =
                        it.calculateRightPadding(layoutDirection)

                override fun calculateBottomPadding() = if (sheetVisible) {
                    it.calculateBottomPadding() + 72.dp
                } else {
                    it.calculateBottomPadding()
                }

            })
            AnimatedVisibility(sheetVisible, Modifier.align(Alignment.BottomCenter), slideInVertically { it },
                    slideOutVertically { it }) {
                sheetContent(it)
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