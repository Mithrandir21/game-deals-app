package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the Ignored-games manage sub-screen (epic #272, P3.3 #281): does the remote-as-truth reconcile
 * via [IgnoredRepository.getIgnored] when the screen opens and exposes the entries for display.
 * [onUnignore] is **remote-first** (via [IgnoredRepository.toggleIgnored]); on success the row is dropped
 * from the list and the game reappears in Deals/Search (#280).
 */
internal class IgnoredViewModel(
    private val ignoredRepository: IgnoredRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<GameListState>
        field = MutableStateFlow(GameListState(loading = true))

    init {
        viewModelScope.launch {
            val items = runCatching { ignoredRepository.getIgnored() }
                .getOrElse { fatal(logger, it); emptyList() }
                .map { GameListItem(it.gameId, it.title, it.boxart) }
            uiState.update { it.copy(loading = false, items = items.toImmutableList()) }
        }
    }

    fun onUnignore(gameId: String) {
        viewModelScope.launch {
            val result = runCatching { ignoredRepository.toggleIgnored(gameId) }
                .getOrElse { fatal(logger, it); return@launch }
            if (result == RepoUpdateResult.UPDATED) {
                uiState.update { state ->
                    state.copy(items = state.items.filterNot { it.gameId == gameId }.toImmutableList())
                }
            }
        }
    }
}
