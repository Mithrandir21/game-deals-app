package pm.bam.gamedeals.feature.search.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.flatMapLatestDelayAtLeast
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger

@Suppress("NullChecksToSafeCall")
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
internal class SearchViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository,
    private val waitlistRepository: WaitlistRepository,
    private val ignoredRepository: IgnoredRepository,
) : ViewModel() {

    val initialQuery: String? = savedStateHandle.get<String>("initialQuery")?.takeIf { it.isNotBlank() }

    // We store and react to the Query changes so that only a single search flow can exists.
    // StateFlow gives us atomic read-modify-write via update {} (L-2026-05-02-01) and
    // conflated replay-of-latest semantics, replacing the prior SharedFlow + replayCache RMW.
    private val searchParametersFlow = MutableStateFlow(
        if (initialQuery != null) SearchParameters(title = initialQuery) else SearchParameters()
    )

    val resultState: StateFlow<SearchData>
        field = MutableStateFlow<SearchData>(SearchData.Empty)

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Games on the user's ignore list — hidden from the search results (#280). */
    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Stores keyed by id, for resolving each result's cheapest-deal store name/icon (UI Improvements #251). */
    val stores: StateFlow<ImmutableMap<Int, Store>> = storesRepository.observeStores()
        .map { list -> list.associateBy { it.storeID }.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }
        .catch { emit(persistentMapOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    val events: SharedFlow<SearchUiEvent>
        field = MutableSharedFlow<SearchUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        viewModelScope.launch {
            searchParametersFlow
                .flatMapLatest { dealsSearch ->
                    when (dealsSearch.title == null && dealsSearch.lowerPrice == null && dealsSearch.upperPrice == null && dealsSearch.steamMinRating == null) {
                        true -> flowOf(SearchData.Empty)
                        else -> flowOf(dealsSearch)
                            .onEach { resultState.emit(SearchData.Loading) }
                            .flatMapLatestDelayAtLeast(1000) { gamesRepository.searchGames(it) }
                            .map { deals ->
                                when (deals.isEmpty()) {
                                    true -> SearchData.NoResults
                                    false -> SearchData.SearchResults(deals.groupByGame().toImmutableList())
                                }
                            }
                            .catch { emit(SearchData.Error) }
                    }
                }
                .logFlow(logger)
                .collect { resultState.emit(it) }
        }
    }

    fun searchGames(
        title: String? = null,
        lowerPrice: Int? = null,
        upperPrice: Int? = null,
        steamMinimum: Int? = null,
        exactMatch: Boolean? = null
    ) {
        searchParametersFlow.update { current ->
            SearchParameters(
                title = title ?: current.title,
                lowerPrice = lowerPrice ?: current.lowerPrice,
                upperPrice = upperPrice ?: current.upperPrice,
                steamMinRating = steamMinimum ?: current.steamMinRating,
                exact = exactMatch ?: current.exact
            )
        }
    }

    /** Toggle a game on/off the waitlist from an inline row heart; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(SearchUiEvent.SignInRequired)
            }
        }
    }

    sealed class SearchData {
        data object Empty : SearchData()
        data object Loading : SearchData()
        data object NoResults : SearchData()
        data object Error : SearchData()

        @Immutable
        data class SearchResults(
            val searchResults: ImmutableList<GroupedSearchResult>
        ) : SearchData()
    }

    internal sealed interface SearchUiEvent {
        /** The user tapped a waitlist heart while logged out. */
        data object SignInRequired : SearchUiEvent
    }
}