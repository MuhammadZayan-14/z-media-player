package app.resonance

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

/** Critical journeys through the real app: tab navigation, search, and creating a playlist. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    private val hilt = HiltAndroidRule(this)
    private val permissions: TestRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hilt).around(permissions).around(compose)

    @Test
    fun everyTopLevelDestinationOpens() {
        compose.onNodeWithTag("nav_music").performClick()
        compose.onNodeWithTag("tab_songs").assertIsDisplayed()

        compose.onNodeWithTag("nav_videos").performClick()
        compose.onNodeWithContentDescription("Open a video file").assertIsDisplayed()

        compose.onNodeWithTag("nav_playlists").performClick()
        compose.onNodeWithText("Collections").assertIsDisplayed()

        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_list").assertIsDisplayed()

        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithContentDescription("Search").assertIsDisplayed()
    }

    @Test
    fun searchOpensFromHomeAndReturns() {
        compose.onNodeWithContentDescription("Search").performClick()
        compose.onNodeWithTag("search_field").performTextInput("zzzz-no-such-track")
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount("No results") > 0
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
    }

    @Test
    fun aNewPlaylistAppearsInTheList() {
        val name = "Instrumented ${System.currentTimeMillis()}"
        compose.onNodeWithTag("nav_playlists").performClick()
        compose.onNodeWithTag("create_playlist").performClick()
        compose.onNodeWithTag("playlist_name_field").performTextInput(name)
        compose.onNodeWithTag("playlist_name_confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(name) > 0 }
        compose.onNodeWithText(name).assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
