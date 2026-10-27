package app.resonance.feature.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.model.PlaybackState
import app.resonance.core.model.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val playing = PlaybackState(
        isConnected = true,
        queue = listOf(
            QueueItem("1", "Midnight City", "M83", null, 240_000),
            QueueItem("2", "Wait", "M83", null, 200_000)
        ),
        currentIndex = 0,
        isPlaying = true,
        durationMs = 240_000,
        hasNext = true
    )

    @Test
    fun miniPlayerShowsTrackAndForwardsTaps() {
        var toggles = 0
        var expanded = 0
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                MiniPlayerContent(playing, 60_000, onExpand = { expanded++ }, onTogglePlay = { toggles++ }, onNext = {})
            }
        }
        compose.onNodeWithText("Midnight City").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.onNodeWithText("M83").performClick()
        assertEquals(1, toggles)
        assertEquals(1, expanded)
    }

    @Test
    fun nowPlayingShowsControlsAndForwardsActions() {
        val calls = mutableListOf<String>()
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                NowPlayingScreen(
                    state = PlayerUiState(playing, isFavorite = false),
                    positionMs = 30_000,
                    onCollapse = { calls += "collapse" },
                    actions = actions(calls)
                )
            }
        }
        compose.onNodeWithTag("now_playing_title").assertIsDisplayed()
        compose.onNodeWithText("0:30").assertIsDisplayed()
        compose.onNodeWithText("4:00").assertIsDisplayed()
        compose.onNodeWithTag("now_playing_toggle").performClick()
        compose.onNodeWithContentDescription("Next track").performClick()
        compose.onNodeWithContentDescription("Repeat off").performClick()
        compose.onNodeWithContentDescription("Add to favorites").performClick()
        assertEquals(listOf("toggle", "next", "repeat", "favorite"), calls)
    }

    @Test
    fun emptyQueueShowsAnExplanation() {
        compose.setContent {
            ResonanceTheme(dynamicColor = false) {
                NowPlayingScreen(PlayerUiState(), 0, onCollapse = {}, actions = actions(mutableListOf()))
            }
        }
        compose.onNodeWithText("Nothing is playing").assertIsDisplayed()
    }

    private fun actions(calls: MutableList<String>) = NowPlayingActions(
        onTogglePlay = { calls += "toggle" },
        onNext = { calls += "next" },
        onPrevious = { calls += "previous" },
        onSeek = { calls += "seek" },
        onShuffle = { calls += "shuffle" },
        onRepeat = { calls += "repeat" },
        onSpeed = { calls += "speed" },
        onFavorite = { calls += "favorite" },
        onAddToPlaylist = { calls += "playlist" },
        onShowQueue = { calls += "queue" }
    )
}
