package app.resonance.core.media.service

import app.resonance.core.model.PlaybackSnapshot
import app.resonance.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogicTest {

    @Test
    fun snapshotKeepsLibraryItemsAndRebasesTheIndex() {
        val snapshot = snapshotOf(
            queueMediaIds = listOf("1", "uri:content://doc/9", "2", "3"),
            currentIndex = 2,
            positionMs = 12_345,
            repeatMode = RepeatMode.ALL,
            shuffleEnabled = true,
            speed = 1.25f
        )
        assertEquals(PlaybackSnapshot(listOf(1, 2, 3), 1, 12_345, RepeatMode.ALL, true, 1.25f), snapshot)
    }

    @Test
    fun snapshotOnAPickedDocumentResumesAtTheNextLibraryItemFromTheStart() {
        val snapshot =
            snapshotOf(listOf("1", "uri:x", "2"), currentIndex = 1, positionMs = 9_000, RepeatMode.OFF, false, 1f)
        assertEquals(1, snapshot?.currentIndex)
        assertEquals(0L, snapshot?.positionMs)
    }

    @Test
    fun snapshotOfOnlyPickedDocumentsIsNull() {
        assertNull(snapshotOf(listOf("uri:a"), 0, 0, RepeatMode.OFF, false, 1f))
        assertNull(snapshotOf(emptyList(), -1, 0, RepeatMode.OFF, false, 1f))
    }

    @Test
    fun historyCountsEachStartOnceAndEachRepeatLoop() {
        val tracker = PlayHistoryTracker()
        assertEquals(1L, tracker.onPlaying("1"))
        assertNull("pause/resume is not a new play", tracker.onPlaying("1"))
        tracker.onItemChanged() // repeat-one loop
        assertEquals(1L, tracker.onPlaying("1"))
        tracker.onItemChanged()
        assertEquals(2L, tracker.onPlaying("2"))
        tracker.onItemChanged()
        assertNull("picked documents are not recorded", tracker.onPlaying("uri:content://x"))
        assertNull(tracker.onPlaying(null))
    }

    @Test
    fun errorPolicySkipsUntilTheLimitAndResetsOnSuccess() {
        val policy = ErrorSkipPolicy(limit = 3)
        assertTrue(policy.onError(hasNext = true))
        assertTrue(policy.onError(hasNext = true))
        assertFalse("third consecutive failure stops skipping", policy.onError(hasNext = true))
        policy.onReady()
        assertTrue(policy.onError(hasNext = true))
        assertFalse("nothing to skip to", policy.onError(hasNext = false))
    }
}
