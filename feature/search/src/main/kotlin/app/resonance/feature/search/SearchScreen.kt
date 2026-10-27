package app.resonance.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.common.formatDuration
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.designsystem.component.SectionHeader
import app.resonance.core.model.SearchFilter
import app.resonance.core.ui.AlbumRow
import app.resonance.core.ui.ArtistRow
import app.resonance.core.ui.LibraryNavigator
import app.resonance.core.ui.R as CoreUiR
import app.resonance.core.ui.TrackActions
import app.resonance.core.ui.TrackRow
import app.resonance.core.ui.rememberTrackActions

@Composable
internal fun SearchRoute(
    navigator: LibraryNavigator,
    onPlayVideo: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        actions = rememberTrackActions(viewModel, navigator),
        onQueryChange = viewModel::onQueryChange,
        onFilterChange = viewModel::onFilterChange,
        onPlaySong = viewModel::playSong,
        onOpenAlbum = navigator.onOpenAlbum,
        onOpenArtist = navigator.onOpenArtist,
        onPlayVideo = onPlayVideo,
        onBack = onBack
    )
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    actions: TrackActions,
    onQueryChange: (String) -> Unit,
    onFilterChange: (SearchFilter) -> Unit,
    onPlaySong: (Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlayVideo: (Long) -> Unit,
    onBack: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreUiR.string.core_ui_back))
                    }
                    TextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.search_clear))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focus)
                            .testTag("search_field")
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SearchFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(stringResource(filter.label())) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val results = state.results
        when {
            state.query.isBlank() -> MessageState(
                icon = Icons.Rounded.Search,
                title = stringResource(R.string.search_prompt_title),
                message = stringResource(R.string.search_prompt_message),
                modifier = Modifier.padding(padding)
            )

            state.hasSearched && results.isEmpty -> MessageState(
                icon = Icons.Rounded.SearchOff,
                title = stringResource(R.string.search_no_results_title),
                message = stringResource(R.string.search_no_results_message, state.query.trim()),
                modifier = Modifier.padding(padding)
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier
                    .padding(padding)
                    .testTag("search_results")
            ) {
                if (results.songs.isNotEmpty()) {
                    item(key = "songs_header") { SectionHeader(stringResource(R.string.search_filter_songs)) }
                    itemsIndexed(results.songs, key = { _, song -> "s${song.id}" }) { index, song ->
                        TrackRow(
                            media = song,
                            isCurrent = song.id == state.currentMediaId,
                            isFavorite = song.id in state.favoriteIds,
                            actions = actions,
                            onClick = { onPlaySong(index) }
                        )
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item(key = "albums_header") { SectionHeader(stringResource(R.string.search_filter_albums)) }
                    items(results.albums, key = {
                        "a${it.id}"
                    }) { album -> AlbumRow(album, onClick = { onOpenAlbum(album.id) }) }
                }
                if (results.artists.isNotEmpty()) {
                    item(key = "artists_header") { SectionHeader(stringResource(R.string.search_filter_artists)) }
                    items(results.artists, key = {
                        "r${it.id}"
                    }) { artist -> ArtistRow(artist, onClick = { onOpenArtist(artist.id) }) }
                }
                if (results.videos.isNotEmpty()) {
                    item(key = "videos_header") { SectionHeader(stringResource(R.string.search_filter_videos)) }
                    items(results.videos, key = { "v${it.id}" }) { video ->
                        ListItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onPlayVideo(video.id) },
                            leadingContent = {
                                Artwork(
                                    video.artworkUri,
                                    fallbackIcon = Icons.Rounded.Theaters,
                                    modifier = Modifier.size(width = 80.dp, height = 45.dp)
                                )
                            },
                            headlineContent = { Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(formatDuration(video.durationMs)) }
                        )
                    }
                }
            }
        }
    }
}

private fun SearchFilter.label(): Int = when (this) {
    SearchFilter.ALL -> R.string.search_filter_all
    SearchFilter.SONGS -> R.string.search_filter_songs
    SearchFilter.ALBUMS -> R.string.search_filter_albums
    SearchFilter.ARTISTS -> R.string.search_filter_artists
    SearchFilter.VIDEOS -> R.string.search_filter_videos
}
