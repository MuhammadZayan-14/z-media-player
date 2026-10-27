package app.resonance

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.theme.ResonanceTheme
import app.resonance.core.media.controller.PlaybackConnection
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences
import app.resonance.ui.ResonanceApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playbackConnection: PlaybackConnection

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.uiState.value is MainActivityUiState.Loading }
        enableEdgeToEdge()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val preferences = (state as? MainActivityUiState.Ready)?.preferences ?: UserPreferences()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app theme, which can differ from the system's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme }
                )
                onDispose {}
            }
            ResonanceTheme(darkTheme = darkTheme, dynamicColor = preferences.useDynamicColor) {
                ResonanceApp(playbackEvents = viewModel.playbackEvents, appVersion = BuildConfig.VERSION_NAME)
            }
        }
    }

    // The controller connection follows visibility; playback itself lives on in the service.
    override fun onStart() {
        super.onStart()
        playbackConnection.connect()
    }

    override fun onStop() {
        playbackConnection.disconnect()
        super.onStop()
    }

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
