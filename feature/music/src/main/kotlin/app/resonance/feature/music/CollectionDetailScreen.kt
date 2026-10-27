package app.resonance.feature.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.designsystem.component.SectionHeader
import app.resonance.core.model.Album
import app.resonance.core.ui.CollectionHeader
import app.resonance.core.ui.LibraryNavigator
import app.resonance.core.ui.R as CoreUiR
import app.resonance.core.ui.TrackActions
import app.resonance.core.ui.TrackRow
import app.resonance.core.ui.rememberTrackActions
import app.resonance.feature.music.navigation.CollectionKind

@Composable
internal fun CollectionDetailRoute(
    navigator: LibraryNavigator,
    onBack: () -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actions = rememberTrackActions(
        handler = viewModel,
        navigator = navigator,
        showGoToAlbum = viewModel.kind != CollectionKind.ALBUM,
        showGoToArtist = viewModel.kind != CollectionKind.ARTIST
    )
    CollectionDetailScreen(
        state = state,
        actions = actions,
        onBack = onBack,
        onPlay = viewModel::play,
        onShuffle = viewModel::shuffle,
        onOpenAlbum = navigator.onOpenAlbum
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionDetailScreen(
    state: CollectionUiState,
    actions: TrackActions,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val ready = state as? CollectionUiState.Ready
    val title = ready?.header?.let { it.title ?: unknownTitle(it.kind) }.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreUiR.string.core_ui_back))
                    }
                }
            )
        }
    ) { padding ->
        if (ready == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (ready.tracks.isEmpty()) {
            MessageState(
                icon = Icons.Rounded.MusicOff,
                title = stringResource(R.string.music_collection_not_found),
                message = stringResource(R.string.music_collection_not_found_message),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.padding(padding)) {
            item(key = "header") {
                val count =
                    pluralStringResource(CoreUiR.plurals.core_ui_track_count, ready.tracks.size, ready.tracks.size)
                CollectionHeader(
                    title = title,
                    subtitle = listOfNotNull(ready.header.subtitle, count).joinToString(" · "),
                    onPlay = { onPlay(0) },
                    onShuffle = onShuffle,
                    artworkUri = ready.header.artworkUri,
                    fallbackIcon = ready.header.kind.icon()
                )
            }
            if (ready.albums.isNotEmpty()) {
                item(key = "albums") {
                    SectionHeader(stringResource(R.string.music_section_albums))
                    AlbumRail(ready.albums, onOpenAlbum)
                    SectionHeader(stringResource(R.string.music_section_songs))
                }
            }
            itemsIndexed(ready.tracks, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    media = track,
                    isCurrent = track.id == ready.currentMediaId,
                    isFavorite = track.id in ready.favoriteIds,
                    actions = actions,
                    onClick = { onPlay(index) }
                )
            }
        }
    }
}

@Composable
private fun AlbumRail(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .width(136.dp)
                    .clickable(role = Role.Button) { onOpenAlbum(album.id) }
                    .padding(8.dp)
            ) {
                Artwork(
                    album.artworkUri,
                    fallbackIcon = Icons.Rounded.Album,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                Text(
                    album.title ?: stringResource(CoreUiR.string.core_ui_unknown_album),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                album.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun unknownTitle(kind: CollectionKind): String = stringResource(
    if (kind == CollectionKind.ARTIST) CoreUiR.string.core_ui_unknown_artist else CoreUiR.string.core_ui_unknown_album
)

private fun CollectionKind.icon() = when (this) {
    CollectionKind.ALBUM -> Icons.Rounded.Album
    CollectionKind.ARTIST -> Icons.Rounded.Person
    CollectionKind.GENRE -> Icons.Rounded.GraphicEq
    CollectionKind.FOLDER -> Icons.Rounded.Folder
}
