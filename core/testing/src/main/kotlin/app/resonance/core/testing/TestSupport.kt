package app.resonance.core.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import app.resonance.core.model.MediaFile
import app.resonance.core.model.MediaKind
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Routes `Dispatchers.Main` (and so `viewModelScope`) to a test dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Instrumentation runner that swaps in Hilt's test application. */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}

fun testSong(
    id: Long,
    title: String = "Song $id",
    artist: String? = "Artist ${id % 3}",
    album: String? = "Album ${id % 2}",
    durationMs: Long = 180_000L,
    genre: String? = "Rock",
    folderPath: String = "Music/",
    dateAddedEpochSec: Long = 1_700_000_000L + id
) = MediaFile(
    id = id,
    kind = MediaKind.AUDIO,
    contentUri = "content://media/external/audio/media/$id",
    title = title,
    displayName = "$title.mp3",
    artist = artist,
    artistId = (artist?.hashCode() ?: 0).toLong(),
    album = album,
    albumId = (album?.hashCode() ?: 0).toLong(),
    genre = genre,
    durationMs = durationMs,
    sizeBytes = 4_000_000L,
    mimeType = "audio/mpeg",
    folderPath = folderPath,
    dateAddedEpochSec = dateAddedEpochSec
)

fun testVideo(id: Long, title: String = "Video $id", durationMs: Long = 600_000L) = MediaFile(
    id = id,
    kind = MediaKind.VIDEO,
    contentUri = "content://media/external/video/media/$id",
    title = title,
    displayName = "$title.mp4",
    artist = null,
    artistId = 0,
    album = null,
    albumId = 0,
    genre = null,
    durationMs = durationMs,
    sizeBytes = 50_000_000L,
    mimeType = "video/mp4",
    folderPath = "Movies/",
    dateAddedEpochSec = 1_700_000_000L + id,
    width = 1920,
    height = 1080
)
