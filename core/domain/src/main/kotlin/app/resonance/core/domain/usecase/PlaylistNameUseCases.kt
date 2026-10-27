package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.PlaylistRepository
import javax.inject.Inject

sealed interface PlaylistNameResult {
    data class Saved(val playlistId: Long) : PlaylistNameResult
    data object Blank : PlaylistNameResult
    data object TooLong : PlaylistNameResult
    data object Duplicate : PlaylistNameResult
}

const val MAX_PLAYLIST_NAME_LENGTH = 60

class CreatePlaylistUseCase @Inject constructor(
    private val playlists: PlaylistRepository
) {
    suspend operator fun invoke(rawName: String): PlaylistNameResult {
        val name = rawName.normalisedPlaylistName()
        validatePlaylistName(name, excludingId = null, playlists)?.let { return it }
        return PlaylistNameResult.Saved(playlists.create(name))
    }
}

class RenamePlaylistUseCase @Inject constructor(
    private val playlists: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: Long, rawName: String): PlaylistNameResult {
        val name = rawName.normalisedPlaylistName()
        validatePlaylistName(name, playlistId, playlists)?.let { return it }
        playlists.rename(playlistId, name)
        return PlaylistNameResult.Saved(playlistId)
    }
}

/** Collapses internal whitespace so "Road  trip " and "Road trip" count as the same name. */
internal fun String.normalisedPlaylistName(): String = trim().replace(Regex("\\s+"), " ")

private suspend fun validatePlaylistName(
    name: String,
    excludingId: Long?,
    playlists: PlaylistRepository
): PlaylistNameResult? = when {
    name.isEmpty() -> PlaylistNameResult.Blank
    name.length > MAX_PLAYLIST_NAME_LENGTH -> PlaylistNameResult.TooLong
    playlists.nameExists(name, excludingId) -> PlaylistNameResult.Duplicate
    else -> null
}
