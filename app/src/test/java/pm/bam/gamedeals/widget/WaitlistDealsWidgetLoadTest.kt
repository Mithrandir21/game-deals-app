package pm.bam.gamedeals.widget

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import java.io.IOException

/** What the Waitlist deals widget loads, and when it spends an ITAD refresh ([WaitlistDealsWidget.load]). */
class WaitlistDealsWidgetLoadTest {

    // Only used to download thumbnails, and every fixture has none.
    private val context: Context = mockk()

    private val authState = MutableStateFlow<AuthState>(AuthState.LoggedIn("user"))
    private val snapshot = MutableStateFlow<WaitlistDisplaySnapshot?>(null)
    private val waitlistIds = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())

    private val authTokenStore: AuthTokenStore = mockk { every { observeAuthState() } returns authState }
    private val waitlistRepository: WaitlistRepository = mockk(relaxed = true) {
        every { observeWaitlistDisplay() } returns snapshot
        every { observeWaitlistIds() } returns waitlistIds
    }
    private val regionRepository: RegionRepository = mockk { coEvery { getSelectedCountryCode() } returns "US" }
    private val clock: Clock = Clock { NOW }

    @Before
    fun setUp() {
        WaitlistDealsWidget.refreshedForIds = null
        startKoin {
            modules(module {
                single { authTokenStore }
                single { waitlistRepository }
                single { regionRepository }
                single { clock }
                single<Logger> { mockk(relaxed = true) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private suspend fun load() = WaitlistDealsWidget().load(context)

    private fun item(id: String, cut: Int? = 50, atLow: Boolean = false, regular: String = "\$10") = WaitlistDisplayItem(
        gameId = id,
        title = "Game $id",
        bestPriceDenominated = cut?.let { "\$5" },
        bestRegularDenominated = regular,
        bestShopName = "Steam",
        discountPercent = cut,
        bestPriceValue = 5.0,
        historicalLowValue = if (atLow) 5.0 else 3.0,
    )

    private fun snapshotOf(vararg items: WaitlistDisplayItem, region: String = "US", refreshedAt: Long = NOW) =
        WaitlistDisplaySnapshot(items = items.toList(), regionCode = region, refreshedAtEpochMs = refreshedAt)

    /** Caches [items] as the priced snapshot and puts exactly those games on the waitlist. */
    private fun cache(vararg items: WaitlistDisplayItem, region: String = "US", refreshedAt: Long = NOW) {
        snapshot.value = snapshotOf(*items, region = region, refreshedAt = refreshedAt)
        waitlistIds.value = items.map { it.gameId }.toImmutableSet()
    }

    private fun refreshFails() {
        coEvery { waitlistRepository.refreshWaitlistDisplay() } throws IOException("offline")
    }

    private fun WaitlistDealsState.dealIds() = (this as WaitlistDealsState.Loaded).deals.map { it.gameId }

    @Test
    fun signed_out_shows_the_sign_in_state_without_calling_itad() = runTest {
        authState.value = AuthState.LoggedOut
        cache(item("a"))

        assertEquals(WaitlistDealsState.SignedOut, load())
        coVerify(exactly = 0) { waitlistRepository.refreshWaitlistDisplay() }
    }

    @Test
    fun a_fresh_cache_shows_games_on_sale_biggest_cut_first_without_refreshing() = runTest {
        cache(item("small", cut = 20), item("big", cut = 75), item("noDeal", cut = null), item("noCut", cut = 0))

        assertEquals(listOf("big", "small"), load().dealIds())
        coVerify(exactly = 0) { waitlistRepository.refreshWaitlistDisplay() }
    }

    @Test
    fun a_deal_carries_its_prices_shop_and_all_time_low() = runTest {
        cache(item("a", cut = 50, atLow = true))

        val deal = (load() as WaitlistDealsState.Loaded).deals.single()

        assertEquals("Game a", deal.title)
        assertEquals("\$5", deal.price)
        assertEquals("\$10", deal.regular)
        assertEquals(50, deal.discountPercent)
        assertEquals("Steam", deal.shop)
        assertTrue(deal.atHistoricalLow)
    }

    @Test
    fun a_usual_price_equal_to_the_deal_price_is_left_out() = runTest {
        cache(item("a", regular = "\$5"))

        assertNull((load() as WaitlistDealsState.Loaded).deals.single().regular)
    }

    @Test
    fun at_most_the_widget_row_limit_is_loaded() = runTest {
        cache(*(1..10).map { item("g$it") }.toTypedArray())

        assertEquals(WIDGET_MAX_ROWS, load().dealIds().size)
    }

    @Test
    fun an_empty_waitlist_shows_the_empty_state() = runTest {
        cache()

        assertEquals(WaitlistDealsState.EmptyWaitlist, load())
    }

    @Test
    fun a_game_removed_since_the_last_refresh_is_dropped() = runTest {
        cache(item("kept"), item("removed"))
        waitlistIds.value = persistentSetOf("kept")

        assertEquals(listOf("kept"), load().dealIds())
        coVerify(exactly = 0) { waitlistRepository.refreshWaitlistDisplay() }
    }

    @Test
    fun a_stale_cache_is_refreshed_before_showing() = runTest {
        cache(item("a", cut = 50), refreshedAt = NOW - WaitlistDealsWidget.STALE_AFTER_MILLIS - 1)
        coEvery { waitlistRepository.refreshWaitlistDisplay() } answers { snapshot.value = snapshotOf(item("a", cut = 80)) }

        assertEquals(80, (load() as WaitlistDealsState.Loaded).deals.single().discountPercent)
        coVerify(exactly = 1) { waitlistRepository.refreshWaitlistDisplay() }
    }

    @Test
    fun a_failed_refresh_keeps_showing_the_cache() = runTest {
        cache(item("a"), refreshedAt = NOW - WaitlistDealsWidget.STALE_AFTER_MILLIS - 1)
        refreshFails()

        assertEquals(listOf("a"), load().dealIds())
    }

    @Test
    fun prices_for_another_region_are_refreshed_and_never_shown() = runTest {
        cache(item("a"), region = "DE")
        refreshFails()

        assertEquals(emptyList<String>(), load().dealIds())
        coVerify(exactly = 1) { waitlistRepository.refreshWaitlistDisplay() }
    }

    @Test
    fun nothing_cached_is_refreshed_first() = runTest {
        waitlistIds.value = persistentSetOf("a")
        coEvery { waitlistRepository.refreshWaitlistDisplay() } answers { snapshot.value = snapshotOf(item("a")) }

        assertEquals(listOf("a"), load().dealIds())
    }

    @Test
    fun nothing_cached_and_a_failed_refresh_shows_the_error_state() = runTest {
        waitlistIds.value = persistentSetOf("a")
        refreshFails()

        assertEquals(WaitlistDealsState.Error, load())
    }

    @Test
    fun a_newly_waitlisted_game_triggers_one_refresh_per_waitlist_change() = runTest {
        // ITAD can't price "new", so it never reaches the snapshot; each redraw must not refresh again.
        cache(item("a"))
        waitlistIds.value = persistentSetOf("a", "new")

        load()
        load()
        coVerify(exactly = 1) { waitlistRepository.refreshWaitlistDisplay() }

        waitlistIds.value = persistentSetOf("a", "new", "newer")
        load()
        coVerify(exactly = 2) { waitlistRepository.refreshWaitlistDisplay() }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
