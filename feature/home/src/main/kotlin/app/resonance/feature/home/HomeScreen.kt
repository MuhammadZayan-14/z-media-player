package app.resonance.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.designsystem.component.SectionHeader
import app.resonance.core.model.MediaFile
import app.resonance.core.model.QueueItem
import app.resonance.core.model.VideoProgress
import app.resonance.core.ui.MediaCard
import app.resonance.core.ui.MediaPermissionRequest
import app.resonance.core.ui.R as CoreUiR
import app.resonance.core.ui.rememberMediaPermissionState

@Composable
internal fun HomeRoute(
    onPlayVideo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberMediaPermissionState(onAccessChanged = { viewModel.onAccessChanged() })
    HomeScreen(
        state = state,
        hasAccess = permission.access.canReadAnything,
        permissionContent = { MediaPermissionRequest(permission) },
        onPlay = viewModel::play,
        onPlayVideo = onPlayVideo,
        onResume = {
            viewModel.resume()
            onOpenNowPlaying()
        },
        onShuffleLibrary = viewModel::shuffleLibrary,
        onSearch = onSearch
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    hasAccess: Boolean,
    permissionContent: @Composable () -> Unit,
    onPlay: (List<MediaFile>, Int) -> Unit,
    onPlayVideo: (Long) -> Unit,
    onResume: () -> Unit,
    onShuffleLibrary: () -> Unit,
    onSearch: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Rounded.Search, stringResource(CoreUiR.string.core_ui_search))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            when {
                !hasAccess -> permissionContent()
                state is HomeUiState.Loading -> LoadingState()
                state is HomeUiState.Ready -> HomeContent(state, onPlay, onPlayVideo, onResume, onShuffleLibrary)
            }
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onPlay: (List<MediaFile>, Int) -> Unit,
    onPlayVideo: (Long) -> Unit,
    onResume: () -> Unit,
    onShuffleLibrary: () -> Unit
) {
    val feed = state.feed
    if (feed.isEmpty && state.nowPlaying == null) {
        MessageState(
            icon = Icons.Rounded.LibraryMusic,
            title = stringResource(R.string.home_empty_title),
            message = stringResource(R.string.home_empty_message)
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.testTag("home_feed")) {
        state.nowPlaying?.let { item ->
            item(key = "continue") { ContinueCard(item, state.isPlaying, onResume) }
        }
        item(key = "shuffle") {
            FilledTonalButton(
                onClick = onShuffleLibrary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Text(stringResource(R.string.home_shuffle_library), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (feed.continueWatching.isNotEmpty()) {
            item(key = "watching") {
                SectionHeader(stringResource(R.string.home_continue_watching))
                VideoRail(feed.continueWatching, onPlayVideo)
            }
        }
        rail("recent", R.string.home_recently_played, feed.recentlyPlayed, onPlay)
        rail("added", R.string.home_recently_added, feed.recentlyAdded, onPlay)
        rail("most", R.string.home_most_played, feed.mostPlayed, onPlay)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rail(
    key: String,
    title: Int,
    items: List<MediaFile>,
    onPlay: (List<MediaFile>, Int) -> Unit
) {
    if (items.isEmpty()) return
    item(key = key) {
        SectionHeader(stringResource(title))
        LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
            itemsIndexed(items, key = { _, media -> media.id }) { index, media ->
                MediaCard(media, onClick = { onPlay(items, index) })
            }
        }
    }
}

@Composable
private fun VideoRail(items: List<Pair<MediaFile, VideoProgress>>, onPlayVideo: (Long) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
        itemsIndexed(items, key = { _, pair -> pair.first.id }) { _, (video, progress) ->
            MediaCard(video, onClick = { onPlayVideo(video.id) }, progress = progress)
        }
    }
}

@Composable
private fun ContinueCard(item: QueueItem, isPlaying: Boolean, onResume: () -> Unit) {
    ElevatedCard(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("continue_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Artwork(uri = item.artworkUri, modifier = Modifier.size(64.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    stringResource(R.string.home_continue),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                item.artist?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FilledIconButton(onClick = onResume) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_resume)
                )
            }
        }
    }
}
