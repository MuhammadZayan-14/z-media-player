package app.resonance.core.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Opens one file through the Storage Access Framework. The picker grants access to that single
 * document only, so this works with no storage permission at all — the right tool when the
 * user just wants to play one file.
 *
 * Returns a function that launches the picker.
 */
@Composable
fun rememberOpenMediaDocument(
    mimeTypes: Array<String>,
    onPicked: (uri: Uri, displayName: String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) callback(uri, displayNameOf(context, uri))
    }
    return { launcher.launch(mimeTypes) }
}

private fun displayNameOf(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.') ?: uri.lastPathSegment.orEmpty()
