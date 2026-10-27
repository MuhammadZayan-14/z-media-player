package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.HistoryRepository
import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.domain.repository.VideoProgressRepository
import app.resonance.core.model.HomeFeed
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetHomeFeedUseCase @Inject constructor(
    private val library: MediaLibraryRepository,
    private val history: HistoryRepository,
    private val videoProgress: VideoProgressRepository
) {
    operator fun invoke(sectionSize: Int = DEFAULT_SECTION_SIZE): Flow<HomeFeed> {
        val continueWatching = combine(library.videos(), videoProgress.inProgress()) { videos, progress ->
            val byId = videos.associateBy { it.id }
            progress.mapNotNull { p -> byId[p.mediaId]?.let { it to p } }.take(sectionSize)
        }
        return combine(
            history.recentlyPlayed(sectionSize),
            library.recentlyAdded(sectionSize),
            history.mostPlayed(sectionSize),
            continueWatching
        ) { recent, added, most, watching ->
            HomeFeed(
                recentlyPlayed = recent,
                recentlyAdded = added,
                mostPlayed = most,
                continueWatching = watching
            )
        }
    }

    companion object {
        const val DEFAULT_SECTION_SIZE = 12
    }
}
