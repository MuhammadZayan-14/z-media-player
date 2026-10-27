package app.resonance.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.resonance.core.designsystem.component.MessageState
import app.resonance.core.permissions.AccessLevel
import app.resonance.core.permissions.MediaAccess
import app.resonance.core.permissions.MediaPermissions

/** Where the permission flow stands, from the UI's point of view. */
enum class PermissionPrompt {
    /** Never asked, or the system will still show its dialog: explain, then request. */
    REQUEST,

    /** The system will not show its dialog again; only app settings can grant access. */
    OPEN_SETTINGS
}

@Stable
class MediaPermissionState internal constructor(access: MediaAccess, prompt: PermissionPrompt) {
    var access by mutableStateOf(access)
        internal set
    var prompt by mutableStateOf(prompt)
        internal set

    internal var launchRequest: () -> Unit = {}
    internal var openSettings: () -> Unit = {}

    fun request() = if (prompt == PermissionPrompt.OPEN_SETTINGS) openSettings() else launchRequest()
}

/**
 * Tracks media permissions across grants, denials and revocations. Access is re-read on every
 * resume, because the user can change it in system settings while the app is in the background.
 */
@Composable
fun rememberMediaPermissionState(onAccessChanged: (MediaAccess) -> Unit = {}): MediaPermissionState {
    val context = LocalContext.current
    val permissions = remember { MediaPermissions.toRequest(Build.VERSION.SDK_INT) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }

    val state = remember { MediaPermissionState(currentAccess(context), promptFor(context, permissions, deniedOnce)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val access = currentAccess(context)
        if (!access.canReadAnything) deniedOnce = true
        state.access = access
        state.prompt = promptFor(context, permissions, deniedOnce)
        onAccessChanged(access)
    }
    state.launchRequest = { launcher.launch(permissions.toTypedArray()) }
    state.openSettings = { context.startActivity(appSettingsIntent(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val access = currentAccess(context)
        if (access != state.access) {
            state.access = access
            onAccessChanged(access)
        }
        state.prompt = promptFor(context, permissions, deniedOnce)
    }
    return state
}

/** Full-screen explanation shown in place of library content while access is missing. */
@Composable
fun MediaPermissionRequest(state: MediaPermissionState, modifier: Modifier = Modifier) {
    if (state.prompt == PermissionPrompt.OPEN_SETTINGS) {
        MessageState(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.core_ui_permission_denied_title),
            message = stringResource(R.string.core_ui_permission_denied_message),
            actionLabel = stringResource(R.string.core_ui_permission_open_settings),
            onAction = state::request,
            modifier = modifier
        )
    } else {
        MessageState(
            icon = Icons.Rounded.LibraryMusic,
            title = stringResource(R.string.core_ui_permission_title),
            message = stringResource(R.string.core_ui_permission_message),
            actionLabel = stringResource(R.string.core_ui_permission_grant),
            onAction = state::request,
            modifier = modifier
        )
    }
}

/** Banner for Android 14's "selected videos only" grant, offering to change the selection. */
@Composable
fun PartialAccessBanner(state: MediaPermissionState, modifier: Modifier = Modifier) {
    if (state.access.video != AccessLevel.PARTIAL) return
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) {
            Text(
                stringResource(R.string.core_ui_permission_partial),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = state::request) { Text(stringResource(R.string.core_ui_permission_partial_manage)) }
        }
    }
}

private fun currentAccess(context: Context): MediaAccess = MediaPermissions.evaluate(Build.VERSION.SDK_INT) {
    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
}

/**
 * After a denial, `shouldShowRequestPermissionRationale` returning false for every permission
 * means the system has stopped showing the dialog ("don't ask again" or two denials).
 */
private fun promptFor(context: Context, permissions: List<String>, deniedOnce: Boolean): PermissionPrompt {
    val activity = context.findActivity() ?: return PermissionPrompt.REQUEST
    if (!deniedOnce) return PermissionPrompt.REQUEST
    val canAskAgain = permissions.any { activity.shouldShowRequestPermissionRationale(it) }
    return if (canAskAgain) PermissionPrompt.REQUEST else PermissionPrompt.OPEN_SETTINGS
}

private fun appSettingsIntent(context: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
