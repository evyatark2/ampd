package xyz.stalinsky.ampd.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.stalinsky.ampd.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Player(state: SheetValue, onPartialExpand: () -> Unit, onExpand: () -> Unit, onHide: () -> Unit, viewModel: PlayerViewModel) {
    val isPlaying by viewModel.playing.collectAsState()
    val currentItem by viewModel.currentItem.collectAsState()
    val queue by viewModel.queue.collectAsState()

    val scope = rememberCoroutineScope()

    when (state) {
        SheetValue.Hidden -> {
            if (queue != null) {
                onPartialExpand()
            }
        }

        SheetValue.Expanded -> {
            if (queue == null) {
                onHide()
            }
        }

        SheetValue.PartiallyExpanded -> {
            if (queue == null) {
                onHide()
            } else {
                val currentItem = currentItem
                if (currentItem != null) {
                    ListItem(headlineContent = {
                        Text(currentItem.mediaMetadata.title?.toString() ?: "")
                    }, trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                if (isPlaying) {
                                    viewModel.pause()
                                } else {
                                    viewModel.play()
                                }
                            }
                        }) {
                            if (isPlaying) {
                                Icons.Default.Close
                            } else {
                                Icons.Default.PlayArrow

                            }
                        }
                    })
                }
            }
        }
    }
}