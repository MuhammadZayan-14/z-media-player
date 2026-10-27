package app.resonance.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.resonance.core.common.formatDuration
import app.resonance.core.common.formatFileSize
import app.resonance.core.designsystem.component.Artwork
import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.VideoProgress

@Composable
fun MediaFile.artistLabel(): String = artist ?: stringResource(R.string.core_ui_unknown_artist)

@Composable
fun MediaFile.albumLabel(): String = album ?: stringResource(R.string.core_ui_unknown_album)

/** A song row: artwork, title, "artist · album", duration and the track menu. */
@Composable
fun TrackRow(
    media: MediaFile,
    isCurrent: Boolean,
    isFavorite: Boolean,
    actions: TrackActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false
) {
    val nowPlaying = stringResource(R.string.core_ui_now_playing)
    ListItem(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (isCurrent) stateDescription = nowPlaying },
        colors = if (isCurrent) {
            ListItemDefaults.colors(headlineColor = MaterialTheme.colorScheme.primary)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            if (isCurrent) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp).padding(10.dp)
                )
            } else {
                Artwork(uri = media.artworkUri, modifier = Modifier.size(48.dp))
            }
        },
        headlineContent = { Text(media.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val subtitle = buildString {
                append(media.artistLabel())
                media.album?.let { append(" · ").append(it) }
                if (showDetails) {
                    append(
                        " · "
                    ).append(media.format).append(" · ").append(formatFileSize(media.sizeBytes))
                }
            }
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(media.durationMs), style = MaterialTheme.typography.labelMedium)
                TrackMenuButton(media, isFavorite, actions)
            }
        }
    )
}

@Composable
fun AlbumRow(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = {
            Artwork(uri = album.artworkUri, fallbackIcon = Icons.Rounded.Album, modifier = Modifier.size(56.dp))
        },
        headlineContent = {
            Text(
                album.title ?: stringResource(R.string.core_ui_unknown_album),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            val tracks = pluralStringResource(R.plurals.core_ui_track_count, album.trackCount, album.trackCount)
            Text(
                listOfNotNull(
                    album.artist ?: stringResource(R.string.core_ui_unknown_artist),
                    album.year?.toString(),
                    tracks
                )
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = {
            Artwork(
                uri = null,
                shape = CircleShape,
                fallbackIcon = Icons.Rounded.Person,
                modifier = Modifier.size(56.dp)
            )
        },
        headlineContent = {
            Text(
                artist.name ?: stringResource(R.string.core_ui_unknown_artist),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                pluralStringResource(R.plurals.core_ui_album_count, artist.albumCount, artist.albumCount) + " · " +
                    pluralStringResource(R.plurals.core_ui_track_count, artist.trackCount, artist.trackCount)
            )
        }
    )
}

@Composable
fun GenreRow(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
        headlineContent = { Text(genre.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.core_ui_track_count, genre.trackCount, genre.trackCount))
        }
    )
}

@Composable
fun FolderRow(folder: Folder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                folder.path + " · " +
                    pluralStringResource(R.plurals.core_ui_track_count, folder.itemCount, folder.itemCount),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

/** Square card for horizontal rails (home dashboard). */
@Composable
fun MediaCard(
    media: MediaFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: VideoProgress? = null
) {
    val isVideo = media.kind == MediaKind.VIDEO
    Column(
        modifier = modifier
            .width(if (isVideo) 200.dp else 140.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
    ) {
        Artwork(
            uri = media.artworkUri,
            fallbackIcon = if (isVideo) Icons.Rounded.Theaters else Icons.Rounded.Album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isVideo) 16f / 9f else 1f)
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        Text(
            media.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            if (isVideo) formatDuration(media.durationMs) else media.artistLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
