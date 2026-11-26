package pm.bam.gamedeals.domain.repositories.franchise

/**
 * Seeds the followed-franchise deal dedup store at follow time (#7 notification revamp). When a user newly
 * follows a franchise, the games in it that are *already* on sale are recorded as "seen" so the next
 * background poll only alerts on deals that appear *after* the follow — preventing a back-catalog
 * notification flood. (The Followed-series screen still surfaces those existing deals; they're just not
 * pushed to the OS tray.) Implemented by [FollowedFranchiseCheckerImpl]; best-effort.
 */
interface FranchiseFollowSeeder {
    suspend fun seedSeen(franchiseId: Long)
}
