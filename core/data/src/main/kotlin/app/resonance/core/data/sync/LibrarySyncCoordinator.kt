package app.resonance.core.data.sync

import app.resonance.core.common.ApplicationScope
import app.resonance.core.data.mediastore.MediaStoreChanges
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Drives automatic syncs: one when the app comes to the foreground, and debounced ones while it
 * stays there and MediaStore reports changes (a download finishing, a file deleted elsewhere).
 * Observing stops in the background; the next foreground sync catches up incrementally.
 */
@Singleton
class LibrarySyncCoordinator @Inject constructor(
    private val synchronizer: LibrarySynchronizer,
    private val changes: MediaStoreChanges,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private var job: Job? = null

    @OptIn(FlowPreview::class)
    fun onForeground() {
        if (job?.isActive == true) return
        job = scope.launch {
            synchronizer.sync(full = false)
            changes.observe()
                .debounce(CHANGE_DEBOUNCE_MS)
                .collect { synchronizer.sync(full = false) }
        }
    }

    fun onBackground() {
        job?.cancel()
        job = null
    }

    private companion object {
        /** MediaStore fires bursts of notifications while files are copied in. */
        const val CHANGE_DEBOUNCE_MS = 2_000L
    }
}
