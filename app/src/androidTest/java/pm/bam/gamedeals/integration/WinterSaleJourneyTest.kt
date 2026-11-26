package pm.bam.gamedeals.integration

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.STEAM_SHOP_ID
import pm.bam.gamedeals.domain.repositories.wintersale.WINTER_SALE_HUB_STORES
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealPage
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEvent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubSource
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderDecision
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShopCount
import pm.bam.gamedeals.notifications.EXTRA_NOTIFICATION_ROUTE

/**
 * The sale through the real app: Home's card, the hub, a store's full list and back, "For you", and the reminder
 * notification's link. Only the sale's sources are faked (an event that is on sale, and ITAD's counts and deals);
 * the repositories, view models, navigation routes and screens are the production ones.
 */
class WinterSaleJourneyTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        runBlocking { GlobalContext.get().get<SettingsRepository>().setOnboardingCompleted(true) }
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun the_home_card_opens_the_hub_then_a_store_list_and_back() {
        launch()

        compose.waitFor(hasText("40,000+ deals across 8 stores"))
        compose.onNodeWithText("40,000+ deals across 8 stores").performClick()

        compose.waitFor(hasText("For you"))
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasContentDescription(STEAM_TILE))
        compose.onNodeWithContentDescription(STEAM_TILE).performClick()

        compose.waitFor(hasText("33,646 deals · top games, most popular first"))
        compose.waitFor(hasText("Steam game 1"))

        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitFor(hasText("For you"))
    }

    @Test
    fun the_reminder_notification_opens_the_hub_and_for_you_opens_the_personal_guide() {
        launch(route = "winter_sale_guide")

        compose.waitFor(hasText("For you"))
        compose.onNodeWithText("For you").performClick()

        // Signed out in tests, so the personal guide asks to sign in for the waitlist.
        compose.waitFor(hasText("Sign in to see your waitlist"))
    }

    private fun launch(route: String? = null) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (route != null) putExtra(EXTRA_NOTIFICATION_ROUTE, route)
        }
        scenario = ActivityScenario.launch(intent)
    }

    private fun ComposeTestRule.waitFor(matcher: SemanticsMatcher) {
        waitUntil(timeoutMillis = 15_000) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    companion object {
        private const val STEAM_TILE = "Steam, 33,646 deals. See all"

        @JvmStatic
        @BeforeClass
        fun fakeTheSaleSources() {
            loadKoinModules(
                module {
                    single<WinterSaleEventRepository> { OnSaleEvents }
                    single<WinterSaleReminder> { QuietReminder }
                    single<WinterSaleHubSource> { FakeItadSale }
                },
            )
        }
    }
}

/** A sale that started yesterday and ends in nine days, by the phone's own clock. */
private object OnSaleEvents : WinterSaleEventRepository {
    override suspend fun refresh(): WinterSaleEventState {
        val now = System.currentTimeMillis()
        val event = WinterSaleEvent("steam-winter-journey", now - 2 * DAY, now - DAY, now + 9 * DAY + HOUR, "Winter Sale guide", "Soon.", "On.")
        return WinterSaleEventState.Available(event, WinterSalePhase.OnSale, now, usingCachedDocument = false)
    }

    private const val HOUR = 60L * 60 * 1000
    private const val DAY = 24 * HOUR
}

private object QuietReminder : WinterSaleReminder {
    override suspend fun sync() = Unit
    override suspend fun reconcile(state: WinterSaleEventState) = Unit
    override suspend fun cancel() = Unit
    override suspend fun onSummaryPosted() = Unit
    override suspend fun claimDelivery() = WinterSaleReminderDecision.Skip
}

/** ITAD's side of the sale: Steam with 33,646 deals, the other seven hub stores with 1,000 each, and three pages per shelf. */
private object FakeItadSale : WinterSaleHubSource {
    private val names = mapOf(61 to "Steam", 35 to "GOG", 16 to "Epic Game Store", 6 to "Fanatical", 36 to "GreenManGaming",
        37 to "Humble Store", 20 to "GameBillet", 64 to "WinGameStore")

    override suspend fun shopCounts(country: String) =
        WINTER_SALE_HUB_STORES.map { WinterSaleShopCount(it, names.getValue(it), if (it == STEAM_SHOP_ID) 33_646 else 1_000) }

    override suspend fun deals(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int, limit: Int): WinterSaleDealPage {
        val shopId = (shelf as? WinterSaleShelf.Store)?.shopId ?: STEAM_SHOP_ID
        val label = when (shelf) {
            is WinterSaleShelf.Store -> names.getValue(shopId)
            WinterSaleShelf.AllTimeLows -> "Low"
            WinterSaleShelf.BigCuts -> "Cut"
        }
        val deals = (offset + 1..offset + limit).map { n ->
            WinterSaleDeal(
                gameId = "${shelf.key}-$n", title = "$label game $n", artwork = GameArtwork(), shopId = shopId, shopName = names.getValue(shopId),
                price = WinterSaleMoney(4.99, "GBP"), regular = WinterSaleMoney(19.99, "GBP"), cutPercent = 75, url = "https://itad.link/$n",
                voucher = null, flag = null, mature = false,
            )
        }
        return WinterSaleDealPage(deals, (offset + limit).takeIf { it < 3 * limit })
    }
}
