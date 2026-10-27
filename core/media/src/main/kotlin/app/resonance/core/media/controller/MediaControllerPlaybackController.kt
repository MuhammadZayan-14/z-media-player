package app.resonance.core.media.controller

import android.content.ComponentName
import android.content.Context
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.resonance.core.common.ApplicationScope
import app.resonance.core.common.Log
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.media.externalMediaItem
import app.resonance.core.media.queueItems
import app.resonance.core.media.service.PlaybackService
import app.resonance.core.media.toMediaItem
import app.resonance.core.media.toPlaybackError
import app.resonance.core.media.toPlaybackState
import app.resonance.core.media.toPlayerRepeatMode
import app.resonance.core.model.MediaFile
import app.resonance.core.model.PlaybackEvent
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.QueueItem
import app.resonance.core.model.RepeatMode
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Lets the UI host bind the controller connection to its own lifecycle. */
interface PlaybackConnection {
    /** Call when the UI becomes visible (onStart). */
    fun connect()

    /** Call when the UI is no longer visible (onStop). Playback continues in the service. */
    fun disconnect()
}

/**
 * The app's single [MediaController], connected to [PlaybackService].
 *
 * Process-wide by design (one connection per process is the Media3 recommendation), but its
 * connection follows the visible UI: [connect] / [disconnect] are driven by the activity
 * lifecycle, so the service is not kept bound in the background.
 *
 * Threading: MediaController must be used on the main thread. Commands are funnelled through
 * one channel consumed on the main thread, which also preserves their order across the
 * asynchronous connection handshake.
 */
@Singleton
class MediaControllerPlaybackController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val appScope: CoroutineScope
) : PlaybackController, PlaybackConnection {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = Channel<PlaybackEvent>(Channel.BUFFERED)
    override val events: Flow<PlaybackEvent> = _events.receiveAsFlow()

    private val commands = Channel<(MediaController) -> Unit>(Channel.UNLIMITED)

    // Main-thread only.
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var wantsConnection = false
    private var queue: List<QueueItem> = emptyList()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) queue = player.queueItems()
            publish(player)
        }

        override fun onPlayerError(error: PlaybackException) {
            val title = controller?.currentMediaItem?.mediaMetadata?.title?.toString()
            _events.trySend(PlaybackEvent.Failed(error.toPlaybackError(), title))
        }
    }

    init {
        appScope.launch(Dispatchers.Main.immediate) {
            for (command in commands) {
                val connected = awaitController() ?: continue
                command(connected)
                if (!wantsConnection) releaseController()
            }
        }
    }

    @MainThread
    override fun connect() {
        wantsConnection = true
        ensureConnecting()
    }

    @MainThread
    override fun disconnect() {
        wantsConnection = false
        releaseController()
    }

    @MainThread
    private fun ensureConnecting(): ListenableFuture<MediaController> {
        controllerFuture?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    // The service died or was stopped; reconnect lazily on the next command.
                    Log.i(TAG, "controller_disconnected")
                    releaseController()
                    if (wantsConnection) ensureConnecting()
                }
            })
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val connected = runCatching { future.get() }.getOrElse { error ->
                    Log.e(TAG, "controller_connect_failed", error)
                    controllerFuture = null
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                queue = connected.queueItems()
                publish(connected)
            },
            ContextCompat.getMainExecutor(context)
        )
        return future
    }

    @MainThread
    private fun releaseController() {
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        _state.update { it.copy(isConnected = false) }
    }

    @MainThread
    private suspend fun awaitController(): MediaController? = try {
        controller?.takeIf { it.isConnected } ?: ensureConnecting().await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "controller_unavailable", e)
        controllerFuture = null
        null
    }

    private fun publish(player: Player) {
        _state.value = player.toPlaybackState(queue, connected = true)
    }

    private fun send(command: (MediaController) -> Unit) {
        commands.trySend(command)
    }

    override fun positionMs(): Flow<Long> = flow {
        while (true) {
            val current = controller
            emit(current?.currentPosition ?: 0L)
            delay(if (current?.isPlaying == true) TICK_PLAYING_MS else TICK_PAUSED_MS)
        }
    }.distinctUntilChanged().flowOn(Dispatchers.Main.immediate)

    override fun play(items: List<MediaFile>, startIndex: Int, shuffle: Boolean) {
        if (items.isEmpty()) return
        val mediaItems = items.map { it.toMediaItem() }
        send { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(mediaItems, startIndex.coerceIn(items.indices), 0L)
            c.prepare()
            c.play()
        }
    }

    override fun playUri(uri: String, title: String) {
        val item = externalMediaItem(uri.toUri(), title)
        send { c ->
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    override fun togglePlayPause() = send { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    override fun seekTo(positionMs: Long) = send { it.seekTo(positionMs.coerceAtLeast(0)) }

    override fun skipToNext() = send { it.seekToNext() }

    override fun skipToPrevious() = send { it.seekToPrevious() }

    override fun skipToQueueIndex(index: Int) = send { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            c.play()
        }
    }

    override fun setRepeatMode(mode: RepeatMode) = send { it.repeatMode = mode.toPlayerRepeatMode() }

    override fun setShuffleEnabled(enabled: Boolean) = send { it.shuffleModeEnabled = enabled }

    override fun setSpeed(speed: Float) = send { it.setPlaybackSpeed(speed.coerceIn(MIN_SPEED, MAX_SPEED)) }

    override fun addToQueue(items: List<MediaFile>) {
        if (items.isEmpty()) return
        val mediaItems = items.map { it.toMediaItem() }
        send { c ->
            val wasEmpty = c.mediaItemCount == 0
            c.addMediaItems(mediaItems)
            if (wasEmpty) c.prepare()
        }
    }

    override fun playNext(items: List<MediaFile>) {
        if (items.isEmpty()) return
        val mediaItems = items.map { it.toMediaItem() }
        send { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(mediaItems)
                c.prepare()
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, mediaItems)
            }
        }
    }

    override fun removeFromQueue(index: Int) = send { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = send { c ->
        val range = 0 until c.mediaItemCount
        if (fromIndex in range && toIndex in range && fromIndex != toIndex) c.moveMediaItem(fromIndex, toIndex)
    }

    override fun clearQueue() = send { c ->
        c.stop()
        c.clearMediaItems()
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val TICK_PLAYING_MS = 250L
        const val TICK_PAUSED_MS = 1_000L
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 3f
    }
}
