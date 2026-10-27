package app.resonance.core.media.video

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/**
 * Creates the player for the full-screen video screen.
 *
 * Video uses its own player rather than the background audio session: it lives exactly as long
 * as the video screen's ViewModel (surviving rotation, released in onCleared), and taking audio
 * focus pauses the music session automatically.
 */
interface VideoPlayerFactory {
    fun create(): Player
}

internal class ExoVideoPlayerFactory @Inject constructor(
    @param:ApplicationContext private val context: Context
) : VideoPlayerFactory {
    override fun create(): Player = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
        .build()

    private companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface VideoPlayerModule {
    @Binds fun factory(impl: ExoVideoPlayerFactory): VideoPlayerFactory
}
