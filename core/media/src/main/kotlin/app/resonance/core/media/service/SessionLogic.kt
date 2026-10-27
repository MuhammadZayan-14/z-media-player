package app.resonance.core.media.service

import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.RepeatMode

/**
 * Builds the persisted snapshot from the live queue. Only library items can be restored after
 * a restart (picker documents lose their URI grant), so others are dropped and the current
 * index is re-based onto the surviving items.
 */
internal fun snapshotOf(
    queueMediaIds: List<String>,
    currentIndex: Int,
    positionMs: Long,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    speed: Float
): PlaybackSnapshot? {
    val libraryIds = ArrayList<Long>(queueMediaIds.size)
    var rebasedIndex = 0
    var currentIsLibraryItem = false
    queueMediaIds.forEachIndexed { index, mediaId ->
        val id = mediaId.toLongOrNull() ?: return@forEachIndexed
        if (index < currentIndex) rebasedIndex++
        if (index == currentIndex) currentIsLibraryItem = true
        libraryIds += id
    }
    if (libraryIds.isEmpty()) return null
    return PlaybackSnapshot(
        mediaIds = libraryIds,
        currentIndex = rebasedIndex.coerceAtMost(libraryIds.lastIndex),
        positionMs = if (currentIsLibraryItem) positionMs.coerceAtLeast(0) else 0L,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        speed = speed
    )
}

/**
 * Decides when a "play" is recorded in history: once each time an item starts playing,
 * including each loop of repeat-one, but not when the same item is paused and resumed.
 */
internal class PlayHistoryTracker {
    private var countedMediaId: String? = null

    /** The current item changed (new track, auto-advance, or a repeat-one loop). */
    fun onItemChanged() {
        countedMediaId = null
    }

    /** Playback is running on [mediaId]; returns the library id to record, or null. */
    fun onPlaying(mediaId: String?): Long? {
        if (mediaId == null || mediaId == countedMediaId) return null
        countedMediaId = mediaId
        return mediaId.toLongOrNull()
    }
}

/**
 * Stops the service from skipping forever when every item in the queue fails: after
 * [limit] consecutive errors without a successful start, recovery gives up.
 */
internal class ErrorSkipPolicy(private val limit: Int = 5) {
    private var consecutive = 0

    fun onReady() {
        consecutive = 0
    }

    /** Returns true if the player should skip to the next item. */
    fun onError(hasNext: Boolean): Boolean {
        consecutive++
        return hasNext && consecutive < limit
    }
}
