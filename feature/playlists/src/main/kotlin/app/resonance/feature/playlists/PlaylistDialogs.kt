package app.resonance.feature.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.domain.usecase.MAX_PLAYLIST_NAME_LENGTH
import app.resonance.core.domain.usecase.PlaylistNameResult

@Composable
internal fun PlaylistNameResult?.message(): String? = when (this) {
    PlaylistNameResult.Blank -> stringResource(R.string.playlists_error_blank)
    PlaylistNameResult.TooLong -> stringResource(R.string.playlists_error_too_long, MAX_PLAYLIST_NAME_LENGTH)
    PlaylistNameResult.Duplicate -> stringResource(R.string.playlists_error_duplicate)
    is PlaylistNameResult.Saved, null -> null
}

@Composable
internal fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: PlaylistNameResult?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val focus = remember { FocusRequester() }
    val errorText = error.message()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlists_name_label)) },
                singleLine = true,
                isError = errorText != null,
                supportingText = errorText?.let { { Text(it) } },
                modifier = Modifier
                    .focusRequester(focus)
                    .testTag("playlist_name_field")
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, modifier = Modifier.testTag("playlist_name_confirm")) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlists_cancel)) } }
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/**
 * App-level dialog for adding [mediaIds] to an existing or new playlist. [onFinished] receives a
 * message to show (e.g. in a snackbar) once the songs were added.
 */
@Composable
fun AddToPlaylistDialog(
    mediaIds: List<Long>,
    onDismiss: () -> Unit,
    onFinished: (String) -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    val resources = LocalResources.current

    LaunchedEffect(state.outcome) {
        val outcome = state.outcome ?: return@LaunchedEffect
        val message = if (outcome.added == 0) {
            resources.getString(R.string.playlists_already_there, outcome.playlistName)
        } else {
            resources.getQuantityString(R.plurals.playlists_added, outcome.added, outcome.added, outcome.playlistName)
        }
        viewModel.consumeOutcome()
        onFinished(message)
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.playlists_new),
            confirmLabel = stringResource(R.string.playlists_create),
            initialName = "",
            error = state.nameError,
            onConfirm = { viewModel.createAndAdd(it, mediaIds) },
            onDismiss = {
                viewModel.clearNameError()
                creating = false
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlists_add_title)) },
        text = {
            Column {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { creating = true },
                    leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.playlists_new)) }
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(state.playlists, key = { it.id }) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Button) { viewModel.addTo(playlist, mediaIds) },
                            leadingContent = {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
                            },
                            headlineContent = { Text(playlist.name) },
                            supportingContent = {
                                Text(
                                    pluralStringResource(
                                        app.resonance.core.ui.R.plurals.core_ui_track_count,
                                        playlist.trackCount,
                                        playlist.trackCount
                                    )
                                )
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlists_cancel)) } }
    )
}
