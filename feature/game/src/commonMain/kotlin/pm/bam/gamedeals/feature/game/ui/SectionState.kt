package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable

/**
 * Per-facet load state for the Game Page's independently-degrading sections (epic #291 follow-up:
 * tab empty/error states).
 *
 * The page loads every facet eagerly in one flow, so on first render a facet is already [Loaded] or
 * [Error]; [Loading] is only re-entered when the user taps Retry on that facet. [Loaded] carries the
 * value — which may itself be `null`/empty when the source genuinely had nothing — so an illegal
 * "Error but with data" state is unrepresentable.
 *
 * Distinct from [GamePageViewModel.RegionalPricesState] / [GamePageViewModel.CandidatesState], which
 * carry an extra `Idle` for their lazy/on-demand loads and are intentionally left as-is.
 */
@Immutable
sealed interface SectionState<out T> {
    data object Loading : SectionState<Nothing>
    data class Loaded<out T>(val value: T) : SectionState<T>
    data object Error : SectionState<Nothing>
}
