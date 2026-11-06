package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
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
import pm.bam.gamedeals.logging.analytics.Analytics

/**
 * The joins between the sale screens and their view models, which the content tests skip: which page the guide
 * opens on, and where each tap goes (analytics, navigation, the peek sheet). Also the sale's look in dark mode and
 * the hour countdowns under two days.
 */
class WinterSaleEntryScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val hub: WinterSaleHubViewModel = mockk(relaxed = true)
    private val list: WinterSaleListViewModel = mockk(relaxed = true)
    private val peek: WinterSalePeekViewModel = mockk(relaxed = true)
    private val hubState = MutableStateFlow(WinterSaleHubState())
    private val listState = MutableStateFlow(WinterSaleListState(shelf = WinterSaleShelf.AllTimeLows, saleEndEpochMillis = null))
    private val shelves = mutableListOf<Pair<String, Long>>()
    private var forYou = 0

    @Before
    fun setup() {
        every { hub.state } returns hubState
        every { list.state } returns listState
        every { peek.gamePeek } returns MutableStateFlow<GamePeekSheetData?>(null)
        every { peek.waitlistIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.collectionIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.ignoredIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.stores } returns MutableStateFlow(persistentMapOf())
        every { peek.events } returns MutableSharedFlow()
        every { guide.state } returns MutableStateFlow(
            WinterSaleGuideState(event = available(WinterSalePhase.BeforeSale), country = Country("GB", "United Kingdom", "GBP"), eventChecked = true),
        )
    }

    private fun setEntry() {
        composeTestRule.setContent {
            WinterSaleEntryScreen(
                onBack = {},
                onGame = {},
                onOffer = {},
                onForYou = { forYou++ },
                onShelf = { key, end -> shelves += key to end },
                hub = hub,
                peek = peek,
            )
        }
    }

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeTestRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }

    @Test
    fun before_the_first_check_it_shows_a_spinner_and_starts_checking() {
        setEntry()

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        composeTestRule.onNodeWithText("The Winter Sale is on").assertDoesNotExist()
        verify { hub.onResume() }
    }

    @Test
    fun during_the_sale_it_opens_on_the_hub() {
        hubState.value = onSaleState()
        setEntry()

        composeTestRule.onNodeWithText("The Winter Sale is on").assertIsDisplayed()
        composeTestRule.onNodeWithText("For you").assertIsDisplayed()
        composeTestRule.onNodeWithText("United Kingdom · GBP").assertDoesNotExist()
    }

    @Test
    fun before_the_sale_it_opens_on_the_guide() {
        hubState.value = WinterSaleHubState(event = available(WinterSalePhase.BeforeSale), eventChecked = true)
        setEntry()

        composeTestRule.onNodeWithText("The Winter Sale starts soon").assertIsDisplayed()
        composeTestRule.onNodeWithText("United Kingdom · GBP").assertIsDisplayed()
        composeTestRule.onNodeWithText("For you").assertDoesNotExist()
    }

    @Test
    fun a_store_tile_is_counted_and_opens_its_list_with_the_sale_end_on_this_phone() {
        hubState.value = onSaleState().copy(saleEndOnPhoneEpochMillis = 123_456L)
        setEntry()

        scrollTo(hasContentDescription("Steam, 33,646 deals. See all"))
        composeTestRule.onNodeWithContentDescription("Steam, 33,646 deals. See all").performClick()

        assertEquals(listOf("store:61" to 123_456L), shelves)
        verify { hub.onShelfOpened("store:61") }
    }

    @Test
    fun the_for_you_tile_is_counted_and_opens_the_personal_list() {
        hubState.value = onSaleState()
        setEntry()

        composeTestRule.onNodeWithText("For you").performClick()

        assertEquals(1, forYou)
        verify { hub.onShelfOpened("for_you") }
    }

    @Test
    fun a_game_is_counted_with_its_shelf_and_opens_the_peek_sheet() {
        hubState.value = onSaleState()
        setEntry()

        composeTestRule.onAllNodesWithText("Borderlands 2").onFirst().performClick()

        verify { hub.onGamePeeked("store:61") }
        verify { peek.peek("bl2", "Borderlands 2", null) }
    }

    @Test
    fun the_retry_reaches_the_hub() {
        hubState.value = onSaleState().copy(hub = null, failed = true)
        setEntry()

        composeTestRule.onNodeWithText("Try again").performClick()

        verify { hub.onRetry() }
    }

    @Test
    fun the_sale_list_counts_a_tapped_game_and_opens_its_peek_sheet() {
        listState.value = listState.value.copy(deals = listOf(deal("metro", "Metro Exodus", cut = 90)).toImmutableList(), loading = false)
        composeTestRule.setContent { WinterSaleListScreen(onBack = {}, onGame = {}, onOffer = {}, viewModel = list, peek = peek) }

        composeTestRule.onNodeWithText("Metro Exodus").performClick()

        verify { list.onGamePeeked() }
        verify { peek.peek("metro", "Metro Exodus", null) }
    }

    @Test
    fun the_sale_list_asks_its_view_model_for_more_and_for_a_retry() {
        listState.value = listState.value.copy(deals = listOf(deal("metro", "Metro Exodus", cut = 90)).toImmutableList(), loading = false, moreFailed = true)
        composeTestRule.setContent { WinterSaleListScreen(onBack = {}, onGame = {}, onOffer = {}, viewModel = list, peek = peek) }

        composeTestRule.onNodeWithText("Try again").performClick()

        verify { list.loadMore() }
        verify { list.retry() }
    }

    @Test
    fun in_dark_mode_the_hub_uses_the_dark_sale_colours() {
        var palette: WinterSalePalette? = null
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = true) {
                palette = winterSalePalette()
                WinterSaleHubContent(state = onSaleState(), onBack = {})
            }
        }

        assertEquals(Color(0xFF16365A), palette?.hero)
        composeTestRule.onNodeWithText("64,000+ deals across 8 stores").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("N").onFirst().assertExists()
        composeTestRule.onAllNodesWithText("-90%").onFirst().assertExists()
    }

    @Test
    fun in_light_mode_the_hub_uses_the_light_sale_colours() {
        var palette: WinterSalePalette? = null
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = false) { palette = winterSalePalette() }
        }

        composeTestRule.runOnIdle { assertEquals(Color(0xFFDCEFFF), palette?.hero) }
    }

    @Test
    fun in_dark_mode_the_home_card_reads_the_same() {
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = true) {
                WinterSaleHomeCard(available(WinterSalePhase.OnSale), WinterSaleDealTotals(64_127, 8)) {}
            }
        }

        composeTestRule.onNodeWithText("The Winter Sale is on").assertIsDisplayed()
        composeTestRule.onNodeWithText("64,000+ deals across 8 stores").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ends in 9 days").assertIsDisplayed()
    }

    @Test
    fun under_two_days_the_cards_count_down_in_hours() {
        val startsIn47Hours = available(WinterSalePhase.BeforeSale, serverNow = SALE_START - 47 * HOUR - 30 * MINUTE)
        val endsIn47Hours = available(WinterSalePhase.OnSale, serverNow = SALE_END - 47 * HOUR - 30 * MINUTE)
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                WinterSaleHomeCard(startsIn47Hours) {}
                WinterSaleHomeCard(endsIn47Hours, WinterSaleDealTotals(64_127, 8)) {}
            }
        }

        composeTestRule.onNodeWithText("Starts in 47 hours").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ends in 47 hours").assertIsDisplayed()
    }

    private fun onSaleState(): WinterSaleHubState {
        val steam = WinterSaleHubStore(61, "Steam", 33_646, listOf(deal("bl2", "Borderlands 2", cut = 90, flag = WinterSaleDealFlag.NewLow)))
        return WinterSaleHubState(
            event = available(WinterSalePhase.OnSale),
            eventChecked = true,
            hub = WinterSaleHub(
                country = "GB", includeAdult = false, steam = steam, otherStores = emptyList(),
                allTimeLows = listOf(deal("metro", "Metro Exodus", cut = 90, flag = WinterSaleDealFlag.AllTimeLow)),
                bigCuts = emptyList(), totals = WinterSaleDealTotals(64_127, 8), partial = false, loadedAtEpochMillis = SALE_START,
            ),
            saleEndOnPhoneEpochMillis = SALE_END,
        )
    }

    private fun deal(id: String, title: String, cut: Int, flag: WinterSaleDealFlag? = null) = WinterSaleDeal(
        gameId = id, title = title, artwork = GameArtwork(), shopId = 61, shopName = "Steam",
        price = WinterSaleMoney(1.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = cut, url = "https://itad.link/$id",
        voucher = null, flag = flag, mature = false,
    )

    private fun available(phase: WinterSalePhase, serverNow: Long = if (phase == WinterSalePhase.BeforeSale) SALE_START - 3 * DAY else SALE_END - 9 * DAY - HOUR) =
        WinterSaleEventState.Available(
            event = WinterSaleEvent("steam-winter-2026", SALE_START - 7 * DAY, SALE_START, SALE_END, "Winter Sale guide", "Soon.", "On."),
            phase = phase,
            observedServerNowEpochMillis = serverNow,
            usingCachedDocument = false,
        )

    companion object {
        private const val MINUTE = 60L * 1000
        private const val HOUR = 60 * MINUTE
        private const val DAY = 24 * HOUR
        private const val SALE_START = 1_797_530_400_000L
        private const val SALE_END = SALE_START + 14 * DAY

        /** The guide's own view model, which the entry screen gets from Koin only when it shows the guide. */
        private val guide: WinterSaleGuideViewModel = mockk(relaxed = true)

        @JvmStatic
        @BeforeClass
        fun startKoinForTheGuide() {
            startKoin {
                modules(module {
                    single<Analytics> { mockk(relaxed = true) }
                    viewModel { guide }
                })
            }
        }

        @JvmStatic
        @AfterClass
        fun stopKoinForTheGuide() {
            stopKoin()
        }
    }
}
