package app.resonance.core.domain.usecase

import app.resonance.core.domain.repository.MediaLibraryRepository
import app.resonance.core.model.SearchFilter
import app.resonance.core.model.SearchResults
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SearchLibraryUseCase @Inject constructor(
    private val library: MediaLibraryRepository
) {
    operator fun invoke(query: String, filter: SearchFilter): Flow<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return flowOf(SearchResults.EMPTY)
        val limit = if (filter == SearchFilter.ALL) PREVIEW_LIMIT else FULL_LIMIT
        return library.search(trimmed, limit).map { it.restrictTo(filter) }
    }

    private fun SearchResults.restrictTo(filter: SearchFilter): SearchResults = when (filter) {
        SearchFilter.ALL -> this
        SearchFilter.SONGS -> SearchResults(songs = songs)
        SearchFilter.ALBUMS -> SearchResults(albums = albums)
        SearchFilter.ARTISTS -> SearchResults(artists = artists)
        SearchFilter.VIDEOS -> SearchResults(videos = videos)
    }

    companion object {
        const val MIN_QUERY_LENGTH = 1
        const val PREVIEW_LIMIT = 8
        const val FULL_LIMIT = 200
    }
}
