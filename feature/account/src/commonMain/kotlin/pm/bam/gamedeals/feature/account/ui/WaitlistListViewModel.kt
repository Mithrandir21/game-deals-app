package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the Waitlist sub-screen (#274/#275): does the remote-as-truth reconcile via
 * [WaitlistRepository.getWaitlist] when the screen opens and exposes the entries for display.
 */
internal class WaitlistListViewModel(
    private val waitlistRepository: WaitlistRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<GameListState>
        field = MutableStateFlow(GameListState(loading = true))

    init {
        viewModelScope.launch {
            val items = runCatching { waitlistRepository.getWaitlist() }
                .getOrElse { fatal(logger, it); emptyList() }
                .map { GameListItem(it.gameId, it.title, it.artwork.thumbnail) }
            uiState.update { it.copy(loading = false, items = items.toImmutableList()) }
        }
    }
}
