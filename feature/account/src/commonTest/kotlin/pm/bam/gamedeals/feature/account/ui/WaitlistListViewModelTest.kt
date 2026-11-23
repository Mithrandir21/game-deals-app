@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.ui.share.DealShareBuilder
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.featureflags.NoOpFeatureFlags
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.utils.observeEmissions
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Waitlist dashboard renders the cached snapshot, filters by the live id set, and sorts in-memory. */
class WaitlistListViewModelTest : MainDispatcherTest() {

    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit)
    private val dealShareBuilder: DealShareBuilder = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private val linksOff = object : FeatureFlags by NoOpFeatureFlags {
        override fun isEnabled(flag: FeatureFlag) = flag != FeatureFlag.DealLinks && NoOpFeatureFlags.isEnabled(flag)
    }

    private fun viewModel(featureFlags: FeatureFlags = NoOpFeatureFlags) = WaitlistListViewModel(
        waitlistRepository, storesRepository, regionRepository,
        gamesRepository, collectionRepository, ignoredRepository, dealShareBuilder, featureFlags, logger,
    )

    private fun stubs(
        snapshot: WaitlistDisplaySnapshot?,
        ids: Set<String>,
        region: String = "US",
    ) {
        every { waitlistRepository.observeWaitlistDisplay() } returns flowOf(snapshot)
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(ids.toPersistentSet())
        // observeWaitlistIds returns an ImmutableSet; toPersistentSet() satisfies that contract.
        every { storesRepository.observeStores() } returns flowOf(emptyList())
        every { regionRepository.observeSelectedCountry() } returns flowOf(Country(region, region, "EUR"))
        // The peek delegate observes these id sets at construction (for the sheet's icon states).
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf())
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf())
    }

    private fun snapshot(region: String = "US") = WaitlistDisplaySnapshot(
        items = listOf(
            WaitlistDisplayItem(gameId = "a", title = "Alpha", addedEpochMs = 2L),
            WaitlistDisplayItem(
                gameId = "b", title = "Beta", addedEpochMs = 1L,
                bestPriceDenominated = "€5.00", bestPriceValue = 5.0, discountPercent = 50,
            ),
        ),
        regionCode = region,
    )

    @Test
    fun cached_snapshot_renders_sorted_by_recently_added() = runTest {
        stubs(snapshot(), setOf("a", "b"))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertEquals(WaitlistSort.RECENTLY_ADDED, state.sort)
        assertEquals(listOf("a", "b"), state.rows.map { it.gameId }) // addedEpochMs desc
    }

    @Test
    fun empty_id_set_yields_empty_rows() = runTest {
        stubs(snapshot(), emptySet())

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun removed_id_is_filtered_out_of_the_snapshot() = runTest {
        stubs(snapshot(), setOf("a")) // "b" is in the snapshot but no longer in the id set

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.uiState.value.rows.map { it.gameId })
    }

    @Test
    fun set_sort_reorders_by_biggest_discount() = runTest {
        stubs(snapshot(), setOf("a", "b"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.setSort(WaitlistSort.BIGGEST_DISCOUNT)
        advanceUntilIdle()

        assertEquals(listOf("b", "a"), vm.uiState.value.rows.map { it.gameId }) // 50% then 0%
    }

    @Test
    fun region_mismatch_suppresses_prices() = runTest {
        stubs(snapshot(region = "DE"), setOf("a", "b"), region = "US")

        val vm = viewModel()
        advanceUntilIdle()

        val beta = vm.uiState.value.rows.first { it.gameId == "b" }
        assertNull(beta.salePrice) // priced in DE but the user is in US — hidden until refresh
        assertEquals(0, beta.discountPercent)
    }

    // ---------------------------------------------------------------- sharing the wishlist

    private fun itadId(n: Int) = "018d937f-0000-7000-8000-${n.toString().padStart(12, '0')}"

    private fun itadSnapshot(count: Int) = WaitlistDisplaySnapshot(
        items = (1..count).map { WaitlistDisplayItem(gameId = itadId(it), title = "Game $it", addedEpochMs = (100 - it).toLong()) },
        regionCode = "US",
    )

    @Test
    fun the_waitlist_can_be_shared_when_share_links_are_on_and_it_has_games() = runTest {
        stubs(itadSnapshot(2), setOf(itadId(1), itadId(2)))
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.canShare)
    }

    @Test
    fun the_waitlist_cannot_be_shared_when_share_links_are_off_or_it_is_empty() = runTest {
        stubs(itadSnapshot(2), setOf(itadId(1), itadId(2)))
        val off = viewModel(linksOff)
        advanceUntilIdle()
        assertFalse(off.uiState.value.canShare)
        off.openSharePicker()
        assertNull(off.uiState.value.sharePicked)

        stubs(itadSnapshot(2), emptySet())
        val empty = viewModel()
        advanceUntilIdle()
        assertFalse(empty.uiState.value.canShare)
    }

    @Test
    fun the_picker_opens_with_nothing_picked_and_keeps_the_order_picked() = runTest {
        stubs(itadSnapshot(3), setOf(itadId(1), itadId(2), itadId(3)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.openSharePicker()
        assertEquals<List<String>?>(emptyList(), vm.uiState.value.sharePicked)
        vm.toggleSharePick(itadId(3))
        vm.toggleSharePick(itadId(1))
        assertEquals<List<String>?>(listOf(itadId(3), itadId(1)), vm.uiState.value.sharePicked)
        vm.toggleSharePick(itadId(3))
        assertEquals<List<String>?>(listOf(itadId(1)), vm.uiState.value.sharePicked)
    }

    @Test
    fun no_more_than_ten_games_can_be_picked() = runTest {
        stubs(itadSnapshot(12), (1..12).map(::itadId).toSet())
        val vm = viewModel()
        advanceUntilIdle()

        vm.openSharePicker()
        (1..12).forEach { vm.toggleSharePick(itadId(it)) }
        assertEquals<List<String>?>((1..10).map(::itadId), vm.uiState.value.sharePicked)
    }

    @Test
    fun sharing_emits_the_wishlist_link_for_the_picked_games_and_closes_the_picker() = runTest {
        stubs(itadSnapshot(3), setOf(itadId(1), itadId(2), itadId(3)))
        val vm = viewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.openSharePicker()
        vm.toggleSharePick(itadId(2))
        vm.toggleSharePick(itadId(1))
        vm.shareWishlist()

        assertEquals(listOf<WaitlistEvent>(WaitlistEvent.ShareWishlist(ShareLinks.wishlist(listOf(itadId(2), itadId(1)))!!)), events)
        assertNull(vm.uiState.value.sharePicked)
    }

    @Test
    fun sharing_with_nothing_picked_does_nothing() = runTest {
        stubs(itadSnapshot(2), setOf(itadId(1), itadId(2)))
        val vm = viewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.openSharePicker()
        vm.shareWishlist()

        assertTrue(events.isEmpty())
        assertEquals<List<String>?>(emptyList(), vm.uiState.value.sharePicked)
        vm.closeSharePicker()
        assertNull(vm.uiState.value.sharePicked)
    }
}
