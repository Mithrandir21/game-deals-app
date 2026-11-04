package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleCandidate
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideCard
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideContent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSection
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideSectionKind
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleOffer
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_activation
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_checking_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_discount
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_ends
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_from_store
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hidden
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_last_link
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_no_sales
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_offer_link
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_partial
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_price_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_sign_in
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_starts
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_unavailable
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_updating

/**
 * Device UI coverage for the Winter Sale guide and its Home card, driven by fixed [WinterSaleGuideState]s so each
 * screen state (checking, hidden, sign-in, series still checking, partial, stale, no sales, priced card) is pinned.
 */
class WinterSaleGuideScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val text = mutableMapOf<String, String>()

    @Test
    fun before_the_first_check_it_shows_a_spinner_not_unavailable() {
        setGuide(WinterSaleGuideState(eventChecked = false))

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
        composeTestRule.onNodeWithText(text.getValue("hidden")).assertDoesNotExist()
    }

    @Test
    fun a_hidden_event_says_the_guide_is_unavailable() {
        setGuide(WinterSaleGuideState(event = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled), eventChecked = true))

        composeTestRule.onNodeWithText(text.getValue("hidden")).assertIsDisplayed()
    }

    @Test
    fun a_signed_out_user_is_offered_sign_in_while_series_are_still_checking() {
        setGuide(guideState(content(
            waitlist = section(WinterSaleGuideSectionKind.Waitlist, signInRequired = true),
            series = section(WinterSaleGuideSectionKind.Series, checking = true),
        )))

        show(text.getValue("signIn"))
        show(text.getValue("checkingSeries"))
        composeTestRule.onNodeWithText(text.getValue("noSales")).assertDoesNotExist()
    }

    @Test
    fun a_partial_load_keeps_its_cards_and_names_the_failed_parts() {
        setGuide(guideState(content(
            waitlist = section(WinterSaleGuideSectionKind.Waitlist, listOf(card()), incomplete = true),
            popular = section(WinterSaleGuideSectionKind.Popular, unavailable = true),
        )))

        show(GAME_TITLE)
        show(text.getValue("partial"))
        show(text.getValue("sectionUnavailable"))
    }

    @Test
    fun stale_prices_are_hidden_but_the_last_link_stays() {
        setGuide(guideState(content(waitlist = section(WinterSaleGuideSectionKind.Waitlist, listOf(card())))).copy(stalePrices = true))

        show(text.getValue("lastLink"))
        // Once in the header and once on the card, in place of the price.
        composeTestRule.onAllNodesWithText(text.getValue("priceUnavailable")).assertCountEquals(2)
        composeTestRule.onNodeWithText(text.getValue("discount")).assertDoesNotExist()
    }

    @Test
    fun no_sales_anywhere_says_so() {
        setGuide(guideState(content()))

        show(text.getValue("noSales"))
    }

    @Test
    fun a_card_shows_the_offer_and_opens_the_game_or_the_supplied_link() {
        val games = mutableListOf<String>()
        val links = mutableListOf<String>()
        setGuide(guideState(content(waitlist = section(WinterSaleGuideSectionKind.Waitlist, listOf(card())))),
            onGame = { games += it }, onOffer = { links += it })

        show(text.getValue("updating"))
        show(text.getValue("discount"))
        show(text.getValue("store"))
        show(text.getValue("activation"))
        show(text.getValue("offerLink"))

        composeTestRule.onNodeWithText(text.getValue("offerLink")).performClick()
        composeTestRule.onNodeWithText(GAME_TITLE).performClick()
        assertEquals(listOf(OFFER_URL), links)
        assertEquals(listOf(GAME_ID), games)
    }

    @Test
    fun the_home_card_shows_pre_sale_copy_and_opens_the_guide() {
        var opened = 0
        composeTestRule.setContent {
            loadText()
            WinterSaleHomeCard(available(WinterSalePhase.BeforeSale)) { opened++ }
        }

        composeTestRule.onNodeWithText(BEFORE_COPY).assertIsDisplayed()
        composeTestRule.onNodeWithText(text.getValue("starts")).assertIsDisplayed()
        composeTestRule.onNodeWithText(DURING_COPY).assertDoesNotExist()
        composeTestRule.onNodeWithText(EVENT_TITLE).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun the_home_card_shows_sale_copy_and_the_end_time_during_the_sale() {
        composeTestRule.setContent {
            loadText()
            WinterSaleHomeCard(available(WinterSalePhase.OnSale)) {}
        }

        composeTestRule.onNodeWithText(DURING_COPY).assertIsDisplayed()
        composeTestRule.onNodeWithText(text.getValue("ends")).assertIsDisplayed()
        composeTestRule.onNodeWithText(BEFORE_COPY).assertDoesNotExist()
    }

    private fun setGuide(state: WinterSaleGuideState, onGame: (String) -> Unit = {}, onOffer: (String) -> Unit = {}) {
        composeTestRule.setContent {
            loadText()
            WinterSaleGuideContent(state, onBack = {}, onGame = onGame, onOffer = onOffer, onRefresh = {})
        }
    }

    /** Scrolls the guide's list to [label] (it may sit below the fold on a small screen), then checks it is shown. */
    private fun show(label: String) {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Composable
    private fun loadText() {
        text["hidden"] = stringResource(Res.string.winter_sale_hidden)
        text["signIn"] = stringResource(Res.string.winter_sale_sign_in)
        text["checkingSeries"] = stringResource(Res.string.winter_sale_checking_series)
        text["noSales"] = stringResource(Res.string.winter_sale_no_sales)
        text["partial"] = stringResource(Res.string.winter_sale_partial)
        text["sectionUnavailable"] = stringResource(Res.string.winter_sale_unavailable)
        text["priceUnavailable"] = stringResource(Res.string.winter_sale_price_unavailable)
        text["lastLink"] = stringResource(Res.string.winter_sale_last_link)
        text["offerLink"] = stringResource(Res.string.winter_sale_offer_link)
        text["updating"] = stringResource(Res.string.winter_sale_updating)
        text["discount"] = stringResource(Res.string.winter_sale_discount, CUT)
        text["store"] = stringResource(Res.string.winter_sale_from_store, SHOP)
        text["activation"] = stringResource(Res.string.winter_sale_activation, LAUNCHER)
        text["starts"] = stringResource(Res.string.winter_sale_starts, winterSaleTime(SALE_START))
        text["ends"] = stringResource(Res.string.winter_sale_ends, winterSaleTime(SALE_END))
    }

    private fun available(phase: WinterSalePhase) = WinterSaleEventState.Available(
        event = WinterSaleEvent("steam-winter-2026", SALE_START - DAY, SALE_START, SALE_END, EVENT_TITLE, BEFORE_COPY, DURING_COPY),
        phase = phase,
        observedServerNowEpochMillis = if (phase == WinterSalePhase.BeforeSale) SALE_START - HOUR else SALE_START + HOUR,
        usingCachedDocument = false,
    )

    private fun guideState(content: WinterSaleGuideContent) = WinterSaleGuideState(
        event = available(WinterSalePhase.OnSale),
        content = content,
        country = Country("GB", "United Kingdom", "GBP"),
        eventChecked = true,
    )

    private fun content(
        waitlist: WinterSaleGuideSection = section(WinterSaleGuideSectionKind.Waitlist),
        series: WinterSaleGuideSection = section(WinterSaleGuideSectionKind.Series),
        popular: WinterSaleGuideSection = section(WinterSaleGuideSectionKind.Popular),
    ) = WinterSaleGuideContent("GB", SALE_START + HOUR, waitlist, series, popular)

    private fun section(
        kind: WinterSaleGuideSectionKind,
        cards: List<WinterSaleGuideCard> = emptyList(),
        incomplete: Boolean = false,
        unavailable: Boolean = false,
        signInRequired: Boolean = false,
        checking: Boolean = false,
    ) = WinterSaleGuideSection(kind, cards, incomplete = incomplete, unavailable = unavailable, signInRequired = signInRequired, checking = checking)

    private fun card(): WinterSaleGuideCard {
        val offer = WinterSaleOffer(
            gameId = GAME_ID, country = "GB", shopId = 61, shopName = SHOP,
            price = WinterSaleMoney(9.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = CUT,
            url = OFFER_URL, voucher = null, activationLabels = listOf(LAUNCHER),
            checkedAtEpochMillis = SALE_START + HOUR, expiresAtEpochMillis = null, historicalLow = null,
        )
        return WinterSaleGuideCard(WinterSaleCandidate(GAME_ID, GAME_TITLE, GameArtwork(), offer, adult = false), listOf("On your waitlist"), adultAllowedOff = true)
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        const val DAY = 24 * HOUR
        const val SALE_START = 1_797_530_400_000L // 17 Dec 2026, 18:00 UTC
        const val SALE_END = 1_799_085_600_000L // 4 Jan 2027, 18:00 UTC
        const val EVENT_TITLE = "Winter Sale guide"
        const val BEFORE_COPY = "The sale starts soon."
        const val DURING_COPY = "The sale is on."
        const val GAME_ID = "game-1"
        const val GAME_TITLE = "Frost Runner"
        const val SHOP = "Steam"
        const val LAUNCHER = "Steam"
        const val CUT = 50
        const val OFFER_URL = "https://itad.link/offer"
    }
}
