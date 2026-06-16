package pm.bam.gamedeals.feature.discover.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.repositories.discovery.DISCOVERY_PAGE_SIZE
import pm.bam.gamedeals.domain.repositories.discovery.TagDiscoveryRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Drives the tag-discovery results screen (epic #307, Phase 5). Reconstructs the [IgdbTagFilter] from
 * the route args, loads the first page, and appends pages on scroll-end via offset paging — mirroring
 * [pm.bam.gamedeals.feature.deals.ui.DealsViewModel]. An empty page surfaces as [Status.EMPTY].
 */
internal class DiscoverResultsViewModel(
    private val logger: Logger,
    private val tagDiscoveryRepository: TagDiscoveryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val filter: IgdbTagFilter = savedStateHandle.toFilter()

    val uiState: StateFlow<ResultsScreenData>
        field = MutableStateFlow(ResultsScreenData())

    val events: SharedFlow<DiscoverResultsUiEvent>
        field = MutableSharedFlow<DiscoverResultsUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var appendJob: Job? = null

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        appendJob?.cancel()
        viewModelScope.launch {
            uiState.update { ResultsScreenData(status = ResultsScreenData.Status.LOADING) }
            try {
                val page = tagDiscoveryRepository.discover(filter, offset = 0)
                uiState.update {
                    ResultsScreenData(
                        status = if (page.isEmpty()) ResultsScreenData.Status.EMPTY else ResultsScreenData.Status.DATA,
                        results = page.toImmutableList(),
                        endReached = page.size < DISCOVERY_PAGE_SIZE,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fatal(logger, t) { "Failed to load tag-discovery results (filter=$filter)" }
                uiState.update { ResultsScreenData(status = ResultsScreenData.Status.ERROR) }
            }
        }
    }

    fun loadNextPage() {
        val current = uiState.value
        if (current.status != ResultsScreenData.Status.DATA || current.appending || current.endReached) return
        appendJob?.cancel()
        appendJob = viewModelScope.launch {
            uiState.update { it.copy(appending = true) }
            try {
                val page = tagDiscoveryRepository.discover(filter, offset = current.results.size)
                uiState.update { state ->
                    state.copy(
                        results = (state.results + page).toImmutableList(),
                        appending = false,
                        endReached = page.size < DISCOVERY_PAGE_SIZE,
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                fatal(logger, t) { "Failed to load more tag-discovery results" }
                uiState.update { it.copy(appending = false) }
                events.tryEmit(DiscoverResultsUiEvent.LoadMoreError)
            }
        }
    }

    fun retry() = loadFirstPage()

    data class ResultsScreenData(
        val status: Status = Status.LOADING,
        val results: ImmutableList<TagDiscoveryResult> = persistentListOf(),
        val appending: Boolean = false,
        val endReached: Boolean = false,
    ) {
        enum class Status { LOADING, ERROR, EMPTY, DATA }
    }

    sealed interface DiscoverResultsUiEvent {
        data object LoadMoreError : DiscoverResultsUiEvent
    }
}

/** Decodes the comma-joined per-dimension id args (see [pm.bam.gamedeals.common.navigation.Destination.DiscoverResults]). */
private fun SavedStateHandle.toFilter(): IgdbTagFilter = IgdbTagFilter(
    genreIds = idList("genreIds"),
    themeIds = idList("themeIds"),
    gameModeIds = idList("gameModeIds"),
    perspectiveIds = idList("perspectiveIds"),
    keywordIds = idList("keywordIds"),
)

private fun SavedStateHandle.idList(key: String): kotlinx.collections.immutable.ImmutableList<Long> =
    get<String>(key).orEmpty()
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() }
        .toImmutableList()
