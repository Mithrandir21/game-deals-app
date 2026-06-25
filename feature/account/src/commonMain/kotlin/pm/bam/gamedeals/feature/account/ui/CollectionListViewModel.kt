package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/**
 * Backs the Collection sub-screen (#274/#275). Observes the auth-gated, Room-backed collection id set so the
 * list reacts to login/logout and to in-place removals while the screen is open. Full entries (title/artwork)
 * come from the remote-as-truth reconcile ([CollectionRepository.getCollection]); they're cached by id and
 * re-filtered against the live set, so a removal needs no refetch and only a login (or a game added elsewhere)
 * triggers a network fetch.
 */
internal class CollectionListViewModel(
    private val collectionRepository: CollectionRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<GameListState>
        field = MutableStateFlow(GameListState(loading = true))

    /** Title/artwork lookup by gameId, filled by the reconcile; the displayed list filters this by the live id set. */
    private var entries: Map<String, GameListItem> = emptyMap()

    init {
        viewModelScope.launch {
            collectionRepository.observeCollectionIds().collect { ids ->
                if (ids.isEmpty()) {
                    entries = emptyMap()
                    uiState.update { GameListState(loading = false) }
                    return@collect
                }
                // Fetch full entries only when the cache doesn't already cover the live set (login, or a game
                // added elsewhere). A removal just re-filters the cache below — no network call.
                if (!entries.keys.containsAll(ids)) {
                    if (entries.isEmpty()) uiState.update { it.copy(loading = true) }
                    entries = runCatching { collectionRepository.getCollection() }
                        .getOrElse { error(logger, it); emptyList() }
                        .associate { it.gameId to GameListItem(it.gameId, it.title, it.artwork.thumbnail) }
                }
                uiState.update {
                    it.copy(loading = false, items = entries.values.filter { item -> item.gameId in ids }.toImmutableList())
                }
            }
        }
    }
}
