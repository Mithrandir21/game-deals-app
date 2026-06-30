package pm.bam.gamedeals.domain.repositories.waitlist

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.db.cache.WaitlistGameIdEntry
import pm.bam.gamedeals.domain.db.dao.WaitlistDao
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.RecordingAnalytics
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitlistRepositoryTest {

    @Test
    fun logged_out_getWaitlist_clears_and_returns_empty() = runTest {
        val dao = FakeWaitlistDao(initial = listOf("a"))
        val repo = repo(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"))),
            FakeAuthTokenStore(access = null),
            dao,
            RecordingAnalytics(),
        )
        assertEquals(emptyList(), repo.getWaitlist())
        // Logged out: the cached id set is cleared and the observed set is empty (auth-gated).
        assertEquals(persistentSetOf(), repo.observeWaitlistIds().first())
    }

    @Test
    fun logged_out_toggle_is_a_no_op() = runTest {
        val source = FakeAccountSource()
        val repo = repo(source, FakeAuthTokenStore(access = null), FakeWaitlistDao(), RecordingAnalytics())
        assertEquals(RepoUpdateResult.NOT_LOGGED_IN, repo.toggleWaitlist("a"))
        assertTrue(source.added.isEmpty())
        assertTrue(source.removed.isEmpty())
    }

    @Test
    fun logged_in_getWaitlist_populates_the_id_cache() = runTest {
        val repo = repo(
            FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B"))),
            FakeAuthTokenStore(access = "token"),
            FakeWaitlistDao(),
            RecordingAnalytics(),
        )
        assertEquals(2, repo.getWaitlist().size)
        assertEquals(setOf("a", "b"), repo.observeWaitlistIds().first().toSet())
    }

    @Test
    fun cold_start_observe_reads_the_persisted_id_set_before_any_refresh() = runTest {
        // The id set survives process death, so the heart state is correct without calling getWaitlist.
        val repo = repo(
            FakeAccountSource(),
            FakeAuthTokenStore(access = "token"),
            FakeWaitlistDao(initial = listOf("a", "b")),
            RecordingAnalytics(),
        )
        assertEquals(setOf("a", "b"), repo.observeWaitlistIds().first().toSet())
    }

    @Test
    fun logged_in_toggle_adds_when_absent() = runTest {
        val source = FakeAccountSource()
        val analytics = RecordingAnalytics()
        val repo = repo(source, FakeAuthTokenStore(access = "token"), FakeWaitlistDao(), analytics)

        assertEquals(RepoUpdateResult.UPDATED, repo.toggleWaitlist("a"))

        assertEquals(listOf("a"), source.added)
        assertTrue(repo.observeIsWaitlisted("a").first())
        assertEquals(listOf(AnalyticsEvents.WAITLIST_ADDED), analytics.events)
    }

    @Test
    fun logged_in_toggle_removes_when_present() = runTest {
        val source = FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A")))
        val analytics = RecordingAnalytics()
        val repo = repo(source, FakeAuthTokenStore(access = "token"), FakeWaitlistDao(), analytics)
        repo.getWaitlist() // seed cache with "a"

        assertEquals(RepoUpdateResult.UPDATED, repo.toggleWaitlist("a"))

        assertEquals(listOf("a"), source.removed)
        assertFalse(repo.observeIsWaitlisted("a").first())
        assertEquals(listOf(AnalyticsEvents.WAITLIST_REMOVED), analytics.events)
    }

    @Test
    fun refresh_merges_prices_region_and_caches_snapshot() = runTest {
        val source = FakeAccountSource(waitlist = listOf(WaitlistEntry("a", "A"), WaitlistEntry("b", "B")))
        val deals = mock<DealsSource>(MockMode.autoUnit)
        everySuspend { deals.fetchBundleGamePrices(any()) } returns listOf(
            BundleGamePrice(
                gameId = "a", bestShopName = "Steam", bestPriceValue = 9.99, bestPriceDenominated = "€9.99",
                bestCutPercent = 75, bestRegularDenominated = "€39.99", historicalLowValue = 9.99,
                historicalLowDenominated = "€9.99", currency = "EUR",
            ),
        )
        val store = FakeWaitlistDisplayStore()
        val repo = repo(source, FakeAuthTokenStore("token"), FakeWaitlistDao(), dealsSource = deals, displayStore = store, region = FakeRegionRepository("DE"))

        repo.refreshWaitlistDisplay()

        val snapshot = repo.observeWaitlistDisplay().first()!!
        assertEquals(2, snapshot.items.size)
        assertEquals("DE", snapshot.regionCode)
        assertEquals(snapshot, store.saved)
        val a = snapshot.items.first { it.gameId == "a" }
        assertEquals("€9.99", a.bestPriceDenominated)
        assertEquals(75, a.discountPercent)
        assertTrue(a.isAtHistoricalLow)
        // "b" has no deal in the price result — renders price-less, not dropped.
        val b = snapshot.items.first { it.gameId == "b" }
        assertNull(b.bestPriceDenominated)
        assertFalse(b.hasDeal)
    }

    @Test
    fun refresh_when_logged_out_clears_the_display_cache() = runTest {
        val store = FakeWaitlistDisplayStore().apply {
            saved = WaitlistDisplaySnapshot(regionCode = "US", refreshedAtEpochMs = 1L)
        }
        val repo = repo(FakeAccountSource(), FakeAuthTokenStore(access = null), FakeWaitlistDao(initial = listOf("a")), displayStore = store)

        repo.refreshWaitlistDisplay()

        assertNull(store.saved)
        assertTrue(repo.observeWaitlistDisplay().first()!!.items.isEmpty())
    }

    private fun repo(
        source: ItadAccountSource,
        auth: AuthTokenStore,
        dao: WaitlistDao,
        analytics: Analytics = RecordingAnalytics(),
        dealsSource: DealsSource = mock(MockMode.autoUnit),
        region: RegionRepository = FakeRegionRepository(),
        displayStore: WaitlistDisplayStore = FakeWaitlistDisplayStore(),
        clock: Clock = Clock { 0L },
    ) = WaitlistRepositoryImpl(source, auth, dao, analytics, dealsSource, region, displayStore, clock)
}

