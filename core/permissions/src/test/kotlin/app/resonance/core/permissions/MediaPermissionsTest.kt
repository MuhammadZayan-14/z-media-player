package app.resonance.core.permissions

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPermissionsTest {
    private val android10 = 29
    private val android13 = 33
    private val android14 = 34

    private fun evaluate(sdk: Int, vararg granted: String) = MediaPermissions.evaluate(sdk) { it in granted }

    @Test
    fun legacyStoragePermissionCoversBothKindsBeforeAndroid13() {
        assertEquals(listOf(READ_EXTERNAL_STORAGE), MediaPermissions.toRequest(android10))
        assertEquals(MediaAccess(AccessLevel.FULL, AccessLevel.FULL), evaluate(android10, READ_EXTERNAL_STORAGE))
        assertEquals(MediaAccess.NONE, evaluate(android10))
    }

    @Test
    fun android13RequestsGranularAudioAndVideoOnly() {
        assertEquals(listOf(READ_MEDIA_AUDIO, READ_MEDIA_VIDEO), MediaPermissions.toRequest(android13))
        assertEquals(MediaAccess(AccessLevel.FULL, AccessLevel.NONE), evaluate(android13, READ_MEDIA_AUDIO))
    }

    @Test
    fun android14SelectedVideosAreReportedAsPartialAccess() {
        assertTrue(READ_MEDIA_VISUAL_USER_SELECTED in MediaPermissions.toRequest(android14))
        val access = evaluate(android14, READ_MEDIA_AUDIO, READ_MEDIA_VISUAL_USER_SELECTED)
        assertEquals(MediaAccess(AccessLevel.FULL, AccessLevel.PARTIAL), access)
        assertTrue(access.canReadAnything)
        assertFalse(access.isFull)
    }

    @Test
    fun selectedVisualMediaIsIgnoredBeforeAndroid14() {
        assertEquals(AccessLevel.NONE, evaluate(android13, READ_MEDIA_VISUAL_USER_SELECTED).video)
    }

    @Test
    fun fullVideoGrantWinsOverPartial() {
        val access = evaluate(android14, READ_MEDIA_AUDIO, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        assertTrue(access.isFull)
    }

    @Test
    fun revokedAccessReadsNothing() {
        assertFalse(evaluate(android14).canReadAnything)
    }
}
