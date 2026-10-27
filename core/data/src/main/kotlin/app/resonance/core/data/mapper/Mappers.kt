package app.resonance.core.data.mapper

import app.resonance.core.database.dao.AlbumRow
import app.resonance.core.database.dao.ArtistRow
import app.resonance.core.database.dao.FolderRow
import app.resonance.core.database.dao.GenreRow
import app.resonance.core.database.dao.PlaylistRow
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.VideoProgressEntity
import app.resonance.core.model.Album
import app.resonance.core.model.Artist
import app.resonance.core.model.Folder
import app.resonance.core.model.Genre
import app.resonance.core.model.MediaFile
import app.resonance.core.model.Playlist
import app.resonance.core.model.VideoProgress

/** Album art is served as a thumbnail of one of the album's tracks (see the artwork fetcher in :core:ui). */
private const val AUDIO_ITEM_URI = "content://media/external/audio/media/"

fun MediaEntity.asExternalModel() = MediaFile(
    id = id,
    kind = kind,
    contentUri = contentUri,
    title = title,
    displayName = displayName,
    artist = artist,
    artistId = artistId,
    album = album,
    albumId = albumId,
    genre = genre,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    mimeType = mimeType,
    folderPath = folderPath,
    dateAddedEpochSec = dateAddedEpochSec,
    trackNumber = trackNumber,
    year = year,
    width = width,
    height = height
)

fun AlbumRow.asExternalModel() = Album(
    id = id,
    title = title,
    artist = artist,
    trackCount = trackCount,
    year = year,
    artworkUri = AUDIO_ITEM_URI + firstTrackId
)

fun ArtistRow.asExternalModel() = Artist(id = id, name = name, trackCount = trackCount, albumCount = albumCount)

fun GenreRow.asExternalModel() = Genre(name = name, trackCount = trackCount)

fun FolderRow.asExternalModel() = Folder(path = path, itemCount = itemCount)

fun PlaylistRow.asExternalModel() = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs
)

fun VideoProgressEntity.asExternalModel() = VideoProgress(
    mediaId = mediaId,
    positionMs = positionMs,
    durationMs = durationMs
)

fun List<MediaEntity>.asExternalModels(): List<MediaFile> = map { it.asExternalModel() }
