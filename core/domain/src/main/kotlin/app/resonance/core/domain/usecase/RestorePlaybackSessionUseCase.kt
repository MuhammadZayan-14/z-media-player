package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.RestoredSession
import javax.inject.Inject

/**
 * Loads the last persisted session and reconciles it with the files that still exist.
 *
 * If the item that was playing has been deleted, playback resumes at the next surviving item
 * from its start (or the last surviving item if nothing follows it).
 */
class RestorePlaybackSessionUseCase @Inject constructor(
    private val sessions: PlaybackSessionRepository,
    private val library: MediaLibraryRepository
) {
    suspend operator fun invoke(): RestoredSession? {
        val snapshot = sessions.load() ?: return null
        if (snapshot.mediaIds.isEmpty()) return null
        val available = library.getMedia(snapshot.mediaIds.distinct()).associateBy { it.id }
        return resolve(snapshot, available.keys)?.let { resolved ->
            RestoredSession(
                items = resolved.ids.map { available.getValue(it) },
                startIndex = resolved.index,
                startPositionMs = resolved.positionMs,
                repeatMode = snapshot.repeatMode,
                shuffleEnabled = snapshot.shuffleEnabled,
                speed = snapshot.speed
            )
        }
    }

    internal data class Resolution(val ids: List<Long>, val index: Int, val positionMs: Long)

    internal companion object {
        fun resolve(snapshot: PlaybackSnapshot, existingIds: Set<Long>): Resolution? {
            val kept = snapshot.mediaIds.withIndex().filter { it.value in existingIds }
            if (kept.isEmpty()) return null
            val currentOriginal = snapshot.currentIndex.coerceIn(0, snapshot.mediaIds.lastIndex)
            val exact = kept.indexOfFirst { it.index == currentOriginal }
            return if (exact >= 0) {
                Resolution(kept.map { it.value }, exact, snapshot.positionMs.coerceAtLeast(0))
            } else {
                val next = kept.indexOfFirst { it.index > currentOriginal }
                Resolution(kept.map { it.value }, if (next >= 0) next else kept.lastIndex, 0L)
            }
        }
    }
}
