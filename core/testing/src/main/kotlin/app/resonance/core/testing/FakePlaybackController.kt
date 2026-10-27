package app.resonance.core.testing

import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.model.MediaFile
import app.resonance.core.model.PlaybackEvent
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.QueueItem
import app.resonance.core.model.RepeatMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A controller that behaves like a minimal player: it keeps a queue and current index and
 * records every command, so tests can assert on intent without a MediaSession.
 */
class FakePlaybackController : PlaybackController {
    override val state = MutableStateFlow(PlaybackState(isConnected = true))
    override val events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val position = MutableStateFlow(0L)
    val commands = mutableListOf<String>()

    override fun positionMs(): Flow<Long> = position

    override fun play(items: List<MediaFile>, startIndex: Int, shuffle: Boolean) {
        commands += "play(${items.map { it.id }}, $startIndex, shuffle=$shuffle)"
        state.update {
            it.copy(
                queue = items.map(MediaFile::toQueueItem),
                currentIndex = startIndex,
                isPlaying = true,
                shuffleEnabled = shuffle,
                durationMs = items[startIndex].durationMs
            )
        }
    }

    override fun playUri(uri: String, title: String) {
        commands += "playUri($uri)"
    }

    override fun togglePlayPause() {
        commands += "togglePlayPause"
        state.update { it.copy(isPlaying = !it.isPlaying) }
    }

    override fun seekTo(positionMs: Long) {
        commands += "seekTo($positionMs)"
        position.value = positionMs
    }

    override fun skipToNext() {
        commands += "next"
        state.update { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.queue.lastIndex)) }
    }

    override fun skipToPrevious() {
        commands += "previous"
        state.update { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }
    }

    override fun skipToQueueIndex(index: Int) {
        commands += "skipTo($index)"
        state.update { it.copy(currentIndex = index) }
    }

    override fun setRepeatMode(mode: RepeatMode) {
        commands += "repeat($mode)"
        state.update { it.copy(repeatMode = mode) }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        commands += "shuffle($enabled)"
        state.update { it.copy(shuffleEnabled = enabled) }
    }

    override fun setSpeed(speed: Float) {
        commands += "speed($speed)"
        state.update { it.copy(speed = speed) }
    }

    override fun addToQueue(items: List<MediaFile>) {
        commands += "addToQueue(${items.map { it.id }})"
        state.update { it.copy(queue = it.queue + items.map(MediaFile::toQueueItem)) }
    }

    override fun playNext(items: List<MediaFile>) {
        commands += "playNext(${items.map { it.id }})"
        state.update { s ->
            val at = (s.currentIndex + 1).coerceIn(0, s.queue.size)
            s.copy(queue = s.queue.toMutableList().apply { addAll(at, items.map(MediaFile::toQueueItem)) })
        }
    }

    override fun removeFromQueue(index: Int) {
        commands += "remove($index)"
        state.update { it.copy(queue = it.queue.filterIndexed { i, _ -> i != index }) }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        commands += "move($fromIndex, $toIndex)"
        // Like the real controller, out-of-range moves are ignored.
        state.update { s ->
            if (fromIndex !in s.queue.indices || toIndex !in s.queue.indices) return@update s
            s.copy(queue = s.queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
        }
    }

    override fun clearQueue() {
        commands += "clear"
        state.update { it.copy(queue = emptyList(), currentIndex = -1, isPlaying = false) }
    }
}

private fun MediaFile.toQueueItem() = QueueItem(id.toString(), title, artist, artworkUri, durationMs)
