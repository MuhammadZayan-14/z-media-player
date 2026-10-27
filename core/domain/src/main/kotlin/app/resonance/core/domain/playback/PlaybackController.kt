package app.resonance.core.domain.playback

import app.resonance.core.model.MediaFile
import app.resonance.core.model.PlaybackEvent
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remote control for the background audio session.
 *
 * Commands are fire-and-forget and are applied in the order they were issued, even when they
 * arrive before the session connection is established.
 */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    /** One-off events such as playback failures; each is delivered once. */
    val events: Flow<PlaybackEvent>

    /** Current position, ticking while collected. Kept apart from [state] to limit recomposition. */
    fun positionMs(): Flow<Long>

    fun play(items: List<MediaFile>, startIndex: Int = 0, shuffle: Boolean = false)

    /** Plays a single file the user picked through the system document picker. */
    fun playUri(uri: String, title: String)
    fun togglePlayPause()
    fun seekTo(positionMs: Long)
    fun skipToNext()
    fun skipToPrevious()
    fun skipToQueueIndex(index: Int)
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)
    fun setSpeed(speed: Float)
    fun addToQueue(items: List<MediaFile>)
    fun playNext(items: List<MediaFile>)
    fun removeFromQueue(index: Int)
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun clearQueue()
}
