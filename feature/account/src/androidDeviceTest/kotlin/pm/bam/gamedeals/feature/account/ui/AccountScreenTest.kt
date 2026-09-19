package pm.bam.gamedeals.feature.account.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.jetbrains.compose.resources.stringResource
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Region
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_row_followed_series
import pm.bam.gamedeals.feature.account.generated.resources.account_row_how_it_works
import pm.bam.gamedeals.feature.account.generated.resources.account_row_mature_switch_description
import pm.bam.gamedeals.feature.account.generated.resources.account_row_region
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_in
import pm.bam.gamedeals.feature.account.ui.AccountViewModel.AccountScreenData

/**
 * Device UI coverage for the [AccountScreen] hub in its signed-out state: sign-in, the discovery/app
 * nav rows, the mature-content toggle, and the region picker (open + select) — driven through a mocked
 * [AccountViewModel].
 *
 * Only the signed-out branch is exercised here: the signed-in branch embeds a NotificationDeliveryRow
 * that resolves its own koinViewModel(), which needs a Koin graph these screen-level tests don't set up.
 * Uses createAndroidComposeRule<ComponentActivity> because the hub has no top bar.
 */
class AccountScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModel: AccountViewModel = mockk(relaxed = true)
    private val onOpenFollowedSeries = mockk<() -> Unit>(relaxed = true)
    private val onReplayOnboarding = mockk<() -> Unit>(relaxed = true)

    private lateinit var labels: Labels

    private fun setContent() {
        every { viewModel.uiState } returns MutableStateFlow(
            AccountScreenData(loggedIn = false, selectedCountry = Country("US", "United States", Region.AMERICAS)),
        )
        every { viewModel.countries } returns persistentListOf(US, UK)
        // The hub collects `events` for the login-failure snackbar. A relaxed mock can't stand in here:
        // `SharedFlow.collect` is declared to return `Nothing`, which mockk cannot fabricate — it throws
        // KotlinNothingValueException. Hand it a real, never-emitting flow instead.
        every { viewModel.events } returns MutableSharedFlow()
        composeTestRule.setContent {
            labels = Labels.load()
            GameDealsTheme {
                AccountScreen(
                    onOpenFollowedSeries = onOpenFollowedSeries,
                    onReplayOnboarding = onReplayOnboarding,
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun signInDispatchesLogin() {
        setContent()

        composeTestRule.onNodeWithText(labels.signIn).performClick()

        verify(exactly = 1) { viewModel.onLogin() }
    }

    @Test
    fun followedSeriesRowNavigates() {
        setContent()

        rowWithText(labels.followedSeries).performClick()

        verify(exactly = 1) { onOpenFollowedSeries() }
    }

    @Test
    fun howItWorksRowReplaysOnboarding() {
        setContent()

        rowWithText(labels.howItWorks).performClick()

        verify(exactly = 1) { onReplayOnboarding() }
    }

    @Test
    fun matureToggleDispatchesOptIn() {
        setContent()

        rowWith(hasContentDescription(labels.matureSwitch)).performClick()

        verify(exactly = 1) { viewModel.onSetMatureOptIn(true) }
    }

    @Test
    fun regionRowOpensPickerAndSelectsCountry() {
        setContent()

        rowWithText(labels.region).performClick()
        // "United Kingdom" appears only inside the opened picker (US is the selected subtitle), so it's unambiguous.
        composeTestRule.onNodeWithText(UK.name).performClick()

        verify(exactly = 1) { viewModel.onCountrySelected(UK) }
    }

    /**
     * The hub's rows live in a `LazyColumn`, which doesn't compose off-screen items — so a row below the
     * fold can't be matched at all on shorter screens, and `performScrollTo` can't help (it needs an
     * existing node). Scroll the list to the row first, then match it. The hub is the only scrollable on
     * screen, so it's addressable without a testTag, consistent with the rest of the suite.
     */
    private fun rowWith(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(matcher)
        return composeTestRule.onNode(matcher)
    }

    private fun rowWithText(label: String): SemanticsNodeInteraction = rowWith(hasText(label))

    private data class Labels(
        val signIn: String,
        val followedSeries: String,
        val howItWorks: String,
        val matureSwitch: String,
        val region: String,
    ) {
        companion object {
            @Composable
            fun load(): Labels = Labels(
                signIn = stringResource(Res.string.account_sign_in),
                followedSeries = stringResource(Res.string.account_row_followed_series),
                howItWorks = stringResource(Res.string.account_row_how_it_works),
                matureSwitch = stringResource(Res.string.account_row_mature_switch_description),
                region = stringResource(Res.string.account_row_region),
            )
        }
    }

    companion object {
        private val US = Country("US", "United States", Region.AMERICAS)
        private val UK = Country("GB", "United Kingdom", Region.EUROPE)

        // DebugEntryRow (the hub's last row) resolves AppInfo via koinInject(), so scrolling far enough to reach
        // it needs a graph. Kept up for the whole class (not per-test) so a late resolution during Compose
        // disposal never hits a closed scope — a per-test stopKoin() in @After races the compose rule's teardown.
        @JvmStatic
        @BeforeClass
        fun startKoinForAppInfo() {
            startKoin {
                modules(module { single { AppInfo(versionName = "1.0.0", versionCode = 1, storeId = "", isDebug = false) } })
            }
        }

        @JvmStatic
        @AfterClass
        fun stopKoinForAppInfo() {
            stopKoin()
        }
    }
}
