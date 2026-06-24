package pm.bam.gamedeals.feature.account.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
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
            AccountScreenData(loggedIn = false, selectedCountry = Country("US", "United States")),
        )
        every { viewModel.countries } returns persistentListOf(US, UK)
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

        composeTestRule.onNodeWithText(labels.followedSeries).performClick()

        verify(exactly = 1) { onOpenFollowedSeries() }
    }

    @Test
    fun howItWorksRowReplaysOnboarding() {
        setContent()

        composeTestRule.onNodeWithText(labels.howItWorks).performClick()

        verify(exactly = 1) { onReplayOnboarding() }
    }

    @Test
    fun matureToggleDispatchesOptIn() {
        setContent()

        composeTestRule.onNodeWithContentDescription(labels.matureSwitch).performClick()

        verify(exactly = 1) { viewModel.onSetMatureOptIn(true) }
    }

    @Test
    fun regionRowOpensPickerAndSelectsCountry() {
        setContent()

        composeTestRule.onNodeWithText(labels.region).performClick()
        // "United Kingdom" appears only inside the opened picker (US is the selected subtitle), so it's unambiguous.
        composeTestRule.onNodeWithText(UK.name).performClick()

        verify(exactly = 1) { viewModel.onCountrySelected(UK) }
    }

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

    private companion object {
        val US = Country("US", "United States")
        val UK = Country("GB", "United Kingdom")
    }
}
