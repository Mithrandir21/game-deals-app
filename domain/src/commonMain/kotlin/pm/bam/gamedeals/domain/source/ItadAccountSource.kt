package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.WaitlistEntry

/**
 * User-scoped ITAD account facade (epic #219, Phase 2). Backed by OAuth (read+write); all calls
 * require a valid user token. The live implementation lands in `:remote:itad` (Phase 2.3, #228);
 * this domain-side port keeps callers provider-agnostic, mirroring [DealsSource].
 */
interface ItadAccountSource {
    suspend fun getUserInfo(): ItadUser

    suspend fun getWaitlist(): List<WaitlistEntry>
    suspend fun addToWaitlist(gameId: String)
    suspend fun removeFromWaitlist(gameId: String)

    suspend fun getCollection(): List<CollectionEntry>
    suspend fun addToCollection(gameId: String)
    suspend fun removeFromCollection(gameId: String)

    suspend fun getNotifications(): List<ItadNotification>
    suspend fun markNotificationRead(id: String)
    suspend fun markAllNotificationsRead()

    suspend fun getIgnored(): List<IgnoredEntry>
    suspend fun addToIgnored(gameId: String)
    suspend fun removeFromIgnored(gameId: String)

    suspend fun getNotes(): List<ItadNote>
    suspend fun setNote(gameId: String, note: String)
    suspend fun removeNote(gameId: String)
}
