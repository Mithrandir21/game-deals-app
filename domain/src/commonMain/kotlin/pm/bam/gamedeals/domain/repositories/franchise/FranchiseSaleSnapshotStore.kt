package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.serialization.builtins.ListSerializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.FranchiseSaleGame

/**
 * Persists the last computed set of games on sale across the user's followed franchises (#7 notification
 * revamp). The background [FollowedFranchiseChecker] writes it every poll; the Followed-series screen reads
 * it to surface "current franchise sales" instantly (a pull-to-refresh recomputes + rewrites it). Backed by
 * [Storage] (the same `SETTINGS_QUALIFIER` store as [FollowedDealSeenStore]). Replaced wholesale each write.
 */
interface FranchiseSaleSnapshotStore {
    suspend fun get(): List<FranchiseSaleGame>
    suspend fun replace(games: List<FranchiseSaleGame>)
}

internal const val FRANCHISE_SALE_SNAPSHOT_KEY = "followed_franchise_sale_snapshot"

internal class FranchiseSaleSnapshotStoreImpl(
    private val storage: Storage,
) : FranchiseSaleSnapshotStore {

    override suspend fun get(): List<FranchiseSaleGame> =
        runCatching { storage.getNullable(FRANCHISE_SALE_SNAPSHOT_KEY, ListSerializer(FranchiseSaleGame.serializer())) }
            .getOrNull() ?: emptyList()

    override suspend fun replace(games: List<FranchiseSaleGame>) {
        storage.save(FRANCHISE_SALE_SNAPSHOT_KEY, games, ListSerializer(FranchiseSaleGame.serializer()))
    }
}
