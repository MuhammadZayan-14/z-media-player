package app.resonance.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import app.resonance.core.model.MediaKind

/**
 * Local index of a MediaStore row. MediaStore ids are unique across audio and video because
 * both live in the same `files` table, so the id doubles as our primary key (and FTS rowid).
 */
@Entity(
    tableName = "media",
    indices = [
        Index("kind"),
        Index("album_id"),
        Index("artist_id"),
        Index("genre"),
        Index("folder_path"),
        Index("date_added"),
        Index("duplicate_key")
    ]
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val kind: MediaKind,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    val title: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val artist: String?,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    val album: String?,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val genre: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "folder_path") val folderPath: String,
    @ColumnInfo(name = "date_added") val dateAddedEpochSec: Long,
    @ColumnInfo(name = "date_modified") val dateModifiedEpochSec: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    val year: Int?,
    val width: Int?,
    val height: Int?,
    /** MediaStore's IS_MUSIC flag; false for ringtones, recordings, notification sounds. */
    @ColumnInfo(name = "is_music") val isMusic: Boolean,
    /** Same value for copies of the same recording; see [duplicateKeyOf]. */
    @ColumnInfo(name = "duplicate_key") val duplicateKey: String
)

/** Full-text index over the searchable columns; Room keeps it in sync with [MediaEntity] via triggers. */
@Fts4(contentEntity = MediaEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "media_fts")
data class MediaFtsEntity(
    val title: String,
    val artist: String?,
    val album: String?
)

/**
 * Two files are treated as duplicates when title, artist, album and duration (to the second)
 * all match, case-insensitively. The lowest MediaStore id of each group is the one kept visible.
 */
fun duplicateKeyOf(kind: MediaKind, title: String, artist: String?, album: String?, durationMs: Long): String =
    listOf(
        kind.name,
        title.trim().lowercase(),
        artist?.trim()?.lowercase().orEmpty(),
        album?.trim()?.lowercase().orEmpty(),
        (
            durationMs /
                1000
            ).toString()
    )
        .joinToString("\u001F")
