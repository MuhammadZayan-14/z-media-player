package app.resonance.core.domain.usecase

import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.model.MediaFile
import javax.inject.Inject
import kotlin.random.Random

/** The per-track and per-list commands every library screen offers. */
class TrackCommands @Inject constructor(
    private val controller: PlaybackController,
    private val toggleFavorite: ToggleFavoriteUseCase
) {
    fun playFrom(tracks: List<MediaFile>, index: Int) {
        if (tracks.isNotEmpty()) controller.play(tracks, index.coerceIn(tracks.indices))
    }

    /** Starts at a random track with shuffle on, so the first song is not always the same one. */
    fun shuffle(tracks: List<MediaFile>, random: Random = Random.Default) {
        if (tracks.isNotEmpty()) controller.play(tracks, random.nextInt(tracks.size), shuffle = true)
    }

    fun playNext(track: MediaFile) = controller.playNext(listOf(track))

    fun addToQueue(track: MediaFile) = controller.addToQueue(listOf(track))

    suspend fun toggleFavorite(track: MediaFile): Boolean = toggleFavorite(track.id)
}
