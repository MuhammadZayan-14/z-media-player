package app.resonance.feature.video.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.model.LibraryScanState
import app.resonance.core.model.MediaFile
import app.resonance.core.model.VideoProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VideoLibraryUiState(
    val isLoading: Boolean = true,
    val videos: List<MediaFile> = emptyList(),
    val progress: Map<Long, VideoProgress> = emptyMap(),
    val scanState: LibraryScanState = LibraryScanState.Idle
) {
    val isRefreshing: Boolean get() = scanState is LibraryScanState.Scanning
}

@HiltViewModel
class VideoLibraryViewModel @Inject constructor(
    private val library: MediaLibraryRepository,
    progress: VideoProgressRepository
) : ViewModel() {

    val uiState: StateFlow<VideoLibraryUiState> =
        combine(library.videos(), progress.inProgress(), library.scanState) { videos, inProgress, scan ->
            VideoLibraryUiState(
                isLoading = false,
                videos = videos,
                progress = inProgress.associateBy { it.mediaId },
                scanState = scan
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VideoLibraryUiState())

    fun refresh() {
        viewModelScope.launch { library.refresh(full = true) }
    }

    fun onAccessChanged() {
        viewModelScope.launch { library.refresh(full = false) }
    }
}
