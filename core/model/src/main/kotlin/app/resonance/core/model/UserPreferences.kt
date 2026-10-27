package app.resonance.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val hideDuplicates: Boolean = true,
    val minAudioDurationSec: Int = DEFAULT_MIN_AUDIO_DURATION_SEC,
    val includeNonMusicAudio: Boolean = false,
    val songSort: SongSort = SongSort.TITLE,
    val skipSilence: Boolean = false,
    val restoreLastSession: Boolean = true
) {
    companion object {
        const val DEFAULT_MIN_AUDIO_DURATION_SEC = 10
        val MIN_AUDIO_DURATION_CHOICES = listOf(0, 10, 30, 60)
    }
}
