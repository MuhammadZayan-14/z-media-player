package app.resonance

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.media.controller.PlaybackConnection
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.RepeatMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real controller -> MediaSession -> PlaybackService -> ExoPlayer path on a device,
 * with generated WAV files so the test does not depend on what media the device holds.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaybackServiceIntegrationTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var controller: PlaybackController

    @Inject lateinit var connection: PlaybackConnection

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        hilt.inject()
        instrumentation.runOnMainSync { connection.connect() }
    }

    @After
    fun tearDown() {
        controller.clearQueue()
        awaitState { it.queue.isEmpty() }
        instrumentation.runOnMainSync { connection.disconnect() }
    }

    @Test
    fun playsPausesAndReportsProgress() {
        controller.play(listOf(tone(1, seconds = 4)), startIndex = 0)

        val playing = awaitState { it.isPlaying && it.current?.mediaId == "1" }
        assertEquals("Tone 1", playing.current?.title)

        val position = runBlocking { withTimeout(TIMEOUT_MS) { controller.positionMs().first { it > 300 } } }
        assertTrue(position > 300)

        controller.togglePlayPause()
        awaitState { !it.isPlaying }
    }

    @Test
    fun queueEditsAndModesAreReflectedInState() {
        controller.play(listOf(tone(1), tone(2)), startIndex = 0)
        awaitState { it.queue.size == 2 }

        controller.addToQueue(listOf(tone(3)))
        controller.playNext(listOf(tone(4)))
        val queued = awaitState { it.queue.size == 4 }
        assertEquals(listOf("1", "4", "2", "3"), queued.queue.map { it.mediaId })

        controller.moveQueueItem(3, 1)
        controller.removeFromQueue(2)
        val edited = awaitState { it.queue.map { q -> q.mediaId } == listOf("1", "3", "4") }
        assertEquals(0, edited.currentIndex)

        controller.setRepeatMode(RepeatMode.ONE)
        controller.setShuffleEnabled(true)
        controller.setSpeed(1.5f)
        val modes = awaitState { it.repeatMode == RepeatMode.ONE && it.shuffleEnabled && it.speed == 1.5f }
        assertTrue(modes.hasMedia)
    }

    @Test
    fun skipsToTheNextTrack() {
        controller.play(listOf(tone(1), tone(2)), startIndex = 0)
        awaitState { it.isPlaying && it.currentIndex == 0 }

        controller.skipToNext()

        awaitState { it.currentIndex == 1 && it.current?.mediaId == "2" }
    }

    private fun awaitState(predicate: (PlaybackState) -> Boolean): PlaybackState =
        runBlocking { withTimeout(TIMEOUT_MS) { controller.state.first(predicate) } }

    private fun tone(id: Long, seconds: Int = 2): MediaFile {
        val file = File(context.cacheDir, "tone_$id.wav")
        if (!file.exists()) writeSineWav(file, seconds, frequencyHz = 220.0 * id)
        return MediaFile(
            id = id, kind = MediaKind.AUDIO, contentUri = Uri.fromFile(file).toString(), title = "Tone $id",
            displayName = file.name, artist = "Test", artistId = 0, album = null, albumId = 0, genre = null,
            durationMs = seconds * 1000L, sizeBytes = file.length(), mimeType = "audio/wav", folderPath = "",
            dateAddedEpochSec = 0,
        )
    }

    /** 16-bit mono PCM WAV. */
    private fun writeSineWav(file: File, seconds: Int, frequencyHz: Double) {
        val sampleRate = 22_050
        val samples = sampleRate * seconds
        RandomAccessFile(file, "rw").use { out ->
            fun intLe(v: Int) = out.write(ByteArray(4) { i -> (v shr (8 * i)).toByte() })
            fun shortLe(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF")
            intLe(36 + samples * 2)
            out.writeBytes("WAVEfmt ")
            intLe(16)
            shortLe(1)
            shortLe(1)
            intLe(sampleRate)
            intLe(sampleRate * 2)
            shortLe(2)
            shortLe(16)
            out.writeBytes("data")
            intLe(samples * 2)
            val buffer = ByteArray(samples * 2)
            for (i in 0 until samples) {
                val v = (sin(2 * PI * frequencyHz * i / sampleRate) * 8_000).toInt()
                buffer[i * 2] = v.toByte()
                buffer[i * 2 + 1] = (v shr 8).toByte()
            }
            out.write(buffer)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
