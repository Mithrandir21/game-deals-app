package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A named, re-runnable Deals search the user pinned — the [query] text plus the [filter] that was active
 * when it was saved. Persisted locally via
 * [pm.bam.gamedeals.domain.repositories.search.SearchHistoryRepository].
 *
 * NOTE: [filter] embeds [DealsFilter], whose enums serialize **by Kotlin constant name** — the same
 * PERSISTED CONTRACT that governs the `deals_filter` blob applies here (never rename/remove a
 * [ProductType]/[DealFlag]/[ReleaseWindow] constant without a read-time migration).
 */
@Immutable
@Serializable
data class SavedSearch(
    val name: String,
    val query: String,
    val filter: DealsFilter = DealsFilter(),
    val addedAtMs: Long,
)
