package app.resonance.core.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.resonance.core.database.entity.MediaEntity
import app.resonance.core.database.entity.duplicateKeyOf
import app.resonance.core.model.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Narrow view of MediaStore used by the library sync. An interface so the sync algorithm can be
 * tested against an in-memory fake; the real implementation does blocking I/O and must be called
 * off the main thread.
 */
interface MediaStoreSource {
    /** Changes when MediaStore's database is rebuilt; generations from another version are meaningless. */
    fun version(): String

    fun volumes(): Set<String>

    /** Null below Android 11, where MediaStore has no generation counter. */
    fun generation(volume: String): Long?

    /** Rows of [kind] on [volume], optionally only those modified after [sinceGeneration]. */
    fun query(volume: String, kind: MediaKind, sinceGeneration: Long?): List<MediaEntity>

    /** All ids of [kind] currently on [volume]; a cheap query used to detect deletions. */
    fun ids(volume: String, kind: MediaKind): Set<Long>
}

class AndroidMediaStoreSource @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaStoreSource {
    private val resolver: ContentResolver get() = context.contentResolver

    override fun version(): String = MediaStore.getVersion(context)

    override fun volumes(): Set<String> = MediaStore.getExternalVolumeNames(context)

    override fun generation(volume: String): Long? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.getGeneration(context, volume) else null

    override fun ids(volume: String, kind: MediaKind): Set<Long> {
        val ids = HashSet<Long>()
        resolver.query(collectionUri(volume, kind), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (c.moveToNext()) ids += c.getLong(idCol)
        }
        return ids
    }

    override fun query(volume: String, kind: MediaKind, sinceGeneration: Long?): List<MediaEntity> {
        val collection = collectionUri(volume, kind)
        val (selection, args) = if (sinceGeneration != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?" to arrayOf(sinceGeneration.toString())
        } else {
            null to null
        }
        val projection = if (kind == MediaKind.AUDIO) AUDIO_PROJECTION else VIDEO_PROJECTION
        val rows = ArrayList<MediaEntity>()
        resolver.query(collection, projection, selection, args, null)?.use { cursor ->
            val reader = RowReader(cursor, kind)
            while (cursor.moveToNext()) {
                rows += reader.read(collection)
            }
        }
        return rows
    }

    private fun collectionUri(volume: String, kind: MediaKind): Uri = when (kind) {
        MediaKind.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
        MediaKind.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
    }

    /** Resolves column indexes once per cursor rather than once per row. */
    private class RowReader(private val c: Cursor, private val kind: MediaKind) {
        private val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        private val title = c.getColumnIndex(MediaStore.MediaColumns.TITLE)
        private val displayName = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        private val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
        private val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
        private val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        private val relativePath = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
        private val dateAdded = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        private val dateModified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
        private val artist = c.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST)
        private val artistId = c.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST_ID)
        private val album = c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM)
        private val albumId = c.getColumnIndex(MediaStore.Audio.AudioColumns.ALBUM_ID)
        private val genre = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            c.getColumnIndex(MediaStore.Audio.AudioColumns.GENRE)
        } else {
            -1
        }
        private val track = c.getColumnIndex(MediaStore.Audio.AudioColumns.TRACK)
        private val year = c.getColumnIndex(MediaStore.Audio.AudioColumns.YEAR)
        private val isMusic = c.getColumnIndex(MediaStore.Audio.AudioColumns.IS_MUSIC)
        private val width = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        private val height = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)

        fun read(collection: Uri): MediaEntity {
            val mediaId = c.getLong(id)
            val name = c.stringOrNull(displayName).orEmpty()
            val cleanTitle = c.stringOrNull(title).cleanMetadata() ?: name.substringBeforeLast('.').ifBlank { name }
            val artistName = c.stringOrNull(artist).cleanMetadata()
            val albumName = c.stringOrNull(album).cleanMetadata()
            val durationMs = c.longOrNull(duration)?.coerceAtLeast(0) ?: 0L
            return MediaEntity(
                id = mediaId,
                kind = kind,
                contentUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                title = cleanTitle,
                displayName = name,
                artist = artistName,
                artistId = c.longOrNull(artistId) ?: 0L,
                album = albumName,
                albumId = c.longOrNull(albumId) ?: 0L,
                genre = c.stringOrNull(genre).cleanMetadata(),
                durationMs = durationMs,
                sizeBytes = c.longOrNull(size) ?: 0L,
                mimeType = c.stringOrNull(mime).orEmpty(),
                folderPath = c.stringOrNull(relativePath).orEmpty(),
                dateAddedEpochSec = c.longOrNull(dateAdded) ?: 0L,
                dateModifiedEpochSec = c.longOrNull(dateModified) ?: 0L,
                trackNumber = c.intOrNull(track)?.takeIf { it > 0 },
                year = c.intOrNull(year)?.takeIf { it > 0 },
                width = c.intOrNull(width)?.takeIf { it > 0 },
                height = c.intOrNull(height)?.takeIf { it > 0 },
                isMusic = kind == MediaKind.AUDIO && (c.intOrNull(isMusic) ?: 0) != 0,
                duplicateKey = duplicateKeyOf(kind, cleanTitle, artistName, albumName, durationMs)
            )
        }
    }

    private companion object {
        val COMMON = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val AUDIO_PROJECTION = COMMON + listOfNotNull(
            MediaStore.Audio.AudioColumns.ARTIST,
            MediaStore.Audio.AudioColumns.ARTIST_ID,
            MediaStore.Audio.AudioColumns.ALBUM,
            MediaStore.Audio.AudioColumns.ALBUM_ID,
            MediaStore.Audio.AudioColumns.TRACK,
            MediaStore.Audio.AudioColumns.YEAR,
            MediaStore.Audio.AudioColumns.IS_MUSIC,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.Audio.AudioColumns.GENRE else null
        )
        val VIDEO_PROJECTION = COMMON + arrayOf(
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT
        )
    }
}

/** MediaStore reports missing tags as "<unknown>" or blanks; both mean "no value". */
internal fun String?.cleanMetadata(): String? =
    this?.trim()?.takeUnless { it.isEmpty() || it.equals(MediaStore.UNKNOWN_STRING, ignoreCase = true) }

private fun Cursor.stringOrNull(index: Int): String? = if (index < 0 || isNull(index)) null else getString(index)
private fun Cursor.longOrNull(index: Int): Long? = if (index < 0 || isNull(index)) null else getLong(index)
private fun Cursor.intOrNull(index: Int): Int? = if (index < 0 || isNull(index)) null else getInt(index)
