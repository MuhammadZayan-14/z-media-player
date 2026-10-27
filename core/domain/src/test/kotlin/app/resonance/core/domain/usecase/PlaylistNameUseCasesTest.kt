package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.PlaylistRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistNameUseCasesTest {

    private val existing = mutableMapOf(1L to "Road Trip")
    private val repository = mockk<PlaylistRepository>(relaxUnitFun = true) {
        coEvery { nameExists(any(), any()) } answers {
            val name = firstArg<String>()
            val excluding = secondArg<Long?>()
            existing.any { (id, value) -> value.equals(name, ignoreCase = true) && id != excluding }
        }
        coEvery { create(any()) } returns 7L
    }

    @Test
    fun `blank names are rejected`() = runTest {
        assertEquals(PlaylistNameResult.Blank, CreatePlaylistUseCase(repository)("   "))
    }

    @Test
    fun `names over the limit are rejected`() = runTest {
        assertEquals(
            PlaylistNameResult.TooLong,
            CreatePlaylistUseCase(repository)("x".repeat(MAX_PLAYLIST_NAME_LENGTH + 1))
        )
    }

    @Test
    fun `duplicates are detected case-insensitively after normalising whitespace`() = runTest {
        assertEquals(PlaylistNameResult.Duplicate, CreatePlaylistUseCase(repository)("  road   trip "))
    }

    @Test
    fun `valid names are trimmed and saved`() = runTest {
        val result = CreatePlaylistUseCase(repository)("  Late   night ")
        assertEquals(PlaylistNameResult.Saved(7L), result)
        coVerify { repository.create("Late night") }
    }

    @Test
    fun `renaming to its own name is not a duplicate`() = runTest {
        val result = RenamePlaylistUseCase(repository)(1L, "ROAD TRIP")
        assertEquals(PlaylistNameResult.Saved(1L), result)
        coVerify { repository.rename(1L, "ROAD TRIP") }
    }

    @Test
    fun `renaming onto another playlist's name is a duplicate`() = runTest {
        assertEquals(PlaylistNameResult.Duplicate, RenamePlaylistUseCase(repository)(2L, "road trip"))
    }
}
