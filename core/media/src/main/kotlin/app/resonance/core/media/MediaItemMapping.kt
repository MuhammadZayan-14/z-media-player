package app.resonance.core.media

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.QueueItem
import app.resonance.core.model.RepeatMode

/** Prefix for items that did not come from the library (documents opened via the system picker). */
internal const val EXTERNAL_ID_PREFIX = "uri:"

/**
 * The URI is set twice on purpose: `localConfiguration` for local playback, and
 * `requestMetadata.mediaUri` because `localConfiguration` is stripped when a MediaItem crosses
 * to the session in another process; the session callback restores it from there.
 */
fun MediaFile.toMediaItem(): MediaItem {
    val uri = contentUri.toUri()
    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMimeType(mimeType.ifEmpty { null })
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri.toUri())
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setTrackNumber(trackNumber)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(
                    if (kind ==
                        MediaKind.VIDEO
                    ) {
                        MediaMetadata.MEDIA_TYPE_VIDEO
                    } else {
                        MediaMetadata.MEDIA_TYPE_MUSIC
                    }
                )
                .build()
        )
        .build()
}

fun externalMediaItem(uri: Uri, title: String): MediaItem = MediaItem.Builder()
    .setMediaId(EXTERNAL_ID_PREFIX + uri)
    .setUri(uri)
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
    )
    .build()

internal fun MediaItem.toQueueItem() = QueueItem(
    mediaId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString(),
    artworkUri = mediaMetadata.artworkUri?.toString(),
    durationMs = mediaMetadata.durationMs ?: 0L
)

internal fun Player.queueItems(): List<QueueItem> = List(mediaItemCount) { getMediaItemAt(it).toQueueItem() }

/** Whether the UI should offer "pause" (as opposed to "play"). */
internal val Player.showsPause: Boolean
    get() = playWhenReady && playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE

internal fun Player.toPlaybackState(queue: List<QueueItem>, connected: Boolean): PlaybackState {
    val index = if (queue.isEmpty()) -1 else currentMediaItemIndex
    return PlaybackState(
        isConnected = connected,
        queue = queue,
        currentIndex = index,
        isPlaying = showsPause,
        isBuffering = playWhenReady && playbackState == Player.STATE_BUFFERING,
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: queue.getOrNull(index)?.durationMs ?: 0L,
        repeatMode = repeatMode.toRepeatMode(),
        shuffleEnabled = shuffleModeEnabled,
        speed = playbackParameters.speed,
        hasNext = hasNextMediaItem(),
        hasPrevious = hasPreviousMediaItem()
    )
}

internal fun @Player.RepeatMode Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}

internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
}

fun PlaybackException.toPlaybackError(): PlaybackError = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
    -> PlaybackError.FileNotFound

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODING_FAILED
    -> PlaybackError.Corrupted

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
    -> PlaybackError.UnsupportedFormat

    else -> PlaybackError.Unknown(errorCode)
}
