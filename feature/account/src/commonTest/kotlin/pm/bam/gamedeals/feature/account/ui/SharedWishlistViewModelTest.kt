@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.share.DealShareBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlist
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlistGame
import pm.bam.gamedeals.domain.repositories.sharedwishlist.SharedWishlistRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.store
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A friend's wishlist: "You both want" first when signed in, both groups cheapest first. */
class SharedWishlistViewModelTest : MainDispatcherTest() {

    private val sharedWishlistRepository: SharedWishlistRepository = mock(MockMode.autoUnit)
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit)
    private val dealShareBuilder: DealShareBuilder = mock(MockMode.autoUnit)

    private val auth = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    private val waitlistIds = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())

    @BeforeTest fun setUp() {
        installMainDispatcher()
        every { accountRepository.observeAuthState() } returns auth
        every { waitlistRepository.observeWaitlistIds() } returns waitlistIds
        every { storesRepository.observeStores() } returns flowOf(listOf(steam))
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf())
        every { ignoredRepository.observeIgnoredIds() } returns flowOf(persistentSetOf())
        everySuspend { sharedWishlistRepository.load(any()) } returns SharedWishlist(
            games = listOf(game("pricey", 20.0), game("none", null), game("cheap", 2.0), game("mid", 9.0)),
            unavailableCount = 1,
        )
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private val steam = store(storeID = 1, storeName = "Steam", iconUrl = "steam.png")

    private fun game(id: String, price: Double?) = SharedWishlistGame(
        gameId = id,
        title = id.replaceFirstChar { it.uppercase() },
        artwork = GameArtwork(banner300 = "art-$id"),
        price = price?.let {
            BundleGamePrice(
                gameId = id,
                bestShopName = "Steam",
                bestPriceValue = it,
                bestPriceDenominated = "USD $it",
                bestCutPercent = 50,
                bestRegularDenominated = "USD ${it * 2}",
                bestIsLowestEver = id == "cheap",
                historicalLowValue = null,
                historicalLowDenominated = null,
            )
        },
    )

    private fun viewModel(ids: String = "pricey,none,cheap,mid") = SharedWishlistViewModel(
        SavedStateHandle(mapOf("gameIds" to ids)),
        sharedWishlistRepository, accountRepository, waitlistRepository, storesRepository,
        gamesRepository, collectionRepository, ignoredRepository, dealShareBuilder, TestingLoggingListener(),
    )

    @Test
    fun signed_out_shows_the_whole_list_cheapest_first_with_games_not_on_sale_last() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertFalse(state.signedIn)
        assertTrue(state.bothWant.isEmpty())
        assertEquals(listOf("cheap", "mid", "pricey", "none"), state.others.map { it.gameId })
        assertEquals(1, state.unavailableCount)
        verifySuspend { sharedWishlistRepository.load(listOf("pricey", "none", "cheap", "mid")) }
    }

    @Test
    fun rows_carry_the_best_deal_and_the_store_icon() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        val cheap = vm.uiState.value.others.first()
        assertEquals("USD 2.0", cheap.salePrice)
        assertEquals("USD 4.0", cheap.regularPrice)
        assertEquals(50, cheap.discountPercent)
        assertEquals("Steam", cheap.storeName)
        assertEquals("steam.png", cheap.storeIconUrl)
        assertEquals("art-cheap", cheap.imageUrl)
        assertTrue(cheap.isLowestEver)
        val none = vm.uiState.value.others.last()
        assertNull(none.salePrice)
        assertNull(none.storeName)
        assertEquals(0, none.discountPercent)
    }

    @Test
    fun signed_in_puts_the_games_you_both_want_first() = runTest {
        auth.value = AuthState.LoggedIn("bam")
        waitlistIds.value = setOf("pricey", "cheap", "not-on-their-list").toPersistentSet()
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.signedIn)
        assertEquals(listOf("cheap", "pricey"), state.bothWant.map { it.gameId })
        assertTrue(state.bothWant.all { it.isWaitlisted })
        assertEquals(listOf("mid", "none"), state.others.map { it.gameId })
        assertFalse(state.others.any { it.isWaitlisted })
    }

    @Test
    fun signing_in_on_the_screen_checks_the_waitlist_and_shows_the_overlap() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        verifySuspend(exactly(0)) { waitlistRepository.getWaitlist() }

        auth.value = AuthState.LoggedIn("bam")
        waitlistIds.value = setOf("mid").toPersistentSet()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { waitlistRepository.getWaitlist() }
        assertEquals(listOf("mid"), vm.uiState.value.bothWant.map { it.gameId })
    }

    @Test
    fun signing_out_on_the_screen_drops_the_overlap_and_keeps_their_whole_list() = runTest {
        auth.value = AuthState.LoggedIn("bam")
        waitlistIds.value = setOf("pricey", "cheap").toPersistentSet()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.bothWant.size)

        // The waitlist ids are cleared a moment later by the app-wide sign-out; the screen mustn't wait for that.
        auth.value = AuthState.LoggedOut
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.signedIn)
        assertTrue(state.bothWant.isEmpty())
        assertEquals(listOf("cheap", "mid", "pricey", "none"), state.others.map { it.gameId })
        assertFalse(state.others.any { it.isWaitlisted })
    }

    @Test
    fun signed_in_with_an_empty_waitlist_there_is_no_overlap_and_their_list_shows_in_full() = runTest {
        auth.value = AuthState.LoggedIn("bam")
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.signedIn)
        assertTrue(state.bothWant.isEmpty())
        assertEquals(4, state.others.size)
        assertEquals(1, state.unavailableCount)
    }

    @Test
    fun a_failed_load_can_be_retried() = runTest {
        everySuspend { sharedWishlistRepository.load(any()) } sequentially {
            throws(IllegalStateException("offline"))
            returns(SharedWishlist(listOf(game("cheap", 2.0))))
        }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.loading)

        vm.retry()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.failed)
        assertEquals(listOf("cheap"), vm.uiState.value.others.map { it.gameId })
    }

    @Test
    fun the_route_ids_are_trimmed_deduplicated_and_capped_at_ten() = runTest {
        val ids = (1..12).joinToString(",") { "g$it" }
        viewModel(ids = " g1 ,g1,,$ids")
        advanceUntilIdle()
        verifySuspend { sharedWishlistRepository.load((1..10).map { "g$it" }) }
    }

    @Test
    fun an_empty_list_shows_the_empty_state() = runTest {
        everySuspend { sharedWishlistRepository.load(any()) } returns SharedWishlist(emptyList())
        val vm = viewModel(ids = "")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEmpty)
    }
}
