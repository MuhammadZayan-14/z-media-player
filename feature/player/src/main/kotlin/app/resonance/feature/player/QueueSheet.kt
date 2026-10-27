package app.resonance.feature.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.model.PlaybackState
import app.resonance.core.ui.R as CoreUiR

/**
 * The live queue in playlist order. Reordering uses explicit move up/down buttons: they work
 * with TalkBack and switch access, which drag handles alone do not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QueueSheet(
    playback: PlaybackState,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.player_queue_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .semantics { heading() }
        )
        if (playback.queue.isEmpty()) {
            Text(stringResource(R.string.player_queue_empty), modifier = Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = playback.currentIndex.coerceAtLeast(0))
        LazyColumn(state = listState) {
            itemsIndexed(playback.queue, key = { index, item -> "$index:${item.mediaId}" }) { index, item ->
                val isCurrent = index == playback.currentIndex
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onSelect(index) },
                    colors = if (isCurrent) {
                        ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary)
                    } else {
                        ListItemDefaults.colors()
                    },
                    leadingContent = {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            if (isCurrent) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = stringResource(CoreUiR.string.core_ui_now_playing),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Artwork(uri = item.artworkUri, modifier = Modifier.size(44.dp))
                            }
                        }
                    },
                    headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = item.artist?.let {
                        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                                Icon(Icons.Rounded.ArrowUpward, stringResource(CoreUiR.string.core_ui_move_up))
                            }
                            IconButton(
                                onClick = { onMove(index, index + 1) },
                                enabled =
                                    index < playback.queue.lastIndex
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, stringResource(CoreUiR.string.core_ui_move_down))
                            }
                            IconButton(onClick = { onRemove(index) }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.player_remove_from_queue, item.title))
                            }
                        }
                    }
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp))
    }
}
