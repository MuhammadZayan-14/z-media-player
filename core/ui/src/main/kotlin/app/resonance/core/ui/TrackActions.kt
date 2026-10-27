package app.resonance.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.resonance.core.model.MediaFile

/**
 * Per-track actions offered from list overflow menus. Optional actions are hidden when null,
 * e.g. "Go to album" on the album screen itself.
 */
@Immutable
data class TrackActions(
    val onPlayNext: (MediaFile) -> Unit,
    val onAddToQueue: (MediaFile) -> Unit,
    val onAddToPlaylist: (MediaFile) -> Unit,
    val onToggleFavorite: (MediaFile) -> Unit,
    val onGoToAlbum: ((MediaFile) -> Unit)? = null,
    val onGoToArtist: ((MediaFile) -> Unit)? = null,
    val onRemove: ((MediaFile) -> Unit)? = null,
    val onMoveUp: ((MediaFile) -> Unit)? = null,
    val onMoveDown: ((MediaFile) -> Unit)? = null
)

/** Implemented by screen ViewModels that offer the standard track menu. */
interface TrackCommandHandler {
    fun playNext(track: MediaFile)
    fun addToQueue(track: MediaFile)
    fun toggleFavorite(track: MediaFile)
}

/** Cross-feature destinations a track menu can lead to; provided by the app's navigation host. */
@Immutable
data class LibraryNavigator(
    val onAddToPlaylist: (List<Long>) -> Unit,
    val onOpenAlbum: (Long) -> Unit,
    val onOpenArtist: (Long) -> Unit
)

@Composable
fun rememberTrackActions(
    handler: TrackCommandHandler,
    navigator: LibraryNavigator,
    showGoToAlbum: Boolean = true,
    showGoToArtist: Boolean = true
): TrackActions = remember(handler, navigator, showGoToAlbum, showGoToArtist) {
    TrackActions(
        onPlayNext = handler::playNext,
        onAddToQueue = handler::addToQueue,
        onAddToPlaylist = { navigator.onAddToPlaylist(listOf(it.id)) },
        onToggleFavorite = handler::toggleFavorite,
        onGoToAlbum = if (showGoToAlbum) { media -> navigator.onOpenAlbum(media.albumId) } else null,
        onGoToArtist = if (showGoToArtist) { media -> navigator.onOpenArtist(media.artistId) } else null
    )
}

@Composable
fun TrackMenuButton(
    media: MediaFile,
    isFavorite: Boolean,
    actions: TrackActions
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.core_ui_more_options, media.title)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            @Composable
            fun item(label: String, icon: ImageVector, action: (MediaFile) -> Unit) {
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        action(media)
                    }
                )
            }
            item(
                stringResource(R.string.core_ui_play_next),
                Icons.AutoMirrored.Rounded.PlaylistPlay,
                actions.onPlayNext
            )
            item(
                stringResource(R.string.core_ui_add_to_queue),
                Icons.AutoMirrored.Rounded.QueueMusic,
                actions.onAddToQueue
            )
            item(
                stringResource(R.string.core_ui_add_to_playlist),
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                actions.onAddToPlaylist
            )
            if (isFavorite) {
                item(stringResource(R.string.core_ui_remove_favorite), Icons.Rounded.Favorite, actions.onToggleFavorite)
            } else {
                item(
                    stringResource(R.string.core_ui_add_favorite),
                    Icons.Rounded.FavoriteBorder,
                    actions.onToggleFavorite
                )
            }
            actions.onGoToAlbum?.let { item(stringResource(R.string.core_ui_go_to_album), Icons.Rounded.Album, it) }
            actions.onGoToArtist?.let { item(stringResource(R.string.core_ui_go_to_artist), Icons.Rounded.Person, it) }
            actions.onMoveUp?.let { item(stringResource(R.string.core_ui_move_up), Icons.Rounded.ArrowUpward, it) }
            actions.onMoveDown?.let {
                item(stringResource(R.string.core_ui_move_down), Icons.Rounded.ArrowDownward, it)
            }
            actions.onRemove?.let {
                item(stringResource(R.string.core_ui_remove_from_playlist), Icons.Rounded.Delete, it)
            }
        }
    }
}
