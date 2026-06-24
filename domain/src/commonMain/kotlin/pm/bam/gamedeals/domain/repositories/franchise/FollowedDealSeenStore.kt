package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage

/**
 * Persists the set of followed-franchise deals already surfaced to the OS tray, so the background
 * [FollowedFranchiseChecker] doesn't re-notify the same deal on every poll. Backed by [Storage] (the same
 * `SETTINGS_QUALIFIER` store as [SurfacedNotificationStore][pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStore]).
 *
 * Each signature is `"$itadGameId@$bestPriceValue"`: keying on the *price* (not just the game) means a deal
 * that ends and later returns — or that drops further — produces a new signature and re-alerts, while a
 * still-running deal at the same price stays suppressed. The set is replaced (pruned to the currently
 * on-sale signatures) each poll rather than appended to, so it can't grow unbounded.
 */
interface FollowedDealSeenStore {
    suspend fun get(): Set<String>
    suspend fun replace(signatures: Set<String>)
}

internal const val FOLLOWED_DEAL_SEEN_KEY = "followed_deal_seen_signatures"

internal class FollowedDealSeenStoreImpl(
    private val storage: Storage,
) : FollowedDealSeenStore {

    override suspend fun get(): Set<String> =
        runCatching { storage.getNullable(FOLLOWED_DEAL_SEEN_KEY, SetSerializer(String.serializer())) }
            .getOrNull() ?: emptySet()

    override suspend fun replace(signatures: Set<String>) {
        storage.save(FOLLOWED_DEAL_SEEN_KEY, signatures, SetSerializer(String.serializer()))
    }
}
