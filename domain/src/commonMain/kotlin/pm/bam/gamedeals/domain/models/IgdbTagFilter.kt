package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * An AND-combined tag query for discovery (epic #307). Each non-empty id list contributes one
 * APICalypse `<dimension> = [ids]` clause ("contains ALL" — AND within a dimension); the clauses
 * are joined with `&` (AND across dimensions). An empty filter matches nothing — callers must not
 * run a discovery query for it (it would otherwise return the whole catalogue).
 */
@Immutable
data class IgdbTagFilter(
    val genreIds: ImmutableList<Long> = persistentListOf(),
    val themeIds: ImmutableList<Long> = persistentListOf(),
    val gameModeIds: ImmutableList<Long> = persistentListOf(),
    val perspectiveIds: ImmutableList<Long> = persistentListOf(),
    val keywordIds: ImmutableList<Long> = persistentListOf(),
) {
    fun isEmpty(): Boolean =
        genreIds.isEmpty() && themeIds.isEmpty() && gameModeIds.isEmpty() &&
            perspectiveIds.isEmpty() && keywordIds.isEmpty()
}
