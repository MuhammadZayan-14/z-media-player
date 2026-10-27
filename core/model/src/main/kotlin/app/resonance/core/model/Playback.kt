package app.resonance.core.model

enum class RepeatMode { OFF, ONE, ALL }

/** An entry in the live player queue, as reported by the media session. */
data class QueueItem(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val durationMs: Long
) {
    /** MediaStore id when the item came from the library; null for files opened through the system picker. */
    val libraryId: Long? get() = mediaId.toLongOrNull()
}

/**
 * Snapshot of the session player. Playback position is deliberately not part of it:
 * it changes many times a second and is exposed as a separate flow so that the whole
 * state is not re-emitted (and every screen recomposed) on each tick.
 */
data class PlaybackState(
    val isConnected: Boolean = false,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    val current: QueueItem? get() = queue.getOrNull(currentIndex)
    val hasMedia: Boolean get() = current != null

    companion object {
        val SPEED_CHOICES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    }
}

sealed interface PlaybackError {
    data object FileNotFound : PlaybackError
    data object UnsupportedFormat : PlaybackError
    data object Corrupted : PlaybackError
    data class Unknown(val code: Int) : PlaybackError
}

sealed interface PlaybackEvent {
    data class Failed(val error: PlaybackError, val title: String?) : PlaybackEvent
}

/** What is persisted between process deaths so the last session can be restored. */
data class PlaybackSnapshot(
    val mediaIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
    val speed: Float
)

/** A snapshot whose ids have been resolved against the current library. */
data class RestoredSession(
    val items: List<MediaFile>,
    val startIndex: Int,
    val startPositionMs: Long,
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
    val speed: Float
)
