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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_activation
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_checking_series
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_ends
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_back
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_hidden
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_try_again
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_no_sales
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_partial
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_price_unavailable_short
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_show_less
import pm.bam.gamedeals.feature.home.generated.resources.winter_sale_view_all
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
        composeTestRule.onNodeWithText(text.getValue("tryAgain")).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription(text.getValue("back")).assertIsDisplayed()
    }

    @Test
    fun a_failed_check_offers_try_again() {
        var retries = 0
        setGuide(WinterSaleGuideState(event = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable), eventChecked = true),
            onRetry = { retries++ })

        composeTestRule.onNodeWithText(text.getValue("hidden")).assertIsDisplayed()
        composeTestRule.onNodeWithText(text.getValue("tryAgain")).performClick()
        assertEquals(1, retries)
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
    fun stale_prices_are_replaced_by_a_notice_and_a_chip_but_the_row_still_opens() {
        val peeked = mutableListOf<String>()
        setGuide(guideState(content(waitlist = section(WinterSaleGuideSectionKind.Waitlist, listOf(card())))).copy(stalePrices = true),
            onPeek = { peeked += it.candidate.gameId })

        show(text.getValue("priceUnavailable"))
        show(text.getValue("priceUnavailableShort"))
        composeTestRule.onNodeWithText(PRICE).assertDoesNotExist()
        composeTestRule.onNodeWithText(GAME_TITLE).performClick()
        assertEquals(listOf(GAME_ID), peeked)
    }

    @Test
    fun no_sales_anywhere_says_so() {
        setGuide(guideState(content()))

        show(text.getValue("noSales"))
    }

    @Test
    fun a_row_shows_the_offer_like_other_deal_rows_and_opens_the_peek_sheet() {
        val peeked = mutableListOf<String>()
        setGuide(guideState(content(waitlist = section(WinterSaleGuideSectionKind.Waitlist, listOf(card())))),
            onPeek = { peeked += it.candidate.gameId })

        show(text.getValue("updating"))
        show(GAME_TITLE)
        show(PRICE)
        show(SHOP)
        // The section heading already says "Your waitlist", so the row only adds the launcher.
        show(text.getValue("activation"))
        composeTestRule.onNodeWithText("On your waitlist", substring = true).assertDoesNotExist()

        composeTestRule.onNodeWithText(GAME_TITLE).performClick()
        assertEquals(listOf(GAME_ID), peeked)
    }

    @Test
    fun a_long_section_shows_its_best_five_until_view_all() {
        val cards = (1..7).map { card(id = "game-$it", title = "Game $it") }
        setGuide(guideState(content(popular = section(WinterSaleGuideSectionKind.Popular, cards))))

        show("Game 5")
        composeTestRule.onNodeWithText("Game 6").assertDoesNotExist()
        composeTestRule.onNodeWithText(text.getValue("viewAll")).performClick()
        show("Game 7")
        show(text.getValue("showLess"))
    }

    @Test
    fun followed_series_are_grouped_under_their_own_headings() {
        val cards = listOf(
            card(id = "a", title = "Fallout 4", series = listOf("Fallout")),
            card(id = "b", title = "Mass Effect LE", series = listOf("Mass Effect")),
            card(id = "c", title = "Fallout 76", series = listOf("Fallout")),
        )
        setGuide(guideState(content(series = section(WinterSaleGuideSectionKind.Series, cards))))

        show("Fallout")
        show("Mass Effect")
        show("Fallout 76")
        // One heading per series, even though two Fallout games are listed.
        composeTestRule.onAllNodesWithText("Fallout").assertCountEquals(1)
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

    private fun setGuide(
        state: WinterSaleGuideState,
        onPeek: (WinterSaleGuideCard) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            loadText()
            WinterSaleGuideContent(state, onBack = {}, onPeek = onPeek, onRefresh = {}, onRetry = onRetry)
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
        text["tryAgain"] = stringResource(Res.string.winter_sale_try_again)
        text["back"] = stringResource(Res.string.winter_sale_back)
        text["signIn"] = stringResource(Res.string.winter_sale_sign_in)
        text["checkingSeries"] = stringResource(Res.string.winter_sale_checking_series)
        text["noSales"] = stringResource(Res.string.winter_sale_no_sales)
        text["partial"] = stringResource(Res.string.winter_sale_partial)
        text["sectionUnavailable"] = stringResource(Res.string.winter_sale_unavailable)
        text["priceUnavailable"] = stringResource(Res.string.winter_sale_price_unavailable)
        text["priceUnavailableShort"] = stringResource(Res.string.winter_sale_price_unavailable_short)
        text["viewAll"] = stringResource(Res.string.winter_sale_view_all, 7)
        text["showLess"] = stringResource(Res.string.winter_sale_show_less)
        text["updating"] = stringResource(Res.string.winter_sale_updating)
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

    private fun card(id: String = GAME_ID, title: String = GAME_TITLE, series: List<String> = emptyList()): WinterSaleGuideCard {
        val offer = WinterSaleOffer(
            gameId = id, country = "GB", shopId = 61, shopName = SHOP,
            price = WinterSaleMoney(9.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = CUT,
            url = OFFER_URL, voucher = null, activationLabels = listOf(LAUNCHER),
            checkedAtEpochMillis = SALE_START + HOUR, expiresAtEpochMillis = null, historicalLow = null,
        )
        val reasons = listOf("On your waitlist") + series.map { "From $it" }
        return WinterSaleGuideCard(WinterSaleCandidate(id, title, GameArtwork(), offer, adult = false), reasons, adultAllowedOff = true, seriesNames = series)
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
        val PRICE = formatMoney(9.99, "GBP")
    }
}
