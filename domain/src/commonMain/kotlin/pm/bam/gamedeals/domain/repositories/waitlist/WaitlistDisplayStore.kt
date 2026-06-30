package pm.bam.gamedeals.domain.repositories.waitlist

import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot

/**
 * Persists the enriched + priced waitlist snapshot so the Waitlist "buy-decision dashboard" renders
 * instantly (and offline) on open, before the network refresh completes. Backed by [Storage] (the same
 * `SETTINGS_QUALIFIER` store as [FranchiseSaleSnapshotStore][pm.bam.gamedeals.domain.repositories.franchise.FranchiseSaleSnapshotStore]).
 * Replaced wholesale on each refresh; tolerant of schema drift ([get] returns null on any read failure).
 */
interface WaitlistDisplayStore {
    suspend fun get(): WaitlistDisplaySnapshot?
    suspend fun replace(snapshot: WaitlistDisplaySnapshot)
    suspend fun clear()
}

internal const val WAITLIST_DISPLAY_SNAPSHOT_KEY = "waitlist_display_snapshot"

internal class WaitlistDisplayStoreImpl(
    private val storage: Storage,
) : WaitlistDisplayStore {

    override suspend fun get(): WaitlistDisplaySnapshot? =
        runCatching { storage.getNullable<WaitlistDisplaySnapshot>(WAITLIST_DISPLAY_SNAPSHOT_KEY) }.getOrNull()

    override suspend fun replace(snapshot: WaitlistDisplaySnapshot) {
        storage.save(WAITLIST_DISPLAY_SNAPSHOT_KEY, snapshot)
    }

    override suspend fun clear() {
        storage.remove(WAITLIST_DISPLAY_SNAPSHOT_KEY)
    }
}
