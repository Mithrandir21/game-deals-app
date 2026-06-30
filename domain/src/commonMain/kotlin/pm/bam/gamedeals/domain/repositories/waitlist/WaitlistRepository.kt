package pm.bam.gamedeals.domain.repositories.waitlist

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.WaitlistGameIdEntry
import pm.bam.gamedeals.domain.db.dao.WaitlistDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's ITAD waitlist (epic #219). Replaces the removed local Favourites (Phase 3); the heart is
 * login-gated and writes here. Mirrors the old `FavouritesRepository` shape so call sites swap cleanly.
 *
 * Backed by [ItadAccountSource] (remote) over a Room-persisted **gameId set** ([WaitlistDao]) for reactive
 * UI (ITAD caching strategy, Phase 7a, #268): the id set survives process death, so the heart state is
 * correct **instantly on cold start and offline** instead of empty-until-refresh. [getWaitlist] is the
 * remote-as-truth reconcile (replaces the cached id set); [toggleWaitlist] is **remote-first** (await the
 * ITAD add/remove, then update Room) so Room never holds an unconfirmed edit and the reconcile is always
 * safe. The observed set is auth-gated — empty whenever logged out — and the rows are cleared on logout.
 * All writes are login-gated.
 */
interface WaitlistRepository {
    fun observeWaitlistIds(): Flow<ImmutableSet<String>>
    fun observeIsWaitlisted(gameId: String): Flow<Boolean>
    suspend fun getWaitlist(): List<WaitlistEntry>

    /**
     * The enriched + priced waitlist snapshot for the dashboard list. Emits the persisted cache first
     * (instant/offline render) and then each [refreshWaitlistDisplay] result. Null until a snapshot exists.
     * Callers still combine this with [observeWaitlistIds] to drop just-removed rows without a refetch.
     */
    fun observeWaitlistDisplay(): Flow<WaitlistDisplaySnapshot?>

    /**
     * Reconciles the waitlist from remote (also refreshing the id set) and merges a batched price lookup
     * into the enriched display snapshot, persisting it. Prices are best-effort — a price failure yields
     * entry-only rows rather than blanking the list. Throws if the waitlist fetch itself fails (the caller
     * keeps showing the cached snapshot).
     */
    suspend fun refreshWaitlistDisplay()

    /** Wipes the locally-cached id set + display snapshot (no remote call) — used to clear the row on logout. */
    suspend fun clearLocal()

    /**
     * Adds/removes [gameId] on the user's ITAD waitlist, returning [RepoUpdateResult]. When logged
     * out this is a no-op and returns [RepoUpdateResult.NOT_LOGGED_IN] so the UI can route the user
     * to sign in (the heart is login-gated — there is no local waitlist).
     */
    suspend fun toggleWaitlist(gameId: String): RepoUpdateResult
}

/** Chunk size for the batched `/games/prices/v3` lookup, to stay under ITAD's per-request id cap. */
private const val PRICE_BATCH_SIZE = 100

internal class WaitlistRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val waitlistDao: WaitlistDao,
    private val analytics: Analytics,
    private val dealsSource: DealsSource,
    private val regionRepository: RegionRepository,
    private val displayStore: WaitlistDisplayStore,
    private val clock: Clock,
) : WaitlistRepository {

    // Hot snapshot of the enriched list, seeded lazily from persistent storage on first collection.
    private val displayFlow = MutableStateFlow<WaitlistDisplaySnapshot?>(null)

    // Auth-gated so the persisted id set is never surfaced while logged out (it is also cleared on logout,
    // but this guards the window before a reconcile and any cross-account switch).
    override fun observeWaitlistIds(): Flow<ImmutableSet<String>> =
        combine(authTokenStore.observeAuthState(), waitlistDao.observeAll()) { authState, rows ->
            if (authState is AuthState.LoggedIn) rows.map { it.gameId }.toImmutableSet() else persistentSetOf()
        }

    override fun observeIsWaitlisted(gameId: String): Flow<Boolean> =
        observeWaitlistIds().map { gameId in it }

    override fun observeWaitlistDisplay(): Flow<WaitlistDisplaySnapshot?> =
        displayFlow.onStart { if (displayFlow.value == null) displayFlow.value = displayStore.get() }

    override suspend fun getWaitlist(): List<WaitlistEntry> {
        if (!loggedIn()) {
            waitlistDao.clear()
            return emptyList()
        }
        val entries = accountSource.getWaitlist()
        waitlistDao.replaceAll(entries.map { it.gameId })
        return entries
    }

    override suspend fun refreshWaitlistDisplay() {
        // Reuses the reconcile (clears the dao + returns empty when logged out, else replaces the id set).
        val entries = getWaitlist()
        if (entries.isEmpty()) {
            // Logged out or an empty waitlist — wipe the display cache so cold start isn't stale.
            displayStore.clear()
            displayFlow.value = WaitlistDisplaySnapshot(regionCode = currentRegion(), refreshedAtEpochMs = clock.nowMillis())
            return
        }
        val region = currentRegion()
        val prices = fetchPrices(entries.map { it.gameId })
        val items = entries.map { it.toDisplayItem(prices[it.gameId]) }
        val snapshot = WaitlistDisplaySnapshot(items = items, regionCode = region, refreshedAtEpochMs = clock.nowMillis())
        displayStore.replace(snapshot)
        displayFlow.value = snapshot
    }

    override suspend fun clearLocal() {
        waitlistDao.clear()
        displayStore.clear()
        displayFlow.value = null
    }

    private suspend fun currentRegion(): String = regionRepository.getSelectedCountryCode()

    // Best-effort + chunked: ITAD's /games/prices/v3 takes an array of ids; chunk to stay under any cap and
    // tolerate a failed chunk (those games simply render price-less) rather than blanking the whole list.
    private suspend fun fetchPrices(gameIds: List<String>): Map<String, BundleGamePrice> =
        gameIds.chunked(PRICE_BATCH_SIZE)
            .flatMap { chunk -> runCatching { dealsSource.fetchBundleGamePrices(chunk) }.getOrDefault(emptyList()) }
            .associateBy { it.gameId }

    private fun WaitlistEntry.toDisplayItem(price: BundleGamePrice?): WaitlistDisplayItem =
        WaitlistDisplayItem(
            gameId = gameId,
            title = title,
            artwork = artwork,
            type = type,
            addedEpochMs = addedEpochMs,
            bestPriceDenominated = price?.bestPriceDenominated,
            bestRegularDenominated = price?.bestRegularDenominated,
            bestShopName = price?.bestShopName,
            discountPercent = price?.bestCutPercent,
            bestPriceValue = price?.bestPriceValue,
            historicalLowValue = price?.historicalLowValue,
            hasVoucher = price?.bestHasVoucher ?: false,
            isNewHistoricalLow = price?.bestIsNewHistoricalLow ?: false,
            isStoreLow = price?.bestIsStoreLow ?: false,
        )

    override suspend fun toggleWaitlist(gameId: String): RepoUpdateResult {
        if (!loggedIn()) return RepoUpdateResult.NOT_LOGGED_IN
        // Remote-first: confirm the ITAD write before mutating Room, so the cache can't drift from remote.
        if (waitlistDao.contains(gameId)) {
            accountSource.removeFromWaitlist(gameId)
            waitlistDao.delete(gameId)
            // Recorded only after the remote+local write succeeds, so failed toggles aren't counted. The base
            // props (environment/app_version) are merged by the Analytics impl.
            analytics.capture(AnalyticsEvents.WAITLIST_REMOVED, mapOf("game_id" to gameId))
        } else {
            accountSource.addToWaitlist(gameId)
            waitlistDao.add(WaitlistGameIdEntry(gameId))
            analytics.capture(AnalyticsEvents.WAITLIST_ADDED, mapOf("game_id" to gameId))
        }
        return RepoUpdateResult.UPDATED
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}
