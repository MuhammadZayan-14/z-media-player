package app.resonance.core.media.service

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import app.resonance.core.common.ApplicationScope
import app.resonance.core.common.Log
import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.PlaybackSessionRepository
import app.resonance.core.domain.repository.UserPreferencesRepository
import app.resonance.core.domain.usecase.RestorePlaybackSessionUseCase
import app.resonance.core.media.R
import app.resonance.core.media.toMediaItem
import app.resonance.core.media.toPlayerRepeatMode
import app.resonance.core.media.toRepeatMode
import app.resonance.core.model.RestoredSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the one ExoPlayer used for audio and exposes it through a [MediaSession].
 *
 * Lifecycle: the player is created in [onCreate] and released in [onDestroy]; nothing else
 * creates or releases it. UI talks to it only through a MediaController (see
 * `MediaControllerPlaybackController`), as do the notification, lock screen, Bluetooth and
 * other external controllers. Media3 promotes the service to the foreground while playing and
 * handles audio focus, becoming-noisy and media buttons.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var restoreSession: RestorePlaybackSessionUseCase

    @Inject lateinit var sessionRepository: PlaybackSessionRepository

    @Inject lateinit var historyRepository: HistoryRepository

    @Inject lateinit var preferences: UserPreferencesRepository

    @Inject lateinit var clock: Clock

    @Inject @ApplicationScope
    lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bitmapExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private var mediaSession: MediaSession? = null

    private val historyTracker = PlayHistoryTracker()
    private val errorPolicy = ErrorSkipPolicy()
    private val saveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastSavedQueue: List<Long>? = null
    private var periodicSave: Job? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // handleAudioFocus: pause for calls/other apps, duck for navigation prompts.
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(PlayerEvents(player))

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setBitmapLoader(CacheBitmapLoader(ThumbnailBitmapLoader(this, bitmapExecutor)))
            .apply { sessionActivity()?.let(::setSessionActivity) }
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_stat_playback)
            }
        )

        observeSkipSilence(player)
        observeSaveRequests()
        serviceScope.launch { restoreIfIdle(player) }
        Log.d(TAG, "service_created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            saveNow(player)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        bitmapExecutor.shutdown()
        Log.d(TAG, "service_destroyed")
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    private fun observeSkipSilence(player: ExoPlayer) {
        serviceScope.launch {
            preferences.preferences.map { it.skipSilence }.distinctUntilChanged().collect {
                player.skipSilenceEnabled = it
            }
        }
    }

    /** Restores the previous queue (paused) when the service starts with nothing loaded. */
    private suspend fun restoreIfIdle(player: Player) {
        if (!preferences.preferences.first().restoreLastSession) return
        val restored = restoreSession() ?: return
        // A controller may have loaded something while we were reading the database.
        if (player.mediaItemCount > 0) return
        player.setMediaItems(restored.items.map { it.toMediaItem() }, restored.startIndex, restored.startPositionMs)
        player.applySettings(restored)
        lastSavedQueue = restored.items.map { it.id }
        // Not prepared: decoding starts only when the user presses play, and no notification
        // appears until then.
        Log.i(TAG, "session_restored", "items" to restored.items.size, "index" to restored.startIndex)
    }

    private fun Player.applySettings(session: RestoredSession) {
        repeatMode = session.repeatMode.toPlayerRepeatMode()
        shuffleModeEnabled = session.shuffleEnabled
        setPlaybackSpeed(session.speed)
    }

    // region Persistence

    @kotlin.OptIn(FlowPreview::class)
    private fun observeSaveRequests() {
        serviceScope.launch {
            saveRequests.debounce(SAVE_DEBOUNCE_MS).collect { mediaSession?.player?.let(::saveNow) }
        }
    }

    private fun requestSave() {
        saveRequests.tryEmit(Unit)
    }

    /**
     * Reads the player on the main thread, then writes on the application scope so the write
     * completes even when called from [onDestroy].
     */
    private fun saveNow(player: Player) {
        val snapshot = snapshotOf(
            queueMediaIds = List(player.mediaItemCount) { player.getMediaItemAt(it).mediaId },
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            repeatMode = player.repeatMode.toRepeatMode(),
            shuffleEnabled = player.shuffleModeEnabled,
            speed = player.playbackParameters.speed
        )
        if (snapshot == null) {
            if (lastSavedQueue != null) {
                lastSavedQueue = null
                appScope.launch { sessionRepository.clear() }
            }
            return
        }
        val queueChanged = snapshot.mediaIds != lastSavedQueue
        lastSavedQueue = snapshot.mediaIds
        appScope.launch { sessionRepository.save(snapshot, queueChanged) }
    }

    private fun updatePeriodicSave(isPlaying: Boolean) {
        periodicSave?.cancel()
        periodicSave = if (isPlaying) {
            serviceScope.launch {
                while (isActive) {
                    delay(PERIODIC_SAVE_MS)
                    requestSave()
                }
            }
        } else {
            null
        }
    }

    // endregion

    private inner class PlayerEvents(private val player: Player) : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            historyTracker.onItemChanged()
            if (player.isPlaying) recordPlay(mediaItem?.mediaId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) recordPlay(player.currentMediaItem?.mediaId)
            updatePeriodicSave(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) errorPolicy.onReady()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "playback_error", error, "code" to error.errorCodeName, "index" to player.currentMediaItemIndex)
            // Skip unplayable files (deleted, corrupt, unsupported) instead of stalling the queue.
            if (errorPolicy.onError(player.hasNextMediaItem())) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY
                )
            ) {
                requestSave()
            }
        }

        private fun recordPlay(mediaId: String?) {
            val id = historyTracker.onPlaying(mediaId) ?: return
            val now = clock.millis()
            appScope.launch { historyRepository.recordPlay(id, now) }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Controllers in other processes send items without a local URI; restore it. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.mapNotNullTo(ArrayList()) { it.withPlayableUri() }
            return if (resolved.isEmpty() && mediaItems.isNotEmpty()) {
                Futures.immediateFailedFuture(UnsupportedOperationException("No playable items"))
            } else {
                Futures.immediateFuture(resolved)
            }
        }

        /** System "resume" (headset button after a reboot, the resumption card in quick settings). */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaItemsWithStartPosition> = serviceScope.future {
            val enabled = preferences.preferences.first().restoreLastSession
            val restored = (if (enabled) restoreSession() else null)
                ?: throw UnsupportedOperationException("Nothing to resume")
            mediaSession.player.applySettings(restored)
            MediaItemsWithStartPosition(
                restored.items.map { it.toMediaItem() },
                restored.startIndex,
                restored.startPositionMs
            )
        }
    }

    private fun MediaItem.withPlayableUri(): MediaItem? {
        if (localConfiguration != null) return this
        val uri = requestMetadata.mediaUri
            ?: mediaId.toLongOrNull()?.let {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
            }
            ?: return null
        return buildUpon().setUri(uri).build()
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val SAVE_DEBOUNCE_MS = 500L

        /** Position-only saves while playing: a single-row update, so cheap, but no need to be frequent. */
        const val PERIODIC_SAVE_MS = 15_000L
    }
}
