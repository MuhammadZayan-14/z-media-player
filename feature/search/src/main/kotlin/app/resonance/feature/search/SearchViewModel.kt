package app.resonance.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.resonance.core.domain.playback.PlaybackController
import app.resonance.core.domain.repository.FavoritesRepository
import app.resonance.core.domain.usecase.SearchLibraryUseCase
import app.resonance.core.domain.usecase.TrackCommands
import app.resonance.core.model.MediaFile
import app.resonance.core.model.SearchFilter
import app.resonance.core.model.SearchResults
import app.resonance.core.ui.TrackCommandHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val results: SearchResults = SearchResults.EMPTY,
    /** True once results for the current (debounced) query have arrived. */
    val hasSearched: Boolean = false,
    val favoriteIds: Set<Long> = emptySet(),
    val currentMediaId: Long? = null
)

private data class Request(val query: String, val filter: SearchFilter)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    search: SearchLibraryUseCase,
    favorites: FavoritesRepository,
    controller: PlaybackController,
    private val commands: TrackCommands
) : ViewModel(), TrackCommandHandler {

    // Saved so the query and filter survive process death.
    private val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, SearchFilter.ALL)

    private val results = combine(query, filter, ::Request)
        .debounce { if (it.query.isBlank()) 0L else DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            search(request.query, request.filter).map { request to it }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        filter,
        results,
        favorites.favoriteIds(),
        controller.state.map { it.current?.libraryId }.distinctUntilChanged()
    ) { currentQuery, currentFilter, (request, results), favoriteIds, currentId ->
        SearchUiState(
            query = currentQuery,
            filter = currentFilter,
            results = results,
            hasSearched = request.query.isNotBlank(),
            favoriteIds = favoriteIds,
            currentMediaId = currentId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun onFilterChange(value: SearchFilter) {
        savedStateHandle[KEY_FILTER] = value
    }

    /** Plays the song list of the current results, starting at [index]. */
    fun playSong(index: Int) = commands.playFrom(uiState.value.results.songs, index)

    override fun playNext(track: MediaFile) = commands.playNext(track)

    override fun addToQueue(track: MediaFile) = commands.addToQueue(track)

    override fun toggleFavorite(track: MediaFile) {
        viewModelScope.launch { commands.toggleFavorite(track) }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_FILTER = "filter"
        const val DEBOUNCE_MS = 250L
    }
}
