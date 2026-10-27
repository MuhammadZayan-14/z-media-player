package app.resonance.core.model

enum class MediaKind { AUDIO, VIDEO }

/**
 * One playable file indexed from MediaStore.
 *
 * Text metadata MediaStore could not supply ("<unknown>", blanks) is normalised to `null`
 * at the data layer, so the UI decides how to label it.
 */
data class MediaFile(
    val id: Long,
    val kind: MediaKind,
    val contentUri: String,
    val title: String,
    val displayName: String,
    val artist: String?,
    val artistId: Long,
    val album: String?,
    val albumId: Long,
    val genre: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val folderPath: String,
    val dateAddedEpochSec: Long,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val width: Int? = null,
    val height: Int? = null
) {
    /** Short container label shown in lists, e.g. "MP3", "FLAC", "MP4": file extension first, MIME subtype as fallback. */
    val format: String
        get() = displayName.substringAfterLast('.', missingDelimiterValue = "").uppercase().ifEmpty {
            mimeType.substringAfter('/', missingDelimiterValue = "").removePrefix("x-").uppercase()
        }

    /** MediaStore serves embedded album art / video frames as thumbnails of the item itself. */
    val artworkUri: String get() = contentUri

    val folderName: String
        get() = folderPath.trimEnd('/').substringAfterLast('/').ifEmpty { folderPath }
}

data class Album(
    val id: Long,
    val title: String?,
    val artist: String?,
    val trackCount: Int,
    val year: Int?,
    val artworkUri: String
)

data class Artist(
    val id: Long,
    val name: String?,
    val trackCount: Int,
    val albumCount: Int
)

data class Genre(
    val name: String,
    val trackCount: Int
)

data class Folder(
    val path: String,
    val itemCount: Int
) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { path }
}

/** A filter that selects a subset of the audio library. */
sealed interface MediaCollection {
    data class AlbumTracks(val albumId: Long) : MediaCollection
    data class ArtistTracks(val artistId: Long) : MediaCollection
    data class GenreTracks(val genre: String) : MediaCollection
    data class FolderTracks(val path: String) : MediaCollection
}
