package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.deal.StoreDealPair
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_go_to_deal_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_details_view_game_page_label
import pm.bam.gamedeals.common.ui.generated.resources.deal_favourite_add_action
import pm.bam.gamedeals.common.ui.generated.resources.library_action_failed
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.PlatformActions
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleCandidate
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideCard
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideContent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSection
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSectionKind
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleOffer
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes

/**
 * Device coverage for the full guide screen (not just its content): a row opens the shared peek sheet, and the
 * sheet's buttons and the peek events reach the right place — store link, game page, waitlist, error snackbar, share.
 */
class WinterSaleGuidePeekScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val guide: WinterSaleGuideViewModel = mockk(relaxed = true)
    private val peek: WinterSalePeekViewModel = mockk(relaxed = true)
    private val gamePeek = MutableStateFlow<GamePeekSheetData?>(null)
    private val events = MutableSharedFlow<WinterSalePeekViewModel.Event>(extraBufferCapacity = 1)
    private val shared = mutableListOf<String>()
    private val text = mutableMapOf<String, String>()

    @Before
    fun setup() {
        every { guide.state } returns MutableStateFlow(guideState())
        every { peek.gamePeek } returns gamePeek
        every { peek.waitlistIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.collectionIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.ignoredIds } returns MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
        every { peek.stores } returns MutableStateFlow(persistentMapOf())
        every { peek.events } returns events
    }

    private fun setScreen(onGame: (String) -> Unit = {}, onOffer: (String) -> Unit = {}) {
        val actions = object : PlatformActions {
            override fun share(text: String) { shared += text }
            override fun openInApp(url: String) = Unit
            override fun openAppNotificationSettings() = Unit
            override fun openStoreListing(storeId: String) = Unit
        }
        composeTestRule.setContent {
            text["goToDeal"] = stringResource(CommonRes.string.deal_details_go_to_deal_label)
            text["gamePage"] = stringResource(CommonRes.string.deal_details_view_game_page_label)
            text["addWaitlist"] = stringResource(CommonRes.string.deal_favourite_add_action)
            text["failed"] = stringResource(CommonRes.string.library_action_failed)
            CompositionLocalProvider(LocalPlatformActions provides actions) {
                WinterSaleGuideScreen(onBack = {}, onGame = onGame, onOffer = onOffer, viewModel = guide, peek = peek)
            }
        }
    }

    @Test
    fun opening_the_guide_starts_its_checks_and_leaving_stops_them() {
        setScreen()
        composeTestRule.waitForIdle()
        verify(exactly = 1) { guide.onResume() }

        composeTestRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        composeTestRule.waitForIdle()
        verify(atLeast = 1) { guide.onPause() }
    }

    @Test
    fun tapping_a_row_peeks_that_game() {
        setScreen()

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()

        verify(exactly = 1) { peek.peek(GAME_ID, GAME_TITLE, any()) }
    }

    @Test
    fun the_sheet_opens_the_offer_and_the_game_page() {
        val offers = mutableListOf<String>()
        val games = mutableListOf<String>()
        gamePeek.value = peekData()
        setScreen(onGame = { games += it }, onOffer = { offers += it })

        composeTestRule.onNodeWithText(text.getValue("goToDeal")).performClick()
        composeTestRule.onNodeWithText(text.getValue("gamePage")).performClick()

        assertEquals(listOf(DEAL_URL), offers)
        assertEquals(listOf(GAME_ID), games)
    }

    @Test
    fun the_sheet_waitlist_button_toggles_through_the_peek_view_model() {
        gamePeek.value = peekData()
        setScreen()

        composeTestRule.onNodeWithContentDescription(text.getValue("addWaitlist")).performClick()

        verify(exactly = 1) { peek.toggleWaitlist(GAME_ID) }
    }

    @Test
    fun a_failed_library_change_shows_a_snackbar_and_share_reaches_the_platform() {
        setScreen()
        composeTestRule.waitForIdle()

        events.tryEmit(WinterSalePeekViewModel.Event.ActionFailed)
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText(text.getValue("failed"))).fetchSemanticsNodes().isNotEmpty()
        }

        events.tryEmit(WinterSalePeekViewModel.Event.Share(SHARE_TEXT))
        composeTestRule.waitUntil(5_000) { shared.isNotEmpty() }
        assertEquals(listOf(SHARE_TEXT), shared)
    }

    private fun peekData() = GamePeekSheetData.Data(
        gameId = GAME_ID,
        gameName = GAME_TITLE,
        thumb = null,
        bestDeal = StoreDealPair(
            store = Store(storeID = 61, storeName = "Steam", isActive = true,
                images = Store.StoreImages(banner = "", logo = "", icon = ""), iconUrl = "", expires = 0L),
            deal = GameDetails.GameDeal(
                storeID = 61, dealID = "deal-1", priceValue = 9.99, priceDenominated = "£9.99",
                retailPriceValue = 19.99, retailPriceDenominated = "£19.99", savings = 50, url = DEAL_URL,
            ),
        ),
    )

    private fun guideState(): WinterSaleGuideState {
        val offer = WinterSaleOffer(
            gameId = GAME_ID, country = "GB", shopId = 61, shopName = "Steam",
            price = WinterSaleMoney(9.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = 50,
            url = DEAL_URL, voucher = null, activationLabels = listOf("Steam"),
            checkedAtEpochMillis = SALE_START + HOUR, expiresAtEpochMillis = null, historicalLow = null,
        )
        val card = WinterSaleGuideCard(WinterSaleCandidate(GAME_ID, GAME_TITLE, GameArtwork(), offer, adult = false),
            listOf("On your waitlist"), adultAllowedOff = true)
        val content = WinterSaleGuideContent("GB", SALE_START + HOUR,
            WinterSaleGuideSection(WinterSaleGuideSectionKind.Waitlist, listOf(card)),
            WinterSaleGuideSection(WinterSaleGuideSectionKind.Series, emptyList()),
            WinterSaleGuideSection(WinterSaleGuideSectionKind.Popular, emptyList()))
        val event = WinterSaleEventState.Available(
            event = WinterSaleEvent("steam-winter-2026", SALE_START - HOUR, SALE_START, SALE_START + 10 * HOUR, "Winter Sale guide", "Soon.", "On."),
            phase = WinterSalePhase.OnSale,
            observedServerNowEpochMillis = SALE_START + HOUR,
            usingCachedDocument = false,
        )
        return WinterSaleGuideState(event = event, content = content, country = Country("GB", "United Kingdom", "GBP"), eventChecked = true)
    }

    companion object {
        private const val HOUR = 60L * 60 * 1000
        private const val SALE_START = 1_797_530_400_000L
        private const val GAME_ID = "game-1"
        private const val GAME_TITLE = "Frost Runner"
        private const val DEAL_URL = "https://itad.link/offer"
        private const val SHARE_TEXT = "Frost Runner is £9.99 on Steam"

        @JvmStatic
        @BeforeClass
        fun startKoinForAnalytics() {
            startKoin { modules(module { single<Analytics> { mockk(relaxed = true) } }) }
        }

        @JvmStatic
        @AfterClass
        fun stopKoinForAnalytics() {
            stopKoin()
        }
    }
}
