package pm.bam.gamedeals.feature.search.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.common.flatMapLatestDelayAtLeast
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.logging.Logger
import javax.inject.Inject

@Suppress("NullChecksToSafeCall")
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val logger: Logger,
    private val gamesRepository: GamesRepository
) : ViewModel() {

    // We store and react to the Query changes so that only a single search flow can exists
    private val searchParametersFlow = MutableStateFlow<SearchParameters?>(null)

    private val _resultState = MutableStateFlow<SearchData>(SearchData.Empty)
    val resultState: StateFlow<SearchData> = _resultState.asStateFlow()

    init {
        viewModelScope.launch {
            searchParametersFlow
                .flatMapLatest { dealsSearch ->
                    when (dealsSearch == null || dealsSearch.title == null) {
                        true -> flowOf(SearchData.Empty)
                        else -> flowOf(dealsSearch)
                            .onEach { _resultState.emit(SearchData.Loading) }
                            .flatMapLatestDelayAtLeast(1000) { gamesRepository.searchGames(it) }
                            .map {
                                when (it.isEmpty()) {
                                    true -> SearchData.NoResults
                                    false -> SearchData.SearchResults(it.toImmutableList())
                                }
                            }
                    }
                }
                .logFlow(logger)
                .catch { emit(SearchData.Error) }
                .collect { _resultState.emit(it) }
        }
    }

    fun searchGames(
        title: String? = null,
        lowerPrice: Int? = null,
        upperPrice: Int? = null,
        steamMinimum: Int? = null,
        exactMatch: Boolean? = null
    ) {
        val searchParameters = SearchParameters(
            title = title?.takeIf { it.isNotBlank() },
            lowerPrice = lowerPrice,
            upperPrice = upperPrice,
            steamMinRating = steamMinimum,
            exact = exactMatch
        )

        viewModelScope.launch {
            searchParametersFlow.emit(searchParameters)
        }
    }

    sealed class SearchData {
        data object Empty : SearchData()
        data object Loading : SearchData()
        data object NoResults : SearchData()
        data object Error : SearchData()

        @Immutable
        data class SearchResults(
            val searchResults: ImmutableList<Deal>
        ) : SearchData()
    }
}