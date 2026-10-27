package app.resonance.core.model

data class Playlist(
    val id: Long,
    val name: String,
    val trackCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

/** Collections the app maintains automatically, shown beside user playlists. */
enum class SmartCollection {
    FAVORITES,
    RECENTLY_PLAYED,
    MOST_PLAYED,
    RECENTLY_ADDED
}

data class VideoProgress(
    val mediaId: Long,
    val positionMs: Long,
    val durationMs: Long
) {
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
