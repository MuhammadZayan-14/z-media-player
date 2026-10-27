package app.resonance.feature.settings

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.resonance.core.designsystem.component.LoadingState
import app.resonance.core.designsystem.component.SectionHeader
import app.resonance.core.designsystem.theme.supportsDynamicColor
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.ThemeMode
import app.resonance.core.model.UserPreferences

@Composable
internal fun SettingsRoute(appVersion: String, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, appVersion, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: SettingsUiState, appVersion: String, viewModel: SettingsViewModel) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        val ready = state as? SettingsUiState.Ready
        if (ready == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val prefs = ready.preferences
        LazyColumn(Modifier.padding(padding).testTag("settings_list")) {
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item { ThemeChoices(prefs.themeMode, viewModel::setThemeMode) }
            item {
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(
                        if (supportsDynamicColor) {
                            R.string.settings_dynamic_color_summary
                        } else {
                            R.string.settings_dynamic_color_unavailable
                        }
                    ),
                    checked = prefs.useDynamicColor && supportsDynamicColor,
                    enabled = supportsDynamicColor,
                    onChange = viewModel::setDynamicColor
                )
            }

            item { SectionHeader(stringResource(R.string.settings_playback)) }
            item {
                SwitchRow(
                    stringResource(R.string.settings_skip_silence),
                    stringResource(R.string.settings_skip_silence_summary),
                    prefs.skipSilence,
                    onChange = viewModel::setSkipSilence
                )
            }
            item {
                SwitchRow(
                    stringResource(R.string.settings_restore_session),
                    stringResource(R.string.settings_restore_session_summary),
                    prefs.restoreLastSession,
                    onChange = viewModel::setRestoreSession
                )
            }

            item { SectionHeader(stringResource(R.string.settings_library)) }
            item {
                SwitchRow(
                    stringResource(R.string.settings_hide_duplicates),
                    stringResource(R.string.settings_hide_duplicates_summary),
                    prefs.hideDuplicates,
                    onChange = viewModel::setHideDuplicates
                )
            }
            item {
                SwitchRow(
                    stringResource(R.string.settings_include_non_music),
                    stringResource(R.string.settings_include_non_music_summary),
                    prefs.includeNonMusicAudio,
                    onChange = viewModel::setIncludeNonMusic
                )
            }
            item { MinDurationChoices(prefs.minAudioDurationSec, viewModel::setMinDuration) }
            item { ScanRow(ready.scan, viewModel::rescan) }
            item {
                ListItem(
                    modifier = Modifier.clickable(role = Role.Button) { confirmClear = true },
                    headlineContent = { Text(stringResource(R.string.settings_clear_history)) },
                    supportingContent = { Text(stringResource(R.string.settings_clear_history_summary)) }
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                    supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) }
                )
            }
            item { ListItem(headlineContent = { Text("Resonance") }, supportingContent = { Text(appVersion) }) }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.settings_clear_history_confirm)) },
            text = { Text(stringResource(R.string.settings_clear_history_summary)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text(stringResource(R.string.settings_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.settings_cancel)) }
            }
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    // The whole row is one toggleable target, so TalkBack reads title, summary and state together.
    ListItem(
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onChange
        ),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}

@Composable
private fun ThemeChoices(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    ThemeMode.entries.forEach { mode ->
        ListItem(
            modifier = Modifier.selectable(selected = mode == current, role = Role.RadioButton) { onSelect(mode) },
            headlineContent = {
                Text(
                    stringResource(
                        when (mode) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                            ThemeMode.DARK -> R.string.settings_theme_dark
                        }
                    )
                )
            },
            leadingContent = { RadioButton(selected = mode == current, onClick = null) }
        )
    }
}

@Composable
private fun MinDurationChoices(current: Int, onSelect: (Int) -> Unit) {
    ListItem(headlineContent = { Text(stringResource(R.string.settings_min_duration)) })
    UserPreferences.MIN_AUDIO_DURATION_CHOICES.forEach { seconds ->
        ListItem(
            modifier = Modifier.selectable(selected = seconds == current, role = Role.RadioButton) {
                onSelect(seconds)
            },
            headlineContent = {
                Text(
                    if (seconds == 0) {
                        stringResource(R.string.settings_min_duration_none)
                    } else {
                        stringResource(R.string.settings_min_duration_value, seconds)
                    }
                )
            },
            leadingContent = { RadioButton(selected = seconds == current, onClick = null) }
        )
    }
}

@Composable
private fun ScanRow(scan: LibraryScanState, onRescan: () -> Unit) {
    val summary = when (scan) {
        LibraryScanState.Scanning -> stringResource(R.string.settings_scanning)

        is LibraryScanState.Done -> stringResource(
            R.string.settings_last_scan,
            DateUtils.getRelativeTimeSpanString(scan.finishedAtEpochMs).toString(),
            scan.itemCount
        )

        is LibraryScanState.Failed -> stringResource(
            if (scan.error == app.resonance.core.model.LibraryError.PermissionDenied) {
                R.string.settings_scan_permission
            } else {
                R.string.settings_scan_failed
            }
        )

        LibraryScanState.Idle -> stringResource(R.string.settings_scan_never)
    }
    ListItem(
        modifier = Modifier.clickable(
            role = Role.Button,
            enabled = scan != LibraryScanState.Scanning,
            onClick = onRescan
        ),
        headlineContent = { Text(stringResource(R.string.settings_rescan)) },
        supportingContent = { Text(summary) },
        leadingContent = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
    )
}
