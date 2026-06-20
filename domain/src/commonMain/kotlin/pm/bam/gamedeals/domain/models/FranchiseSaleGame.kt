package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * One game currently on sale within a followed franchise (#7 notification revamp). Computed client-side by
 * the [FollowedFranchiseChecker][pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker]
 * (IGDB→ITAD→price) and persisted as a snapshot so the Followed-series screen can surface "current
 * franchise sales" instantly (without re-running the expensive pipeline on every open).
 *
 * [signature] keys the background-alert dedup on the *price* (`itadGameId@priceValue`): a deeper or
 * returned deal produces a new signature and re-alerts, while a still-running deal stays suppressed.
 */
@Immutable
@Serializable
data class FranchiseSaleGame(
    val franchiseId: Long,
    val franchiseName: String,
    val igdbGameId: Long,
    val itadGameId: String,
    val title: String,
    val cutPercent: Int,
    val priceValue: Double,
    val priceDenominated: String,
) {
    val signature: String get() = "$itadGameId@$priceValue"
}
