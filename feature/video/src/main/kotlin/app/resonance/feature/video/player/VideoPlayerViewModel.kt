package app.resonance.feature.video.player

import androidx.compose.ui.layout.ContentScale
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import app.resonance.core.common.ApplicationScope
import app.resonance.core.common.Log
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.media.toPlaybackError
import app.resonance.core.media.video.VideoPlayerFactory
import app.resonance.core.model.PlaybackError
import app.resonance.core.model.VideoProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AspectMode(val contentScale: ContentScale) {
    FIT(ContentScale.Fit),
    ZOOM(ContentScale.Crop),
    STRETCH(ContentScale.FillBounds)
}

data class VideoUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val aspect: AspectMode = AspectMode.FIT,
    /** Width / height of the decoded video, once known. */
    val videoAspectRatio: Float? = null,
    val error: PlaybackError? = null
)

/**
 * Owns the video [player] for as long as the video screen exists — across rotation and
 * picture-in-picture — and releases it in [onCleared]. Progress is saved when playback pauses,
 * periodically while playing, and on the way out, so reopening the video resumes where it left off.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val library: MediaLibraryRepository,
    private val progress: VideoProgressRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
    playerFactory: VideoPlayerFactory
) : ViewModel() {

    val player: Player = playerFactory.create()

    private val mediaId: Long? = savedStateHandle.get<Long>(EXTRA_MEDIA_ID)?.takeIf { it >= 0 }
    private val externalUri: String? = savedStateHandle.get<String>(EXTRA_URI)

    private val _uiState = MutableStateFlow(VideoUiState())
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private var periodicSave: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _uiState.update {
                it.copy(
                    isPlaying = player.isPlaying,
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                    durationMs = player.duration.takeIf { d -> d != C.TIME_UNSET } ?: it.durationMs,
                    speed = player.playbackParameters.speed
                )
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            periodicSave?.cancel()
            if (isPlaying) {
                periodicSave = viewModelScope.launch {
                    while (isActive) {
                        delay(PERIODIC_SAVE_MS)
                        saveProgress()
                    }
                }
            } else {
                saveProgress()
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                _uiState.update { it.copy(videoAspectRatio = ratio) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "video_error", error, "code" to error.errorCodeName)
            _uiState.update { it.copy(error = error.toPlaybackError()) }
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = mediaId
        val item: MediaItem
        var startMs = 0L
        if (id != null) {
            val media = library.getMedia(id)
            if (media == null) {
                _uiState.update { it.copy(error = PlaybackError.FileNotFound, isBuffering = false) }
                return
            }
            item = MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(media.contentUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(media.title).build())
                .build()
            startMs = progress.get(id)?.positionMs ?: 0L
            _uiState.update { it.copy(title = media.title, durationMs = media.durationMs) }
        } else {
            val uri = externalUri ?: run {
                _uiState.update { it.copy(error = PlaybackError.FileNotFound, isBuffering = false) }
                return
            }
            item = MediaItem.fromUri(uri.toUri())
            _uiState.update { it.copy(title = uri.toUri().lastPathSegment.orEmpty()) }
        }
        player.setMediaItem(item, startMs)
        player.prepare()
        player.playWhenReady = true
    }

    /** Position, ticking while collected (i.e. while the controls are visible). */
    fun positionMs(): Flow<Long> = flow {
        while (true) {
            emit(player.currentPosition)
            delay(TICK_MS)
        }
    }

    fun togglePlayPause() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }

            player.isPlaying -> player.pause()

            else -> player.play()
        }
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))
    fun seekBack() = player.seekBack()
    fun seekForward() = player.seekForward()
    fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    fun cycleAspect() {
        _uiState.update { it.copy(aspect = AspectMode.entries[(it.aspect.ordinal + 1) % AspectMode.entries.size]) }
    }

    fun retry() {
        _uiState.update { it.copy(error = null) }
        player.prepare()
    }

    /** The screen went to the background (not into picture-in-picture). */
    fun onBackgrounded() {
        player.pause()
    }

    private fun saveProgress() {
        val id = mediaId ?: return
        val snapshot = VideoProgress(id, player.currentPosition, player.duration.takeIf { it != C.TIME_UNSET } ?: 0L)
        // Application scope: the write must finish even if this ViewModel is being cleared.
        appScope.launch { progress.save(snapshot) }
    }

    override fun onCleared() {
        saveProgress()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }

    companion object {
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_URI = "uri"
        private const val TAG = "VideoPlayer"
        private const val PERIODIC_SAVE_MS = 10_000L
        private const val TICK_MS = 500L
    }
}
