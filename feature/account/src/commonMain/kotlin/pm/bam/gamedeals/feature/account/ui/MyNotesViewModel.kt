package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/** A single noted game in the "My notes" list (#283). */
@Immutable
internal data class NotesListItem(
    val gameId: String,
    val title: String,
    val boxart: String?,
    val note: String,
)

@Immutable
internal data class NotesListState(
    val loading: Boolean = false,
    val items: ImmutableList<NotesListItem> = persistentListOf(),
)

/**
 * Backs the "My notes" sub-screen (epic #272, P4.2 #283): does the remote-as-truth reconcile via
 * [NotesRepository.getNotedGames] when the screen opens (which also enriches each id-only note with its
 * game title + boxart) and exposes the entries for display. Tapping a row opens the game detail, where the
 * note can be edited or deleted inline.
 */
internal class MyNotesViewModel(
    private val notesRepository: NotesRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<NotesListState>
        field = MutableStateFlow(NotesListState(loading = true))

    init {
        viewModelScope.launch {
            val items = runCatching { notesRepository.getNotedGames() }
                .getOrElse { fatal(logger, it); emptyList() }
                .map { NotesListItem(it.gameId, it.title, it.boxart, it.note) }
            uiState.update { it.copy(loading = false, items = items.toImmutableList()) }
        }
    }
}