private class FakeRegionRepository(private val code: String = "US") : RegionRepository {
    override val supportedCountries: List<Country> = listOf(Country(code, code))
    override fun observeSelectedCountry(): Flow<Country> = flowOf(Country(code, code))
    override suspend fun getSelectedCountryCode(): String = code
    override suspend fun setSelectedCountry(country: Country) = Unit
}

private class FakeWaitlistDisplayStore : WaitlistDisplayStore {
    var saved: WaitlistDisplaySnapshot? = null
    override suspend fun get(): WaitlistDisplaySnapshot? = saved
    override suspend fun replace(snapshot: WaitlistDisplaySnapshot) { saved = snapshot }
    override suspend fun clear() { saved = null }
}

internal class FakeAccountSource(
    private val waitlist: List<WaitlistEntry> = emptyList(),
    private val collection: List<CollectionEntry> = emptyList(),
    private val ignored: List<IgnoredEntry> = emptyList(),
) : ItadAccountSource {
    val added = mutableListOf<String>()
    val removed = mutableListOf<String>()

    override suspend fun getUserInfo(): ItadUser = ItadUser("user")
    override suspend fun getWaitlist(): List<WaitlistEntry> = waitlist
    override suspend fun addToWaitlist(gameId: String) { added += gameId }
    override suspend fun removeFromWaitlist(gameId: String) { removed += gameId }
    override suspend fun getCollection(): List<CollectionEntry> = collection
    override suspend fun addToCollection(gameId: String) { added += gameId }
    override suspend fun removeFromCollection(gameId: String) { removed += gameId }
    override suspend fun getNotifications(): List<ItadNotification> = emptyList()
    override suspend fun markNotificationRead(id: String) = Unit
    override suspend fun markAllNotificationsRead() = Unit
    override suspend fun getWaitlistNotificationGames(id: String): List<NotificationGame> = emptyList()
    override suspend fun getWaitlistNotificationDetail(id: String): NotificationDetail = NotificationDetail(id, emptyList())
    override suspend fun getIgnored(): List<IgnoredEntry> = ignored
    override suspend fun addToIgnored(gameId: String) { added += gameId }
    override suspend fun removeFromIgnored(gameId: String) { removed += gameId }
    override suspend fun getNotes(): List<ItadNote> = emptyList()
    override suspend fun setNote(gameId: String, note: String) = Unit
    override suspend fun removeNote(gameId: String) = Unit
}

internal class FakeAuthTokenStore(private val access: String?) : AuthTokenStore {
    override fun observeAuthState(): Flow<AuthState> =
        flowOf(if (access != null) AuthState.LoggedIn("user") else AuthState.LoggedOut)
    override suspend fun getAccessToken(): String? = access
    override suspend fun getRefreshToken(): String? = null
    override suspend fun getUsername(): String? = null
    override suspend fun getExpiresAtEpochMs(): Long = 0L
    override suspend fun getScopeVersion(): Int = 0
    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String, scopeVersion: Int) = Unit
    override suspend fun clear() = Unit
}

/** In-memory [WaitlistDao] backed by a reactive [MutableStateFlow] so `observeAll()` emits on every change. */
internal class FakeWaitlistDao(initial: List<String> = emptyList()) : WaitlistDao {
    private val rows = MutableStateFlow(initial.map { WaitlistGameIdEntry(it) })
    override fun observeAll(): Flow<List<WaitlistGameIdEntry>> = rows
    override suspend fun contains(gameId: String): Boolean = rows.value.any { it.gameId == gameId }
    override suspend fun add(vararg entries: WaitlistGameIdEntry) {
        rows.value = (rows.value + entries).distinctBy { it.gameId }
    }
    override suspend fun delete(gameId: String) { rows.value = rows.value.filterNot { it.gameId == gameId } }
    override suspend fun clear() { rows.value = emptyList() }
}
