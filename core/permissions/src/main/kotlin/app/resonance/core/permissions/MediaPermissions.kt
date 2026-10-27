package app.resonance.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

enum class AccessLevel {
    /** Every file of this kind is visible. */
    FULL,

    /** Only files the user picked in the system dialog are visible (Android 14+, video only). */
    PARTIAL,
    NONE
}

data class MediaAccess(
    val audio: AccessLevel,
    val video: AccessLevel
) {
    val canReadAnything: Boolean get() = audio != AccessLevel.NONE || video != AccessLevel.NONE
    val isFull: Boolean get() = audio == AccessLevel.FULL && video == AccessLevel.FULL

    companion object {
        val NONE = MediaAccess(AccessLevel.NONE, AccessLevel.NONE)
    }
}

/**
 * Which runtime permissions the library needs on a given API level, and how a set of grants
 * translates into access. Pure logic over (sdk, isGranted) so it is unit-testable without a device.
 */
object MediaPermissions {
    /** Permissions to request together from the library permission prompt. */
    fun toRequest(sdkInt: Int): List<String> = when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )

        sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO
        )

        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun evaluate(sdkInt: Int, isGranted: (String) -> Boolean): MediaAccess {
        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            val level = if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) AccessLevel.FULL else AccessLevel.NONE
            return MediaAccess(level, level)
        }
        val audio = if (isGranted(Manifest.permission.READ_MEDIA_AUDIO)) AccessLevel.FULL else AccessLevel.NONE
        val video = when {
            isGranted(Manifest.permission.READ_MEDIA_VIDEO) -> AccessLevel.FULL

            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> AccessLevel.PARTIAL

            else -> AccessLevel.NONE
        }
        return MediaAccess(audio, video)
    }
}

/** Reads the current grants. Cheap; call it whenever access may have changed (e.g. on resume). */
class MediaAccessChecker @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun current(): MediaAccess = MediaPermissions.evaluate(Build.VERSION.SDK_INT) { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
