package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/**
 * Backs the Ignored-games manage sub-screen (epic #272, P3.3 #281). Observes the auth-gated, Room-backed ignore
 * id set so the list reacts to login/logout and to in-place changes while the screen is open. Full entries
 * (title/artwork) come from the remote-as-truth reconcile ([IgnoredRepository.getIgnored]); they're cached by
 * id and re-filtered against the live set. [onUnignore] is **remote-first** (via [IgnoredRepository.toggleIgnored]);
 * on success the id leaves the Room set and the observer drops the row (the game reappears in Deals/Search, #280).
 */
internal class IgnoredViewModel(
    private val ignoredRepository: IgnoredRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<GameListState>
        field = MutableStateFlow(GameListState(loading = true))

    /** Title/artwork lookup by gameId, filled by the reconcile; the displayed list filters this by the live id set. */
    private var entries: Map<String, GameListItem> = emptyMap()

    init {
        viewModelScope.launch {
            ignoredRepository.observeIgnoredIds().collect { ids ->
                if (ids.isEmpty()) {
                    entries = emptyMap()
                    uiState.update { GameListState(loading = false) }
                    return@collect
                }
                // Fetch full entries only when the cache doesn't already cover the live set (login, or a game
                // added elsewhere). A removal just re-filters the cache below — no network call.
                if (!entries.keys.containsAll(ids)) {
                    if (entries.isEmpty()) uiState.update { it.copy(loading = true) }
                    entries = runCatching { ignoredRepository.getIgnored() }
                        .getOrElse { error(logger, it); emptyList() }
                        .associate { it.gameId to GameListItem(it.gameId, it.title, it.artwork.thumbnail) }
                }
                uiState.update {
                    it.copy(loading = false, items = entries.values.filter { item -> item.gameId in ids }.toImmutableList())
                }
            }
        }
    }

    /** Remote-first un-ignore; the Room delete flows back through [observeIgnoredIds] and drops the row. */
    fun onUnignore(gameId: String) {
        viewModelScope.launch {
            runCatching { ignoredRepository.toggleIgnored(gameId) }.onFailure { error(logger, it) }
        }
    }
}
