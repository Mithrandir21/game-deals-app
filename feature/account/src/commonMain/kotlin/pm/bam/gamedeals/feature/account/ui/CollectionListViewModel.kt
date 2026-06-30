package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/** Sort orders for the (lightweight, price-less) Collection list. */
internal enum class CollectionSort {
    RECENTLY_ADDED, TITLE_AZ;

    fun comparator(): Comparator<CollectionRowUi> = when (this) {
        RECENTLY_ADDED -> compareByDescending<CollectionRowUi> { it.addedEpochMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() }
        TITLE_AZ -> compareBy { it.title.lowercase() }
    }
}

@Immutable
internal data class CollectionRowUi(
    val gameId: String,
    val title: String,
    val imageUrl: String?,
    val addedEpochMs: Long?,
    val type: String?,
)

@Immutable
internal data class CollectionUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val sort: CollectionSort = CollectionSort.RECENTLY_ADDED,
    val rows: ImmutableList<CollectionRowUi> = persistentListOf(),
)

/**
 * Backs the Collection sub-screen. Collection is games the user owns, so it diverges from the Waitlist: no
 * live price — just title/art + "Added X ago" + type, rendered cache-first from the enriched snapshot
 * ([CollectionRepository.observeCollectionDisplay]) and filtered live by the auth-gated id set so a removal
 * vanishes with no refetch. A refresh runs on open. Sorting is in-memory.
 */
internal class CollectionListViewModel(
    private val collectionRepository: CollectionRepository,
    gamesRepository: GamesRepository,
    storesRepository: StoresRepository,
    waitlistRepository: WaitlistRepository,
    ignoredRepository: IgnoredRepository,
    dealShareTextBuilder: DealShareTextBuilder,
    private val logger: Logger,
) : ViewModel() {

    /** Tapping a row opens the shared game-centric peek sheet (same as Home/Deals/Discover). */
    val peek = GamePeekDelegate(
        viewModelScope, gamesRepository, storesRepository, waitlistRepository,
        collectionRepository, ignoredRepository, dealShareTextBuilder, logger,
    )

    val uiState: StateFlow<CollectionUiState>
        field = MutableStateFlow(CollectionUiState(loading = true))

    private val sort = MutableStateFlow(CollectionSort.RECENTLY_ADDED)

    // viewModelScope launches on the Main dispatcher (single-threaded), so a plain flag suffices.
    private var refreshInFlight = false

    init {
        refresh()
        viewModelScope.launch {
            combine(
                collectionRepository.observeCollectionDisplay(),
                collectionRepository.observeCollectionIds(),
                sort,
            ) { snapshot, ids, sortOrder ->
                Content(snapshot, ids.toSet(), sortOrder)
            }.collect { content ->
                // A game added elsewhere appears in the id set before the snapshot — pull a fresh one once.
                if (content.snapshot != null && !content.snapshotCoversIds()) refresh()
                uiState.update {
                    it.copy(
                        loading = content.ids.isNotEmpty() && content.snapshot == null,
                        sort = content.sort,
                        rows = content.rows(),
                    )
                }
            }
        }
    }

    fun setSort(value: CollectionSort) {
        sort.value = value
    }

    private fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            uiState.update { it.copy(refreshing = true) }
            val failed = runCatching { collectionRepository.refreshCollectionDisplay() }
                .onFailure { error(logger, it) }
                .isFailure
            // On success the snapshot flow drives `loading` false via combine; clear the spinner here for a
            // failed cold load (no cache) so it doesn't spin forever.
            uiState.update { it.copy(refreshing = false, refreshFailed = failed, loading = if (failed) false else it.loading) }
            refreshInFlight = false
        }
    }

    private data class Content(
        val snapshot: List<CollectionEntry>?,
        val ids: Set<String>,
        val sort: CollectionSort,
    ) {
        fun snapshotCoversIds(): Boolean =
            snapshot != null && snapshot.map { it.gameId }.toSet().containsAll(ids)

        fun rows(): ImmutableList<CollectionRowUi> {
            if (ids.isEmpty() || snapshot == null) return persistentListOf()
            return snapshot
                .filter { it.gameId in ids }
                .map { CollectionRowUi(it.gameId, it.title, it.artwork.thumbnail, it.addedEpochMs, it.type) }
                .sortedWith(sort.comparator())
                .toImmutableList()
        }
    }
}
