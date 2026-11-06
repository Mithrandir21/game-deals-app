package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealTotals
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubStore
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf

/** The sale hub and the sale list as people see them: the big numbers, the tiles, the badges, and where each tap goes. */
class WinterSaleHubScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val shelves = mutableListOf<WinterSaleShelf>()
    private val peeks = mutableListOf<Pair<String, WinterSaleShelf>>()
    private var forYou = 0
    private var retries = 0

    private fun setHub(state: WinterSaleHubState) {
        composeTestRule.setContent {
            WinterSaleHubContent(
                state = state,
                onBack = {},
                onForYou = { forYou++ },
                onShelf = { shelves += it },
                onPeek = { deal, shelf -> peeks += deal.gameId to shelf },
                onRetry = { retries++ },
            )
        }
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }

    @Test
    fun the_header_shows_the_rounded_total_and_the_countdown() {
        setHub(hubState())

        composeTestRule.onNodeWithText("The Winter Sale is on").assertExists()
        composeTestRule.onNodeWithText("40,000+ deals across 2 stores").assertExists()
        // One pill: the countdown with the day, never a second pill that could wrap.
        composeTestRule.onNodeWithText("Ends in 9 days · ${winterSaleDay(NOW + 9 * DAY + HOUR)}").assertExists()
        composeTestRule.onNodeWithText("Until", substring = true).assertDoesNotExist()
    }

    @Test
    fun while_the_shelves_load_the_page_shows_only_a_spinner() {
        // The blue card would open on the countdown and switch to the deal total a moment later, so it waits too.
        setHub(hubState().copy(hub = null, loading = true))

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeTestRule.onNodeWithText("The Winter Sale is on").assertDoesNotExist()
        composeTestRule.onNodeWithText("Ends in 9 days", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("For you").assertDoesNotExist()
    }

    @Test
    fun the_store_tiles_open_their_lists() {
        setHub(hubState())

        scrollTo(hasContentDescription("Steam, 33,646 deals. See all"))
        composeTestRule.onNodeWithContentDescription("Steam, 33,646 deals. See all").performClick()
        scrollTo(hasContentDescription("GOG, 6,127 deals. See all"))
        composeTestRule.onNodeWithContentDescription("GOG, 6,127 deals. See all").performClick()

        assertEquals(listOf(WinterSaleShelf.Store(61), WinterSaleShelf.Store(35)), shelves)
    }

    @Test
    fun see_all_opens_the_full_shelf_and_a_card_peeks_its_game() {
        setHub(hubState())

        scrollTo(hasContentDescription("See all: All-time lows"))
        composeTestRule.onNodeWithContentDescription("See all: All-time lows").performClick()
        scrollTo(hasContentDescription("See all: 75% off or more"))
        composeTestRule.onNodeWithContentDescription("See all: 75% off or more").performClick()
        composeTestRule.onAllNodesWithText("Metro Exodus").onFirst().performClick()

        assertEquals(listOf(WinterSaleShelf.AllTimeLows, WinterSaleShelf.BigCuts), shelves)
        assertEquals("metro", peeks.single().first)
    }

    @Test
    fun the_for_you_tile_opens_the_personal_list() {
        setHub(hubState())

        composeTestRule.onNodeWithText("For you").performClick()

        assertEquals(1, forYou)
    }

    @Test
    fun cards_carry_home_s_letters_and_say_them() {
        setHub(hubState())

        composeTestRule.onAllNodesWithText("N").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("-90%").onFirst().assertExists()
        composeTestRule.onAllNodes(hasContentDescription("new lowest price ever", substring = true)).onFirst().assertExists()
        scrollTo(hasText("S"))
        composeTestRule.onAllNodesWithText("S").onFirst().assertExists()
        scrollTo(hasText("H"))
        composeTestRule.onAllNodes(hasContentDescription("at its lowest price ever", substring = true)).onFirst().assertExists()
    }

    @Test
    fun a_voucher_deal_says_so() {
        val state = hubState()
        val hub = state.hub!!
        setHub(state.copy(hub = hub.copy(bigCuts = listOf(deal("rdr2", "Red Dead Redemption 2", cut = 77, shopId = 36).copy(voucher = "SALE10")))))

        scrollTo(hasContentDescription("voucher available", substring = true))
        composeTestRule.onAllNodes(hasContentDescription("voucher available", substring = true)).onFirst().assertExists()
    }

    @Test
    fun prices_line_up_along_a_shelf_whatever_the_title_length() {
        val state = hubState()
        val hub = state.hub!!
        setHub(state.copy(hub = hub.copy(bigCuts = listOf(
            deal("doom", "DOOM", cut = 83, shopId = 36).copy(price = WinterSaleMoney(3.44, "GBP")),
            deal("tomb", "Tomb Raider Game of the Year Edition", cut = 90, shopId = 37).copy(price = WinterSaleMoney(1.98, "GBP")),
        ))))

        scrollTo(hasContentDescription("Tomb Raider", substring = true))
        val short = composeTestRule.onNode(hasText("3.44", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val long = composeTestRule.onNode(hasText("1.98", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(short.top, long.top, 0.5f)
    }

    @Test
    fun a_store_shelf_leaves_the_store_off_its_cards_while_other_shelves_name_it() {
        setHub(hubState())

        // Steam's own shelf: the store is the shelf's heading, so its cards skip it.
        composeTestRule.onAllNodes(hasAnyAncestor(hasContentDescription("Borderlands 2", substring = true)) and hasText("Steam"), useUnmergedTree = true)
            .assertCountEquals(0)
        scrollTo(hasContentDescription("Metro Exodus", substring = true))
        composeTestRule.onAllNodes(hasAnyAncestor(hasContentDescription("Metro Exodus", substring = true)) and hasText("Shop 16"), useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun a_card_strikes_through_the_old_price_only_when_it_is_higher() {
        val state = hubState()
        val hub = state.hub!!
        setHub(state.copy(hub = hub.copy(bigCuts = listOf(
            deal("rdr2", "Red Dead Redemption 2", cut = 77, shopId = 36).copy(price = WinterSaleMoney(13.79, "GBP"), regular = WinterSaleMoney(59.99, "GBP")),
            deal("same", "Same Price", cut = 80, shopId = 37).copy(price = WinterSaleMoney(4.44, "GBP"), regular = WinterSaleMoney(4.44, "GBP")),
        ))))

        scrollTo(hasContentDescription("Same Price", substring = true))
        composeTestRule.onAllNodes(hasText("59.99", substring = true), useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onAllNodes(hasText("4.44", substring = true), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun a_hub_that_failed_offers_a_retry() {
        setHub(hubState().copy(hub = null, failed = true))
        composeTestRule.onNodeWithText("Couldn't load the sale right now.").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test
    fun the_header_counts_every_store_with_deals_even_when_a_tile_is_missing() {
        setHub(hubState().let { it.copy(hub = it.hub!!.copy(partial = true, totals = WinterSaleDealTotals(64_127, 8))) })

        composeTestRule.onNodeWithText("64,000+ deals across 8 stores").assertExists()
    }

    @Test
    fun a_hub_with_no_deals_says_so_instead_of_showing_empty_shelves() {
        setHub(hubState().let {
            it.copy(hub = it.hub!!.copy(steam = null, otherStores = emptyList(), allTimeLows = emptyList(), bigCuts = emptyList()))
        })

        composeTestRule.onNodeWithText("No sale deals found right now.", substring = true).assertExists()
        composeTestRule.onNodeWithContentDescription("See all: All-time lows").assertDoesNotExist()
    }

    @Test
    fun a_partial_hub_says_some_shelves_are_missing() {
        setHub(hubState().let { it.copy(hub = it.hub!!.copy(partial = true, totals = null)) })

        composeTestRule.onNodeWithText("Some shelves couldn't load. Pull down to try again.").assertExists()
        composeTestRule.onNodeWithText("Deals across 2 stores").assertExists()
    }

    @Test
    fun the_list_names_the_store_low_and_shows_the_end() {
        val loadMore = mutableListOf<Unit>()
        composeTestRule.setContent {
            WinterSaleListContent(
                state = WinterSaleListState(
                    shelf = WinterSaleShelf.Store(61),
                    saleEndEpochMillis = NOW + 2 * HOUR,
                    storeName = "Steam",
                    storeDeals = 33_646,
                    deals = listOf(deal("nms", "No Man's Sky", cut = 60, flag = WinterSaleDealFlag.StoreLow)).toImmutableList(),
                    loading = false,
                    endReached = true,
                ),
                onBack = {},
                onLoadMore = { loadMore += Unit },
                nowEpochMillis = { NOW },
            )
        }

        composeTestRule.onNodeWithText("33,646 deals · top games, most popular first").assertExists()
        composeTestRule.onNodeWithText("Ends in 2 hours").assertExists()
        composeTestRule.onNodeWithText("S").assertExists()
        composeTestRule.onAllNodes(hasContentDescription("lowest price at this store", substring = true)).onFirst().assertExists()
        composeTestRule.onNodeWithText("-60%").assertExists()
        composeTestRule.onNodeWithText("That's everything for now.").assertExists()
        assertTrue("a short list asks for more straight away", loadMore.isNotEmpty())
    }

    @Test
    fun a_failed_page_keeps_the_rows_and_offers_a_retry() {
        var retried = 0
        var peeked: String? = null
        composeTestRule.setContent {
            WinterSaleListContent(
                state = WinterSaleListState(
                    shelf = WinterSaleShelf.AllTimeLows,
                    saleEndEpochMillis = null,
                    deals = listOf(deal("metro", "Metro Exodus", cut = 90, flag = WinterSaleDealFlag.AllTimeLow)).toImmutableList(),
                    loading = false,
                    moreFailed = true,
                ),
                onBack = {},
                onRetry = { retried++ },
                onPeek = { peeked = it.gameId },
            )
        }

        composeTestRule.onNodeWithText("Couldn't load more games.").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        composeTestRule.onNodeWithText("Metro Exodus").performClick()

        assertEquals(1, retried)
        assertEquals("metro", peeked)
    }

    @Test
    fun each_list_has_its_own_title_and_line_and_states() {
        var state by mutableStateOf(listState(WinterSaleShelf.BigCuts))
        var retried = 0
        composeTestRule.setContent {
            WinterSaleListContent(state = state, onBack = {}, onRetry = { retried++ }, nowEpochMillis = { NOW })
        }

        composeTestRule.onNodeWithText("75% off or more").assertExists()
        composeTestRule.onNodeWithText("Most popular first").assertExists()

        state = listState(WinterSaleShelf.AllTimeLows)
        composeTestRule.onNodeWithText("All-time lows").assertExists()
        composeTestRule.onNodeWithText("Lowest price ever · most popular first").assertExists()
        composeTestRule.onAllNodesWithText("H").onFirst().assertExists()

        state = listState(WinterSaleShelf.Store(61)).copy(storeName = null, storeDeals = null)
        composeTestRule.onNodeWithText("Store deals").assertExists()
        composeTestRule.onNodeWithText("Top games, most popular first").assertExists()

        state = listState(null)
        composeTestRule.onAllNodesWithText("Winter Sale").onFirst().assertExists()
        composeTestRule.onNodeWithText("Most popular first").assertDoesNotExist()

        state = listState(WinterSaleShelf.BigCuts).copy(deals = emptyList<WinterSaleDeal>().toImmutableList())
        composeTestRule.onNodeWithText("Nothing here right now.").assertExists()

        state = listState(WinterSaleShelf.BigCuts).copy(deals = emptyList<WinterSaleDeal>().toImmutableList(), failed = true)
        composeTestRule.onNodeWithText("Couldn't load this list right now.").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)

        state = listState(WinterSaleShelf.BigCuts).copy(deals = emptyList<WinterSaleDeal>().toImmutableList(), loading = true)
        composeTestRule.onNodeWithText("Nothing here right now.").assertDoesNotExist()
        composeTestRule.onNodeWithText("Couldn't load this list right now.").assertDoesNotExist()
    }

    private fun listState(shelf: WinterSaleShelf?) = WinterSaleListState(
        shelf = shelf,
        saleEndEpochMillis = NOW + 3 * DAY,
        storeName = "Steam",
        storeDeals = 33_646,
        deals = listOf(deal("metro", "Metro Exodus", cut = 90, flag = WinterSaleDealFlag.AllTimeLow, shopId = 16)).toImmutableList(),
        loading = false,
        endReached = true,
    )

    private fun hubState(): WinterSaleHubState {
        val event = WinterSaleEvent("steam-winter-2026", NOW - 2 * DAY, NOW - DAY, NOW + 9 * DAY + HOUR, "Winter Sale guide", "Soon.", "On.")
        val steam = WinterSaleHubStore(61, "Steam", 33_646, listOf(
            deal("bl2", "Borderlands 2", cut = 90, flag = WinterSaleDealFlag.NewLow),
            deal("nms", "No Man's Sky", cut = 60, flag = WinterSaleDealFlag.StoreLow),
        ))
        val gog = WinterSaleHubStore(35, "GOG", 6_127, listOf(deal("doom", "DOOM", cut = 80, shopId = 35)))
        return WinterSaleHubState(
            event = WinterSaleEventState.Available(event, WinterSalePhase.OnSale, NOW, usingCachedDocument = false),
            eventChecked = true,
            hub = WinterSaleHub(
                country = "GB", includeAdult = false, steam = steam, otherStores = listOf(gog),
                allTimeLows = listOf(deal("metro", "Metro Exodus", cut = 90, flag = WinterSaleDealFlag.AllTimeLow, shopId = 16)),
                bigCuts = listOf(deal("rdr2", "Red Dead Redemption 2", cut = 77, shopId = 36)),
                totals = WinterSaleDealTotals(40_773, 2), partial = false, loadedAtEpochMillis = NOW,
            ),
        )
    }

    private fun deal(id: String, title: String, cut: Int, flag: WinterSaleDealFlag? = null, shopId: Int = 61) = WinterSaleDeal(
        gameId = id, title = title, artwork = GameArtwork(), shopId = shopId, shopName = if (shopId == 61) "Steam" else "Shop $shopId",
        price = WinterSaleMoney(1.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = cut, url = "https://itad.link/$id",
        voucher = null, flag = flag, mature = false,
    )

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
        const val NOW = 1_797_530_400_000L
    }
}
