package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * One game in a tag-discovery result page (epic #307). Games are found on IGDB (the only catalogue
 * queryable by tag) but only those that resolve to an **ITAD-tracked** game are surfaced — games with
 * no Steam app id ("untracked") and Steam games ITAD doesn't track ("Steam-only") are filtered out
 * upstream, so every result carries an ITAD [gameId] and opens the in-app peek sheet / Game Page.
 *
 * [price] is null when the game is tracked but has no *current* deal (the row shows "No current deal"
 * and the Game Page shows the historical low).
 */
@Immutable
data class TagDiscoveryResult(
    val igdbId: Long,
    val gameId: String,
    val title: String,
    val coverImageUrl: String?,
    val price: BundleGamePrice?,
)
