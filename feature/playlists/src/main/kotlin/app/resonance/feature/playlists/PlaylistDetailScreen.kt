package app.resonance.feature.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.ui.CollectionHeader
import app.resonance.core.ui.LibraryNavigator
import app.resonance.core.ui.R as CoreUiR
import app.resonance.core.ui.TrackRow
import app.resonance.core.ui.rememberTrackActions

@Composable
internal fun PlaylistDetailRoute(
    navigator: LibraryNavigator,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state is PlaylistDetailUiState.Gone) onBack()
    }
    val base = rememberTrackActions(viewModel, navigator)
    val ready = state as? PlaylistDetailUiState.Ready
    val actions = remember(base, ready?.isUserPlaylist) {
        if (ready?.isUserPlaylist == true) {
            base.copy(
                onRemove = viewModel::remove,
                onMoveUp = { viewModel.move(it, -1) },
                onMoveDown = { viewModel.move(it, +1) }
            )
        } else {
            base
        }
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    PlaylistDetailScreen(
        state = state,
        onBack = onBack,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        onRename = viewModel::openRename,
        onDelete = { confirmDelete = true },
        trackRow = { index, track ->
            val s = state as PlaylistDetailUiState.Ready
            TrackRow(
                media = track,
                isCurrent = track.id == s.currentMediaId,
                isFavorite = track.id in s.favoriteIds,
                actions = actions,
                onClick = { viewModel.play(index) }
            )
        }
    )

    ready?.editor?.let { editor ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlists_rename),
            confirmLabel = stringResource(R.string.playlists_save),
            initialName = ready.name.orEmpty(),
            error = editor.error,
            onConfirm = viewModel::rename,
            onDismiss = viewModel::dismissRename
        )
    }
    if (confirmDelete && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.playlists_delete_title, ready.name.orEmpty())) },
            text = { Text(stringResource(R.string.playlists_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.playlists_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.playlists_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    trackRow: @Composable (Int, app.resonance.core.model.MediaFile) -> Unit
) {
    val ready = state as? PlaylistDetailUiState.Ready
    val title = ready?.let { it.name ?: it.smart?.title() }.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.playlists_back))
                    }
                },
                actions = {
                    if (ready?.isUserPlaylist == true) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Rounded.MoreVert, stringResource(R.string.playlists_more))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlists_rename)) },
                                    onClick = {
                                        menu = false
                                        onRename()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.playlists_delete)) },
                                    onClick = {
                                        menu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            PlaylistDetailUiState.Loading, PlaylistDetailUiState.Gone -> LoadingState(Modifier.padding(padding))

            is PlaylistDetailUiState.Ready -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.padding(padding)
            ) {
                item {
                    CollectionHeader(
                        title = title,
                        subtitle = pluralStringResource(
                            CoreUiR.plurals.core_ui_track_count,
                            state.tracks.size,
                            state.tracks.size
                        ),
                        onPlay = { onPlay(0) },
                        onShuffle = onShuffle,
                        artworkUri = state.tracks.firstOrNull()?.artworkUri,
                        fallbackIcon = state.smart?.icon() ?: Icons.AutoMirrored.Rounded.PlaylistPlay,
                        enabled = state.tracks.isNotEmpty()
                    )
                }
                if (state.tracks.isEmpty()) {
                    item {
                        MessageState(
                            icon = Icons.Rounded.MusicNote,
                            title = stringResource(R.string.playlists_detail_empty_title),
                            message = stringResource(R.string.playlists_detail_empty_message)
                        )
                    }
                }
                itemsIndexed(state.tracks, key = { _, track -> track.id }) { index, track -> trackRow(index, track) }
            }
        }
    }
}
