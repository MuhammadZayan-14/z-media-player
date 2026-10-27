package app.resonance.feature.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.SectionHeader
import app.resonance.core.model.Playlist
import app.resonance.core.model.SmartCollection
import app.resonance.core.ui.R as CoreUiR

@Composable
internal fun PlaylistsRoute(
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreen(
        state = state,
        onOpenPlaylist = onOpenPlaylist,
        onOpenSmart = onOpenSmart,
        onCreate = viewModel::openCreate
    )
    state.editor?.let { editor ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlists_new),
            confirmLabel = stringResource(R.string.playlists_create),
            initialName = "",
            error = editor.error,
            onConfirm = viewModel::create,
            onDismiss = viewModel::dismissCreate
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistsScreen(
    state: PlaylistsUiState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    onCreate: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.playlists_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.playlists_new)) },
                modifier = Modifier.testTag("create_playlist")
            )
        }
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), modifier = Modifier.padding(padding)) {
            item { SectionHeader(stringResource(R.string.playlists_collections)) }
            items(SmartCollection.entries) { collection ->
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { onOpenSmart(collection) },
                    leadingContent = {
                        Icon(collection.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = { Text(collection.title()) }
                )
            }
            item { SectionHeader(stringResource(R.string.playlists_yours)) }
            if (!state.isLoading && state.playlists.isEmpty()) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.playlists_empty_title)) },
                        supportingContent = { Text(stringResource(R.string.playlists_empty_message)) }
                    )
                }
            }
            items(state.playlists, key = { it.id }) { playlist ->
                PlaylistRow(playlist, onClick = { onOpenPlaylist(playlist.id) })
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null) },
        headlineContent = { Text(playlist.name) },
        supportingContent = {
            Text(pluralStringResource(CoreUiR.plurals.core_ui_track_count, playlist.trackCount, playlist.trackCount))
        }
    )
}

@Composable
internal fun SmartCollection.title(): String = stringResource(
    when (this) {
        SmartCollection.FAVORITES -> R.string.playlists_favorites
        SmartCollection.RECENTLY_PLAYED -> R.string.playlists_recently_played
        SmartCollection.MOST_PLAYED -> R.string.playlists_most_played
        SmartCollection.RECENTLY_ADDED -> R.string.playlists_recently_added
    }
)

internal fun SmartCollection.icon(): ImageVector = when (this) {
    SmartCollection.FAVORITES -> Icons.Rounded.Favorite
    SmartCollection.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartCollection.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
    SmartCollection.RECENTLY_ADDED -> Icons.Rounded.NewReleases
}
