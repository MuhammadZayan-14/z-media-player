package app.resonance.feature.video.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.common.formatDuration
import app.resonance.core.common.formatFileSize
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.model.MediaFile
import app.resonance.core.model.VideoProgress
import app.resonance.core.permissions.AccessLevel
import app.resonance.core.ui.MediaPermissionRequest
import app.resonance.core.ui.PartialAccessBanner
import app.resonance.core.ui.rememberMediaPermissionState
import app.resonance.core.ui.rememberOpenMediaDocument
import app.resonance.feature.video.R
import app.resonance.feature.video.VideoPlayerActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoLibraryRoute(
    onPlayVideo: (Long) -> Unit,
    viewModel: VideoLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberMediaPermissionState(onAccessChanged = { viewModel.onAccessChanged() })
    val openFile = rememberOpenMediaDocument(arrayOf("video/*")) { uri, _ ->
        context.startActivity(VideoPlayerActivity.intent(context, uri))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.video_title)) },
                actions = {
                    IconButton(onClick = openFile) {
                        Icon(Icons.Rounded.FileOpen, stringResource(R.string.video_open_file))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (permission.access.video == AccessLevel.NONE) {
                MediaPermissionRequest(permission)
                return@Column
            }
            PartialAccessBanner(permission)
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    state.isLoading -> LoadingState()

                    state.videos.isEmpty() -> MessageState(
                        icon = Icons.Rounded.Theaters,
                        title = stringResource(R.string.video_empty_title),
                        message = stringResource(R.string.video_empty_message)
                    )

                    else -> VideoGrid(state.videos, state.progress, onPlayVideo)
                }
            }
        }
    }
}

@Composable
private fun VideoGrid(videos: List<MediaFile>, progress: Map<Long, VideoProgress>, onPlay: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 220.dp),
        contentPadding = PaddingValues(8.dp),
        modifier = Modifier.testTag("video_grid")
    ) {
        items(videos, key = { it.id }) { video ->
            VideoTile(video, progress[video.id], onClick = { onPlay(video.id) })
        }
    }
}

@Composable
private fun VideoTile(video: MediaFile, progress: VideoProgress?, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
    ) {
        Box {
            Artwork(
                uri = video.artworkUri,
                fallbackIcon = Icons.Rounded.Theaters,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    formatDuration(video.durationMs),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (progress != null) {
            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
        }
        Text(
            video.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        val details = buildList {
            if (progress != null) add(stringResource(R.string.video_resume_at, formatDuration(progress.positionMs)))
            if (video.width != null && video.height != null) add("${video.width}×${video.height}")
            add(video.format)
            add(formatFileSize(video.sizeBytes))
        }.joinToString(" · ")
        Text(
            details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
