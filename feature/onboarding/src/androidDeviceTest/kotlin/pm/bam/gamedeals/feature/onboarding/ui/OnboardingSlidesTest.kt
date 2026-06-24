package pm.bam.gamedeals.feature.onboarding.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.feature.onboarding.generated.resources.Res
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_analytics_enable
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_analytics_enabled
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_done
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_denied
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_enable
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_enabled
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_off
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_open_settings
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_page_indicator
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_action
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_signed_in_as
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signing_in

/**
 * Device (Compose UI) tests for the onboarding slides whose visible state and accessibility semantics have
 * been bug-prone. They render the (test-visible `internal`) slide composables directly with controlled
 * inputs, so the notification-permission state is deterministic rather than coupled to the host device.
 */
class OnboardingSlidesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val us = Country("US", "United States")
    private val gb = Country("GB", "United Kingdom")

    private lateinit var sem: Semantics

    // --- Notifications slide ------------------------------------------------------------------------

    @Test
    fun notifications_active_shows_alerts_on_only() {
        setContent { NotificationsSlide(enabled = true, permissionGranted = true, denied = false, onEnable = {}, onOpenSettings = {}) }

        composeTestRule.onNodeWithText(sem.alertsOn).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.turnOn).assertDoesNotExist()
        composeTestRule.onNodeWithText(sem.openSettings).assertDoesNotExist()
    }

    @Test
    fun notifications_permission_granted_opt_out_offers_turn_on() {
        setContent { NotificationsSlide(enabled = false, permissionGranted = true, denied = false, onEnable = {}, onOpenSettings = {}) }

        composeTestRule.onNodeWithText(sem.turnOn).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.openSettings).assertDoesNotExist()
        composeTestRule.onNodeWithText(sem.off).assertDoesNotExist()
    }

    @Test
    fun notifications_permission_off_shows_status_and_turn_on() {
        setContent { NotificationsSlide(enabled = false, permissionGranted = false, denied = false, onEnable = {}, onOpenSettings = {}) }

        // The "off" status is a polite live region so a change reaches TalkBack.
        composeTestRule.onNodeWithText(sem.off)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithText(sem.turnOn).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.openSettings).assertDoesNotExist()
    }

    @Test
    fun notifications_blocked_offers_open_settings() {
        var opened = false
        setContent { NotificationsSlide(enabled = false, permissionGranted = false, denied = true, onEnable = {}, onOpenSettings = { opened = true }) }

        composeTestRule.onNodeWithText(sem.denied)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithText(sem.turnOn).assertDoesNotExist()

        composeTestRule.onNodeWithText(sem.openSettings).assertIsDisplayed().performClick()
        assertTrue(opened)
    }

    @Test
    fun notifications_granted_after_settings_drops_open_settings_despite_stale_denied() {
        // Regression: returning from system settings with the permission granted must not keep showing the
        // "Open settings" deep-link just because the in-app denied flag is still set.
        setContent { NotificationsSlide(enabled = false, permissionGranted = true, denied = true, onEnable = {}, onOpenSettings = {}) }

        composeTestRule.onNodeWithText(sem.openSettings).assertDoesNotExist()
        composeTestRule.onNodeWithText(sem.turnOn).assertIsDisplayed()
    }

    // --- Sign-in slide ------------------------------------------------------------------------------

    @Test
    fun signin_signed_out_shows_sign_in_action() {
        setContent { SignInSlide(loggedIn = false, username = "", signingIn = false, onSignIn = {}, onLater = {}) }

        composeTestRule.onNodeWithText(sem.signInAction).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.signedInAsBob).assertDoesNotExist()
    }

    @Test
    fun signin_signed_in_shows_confirmation_and_done() {
        var done = false
        setContent { SignInSlide(loggedIn = true, username = "bob", signingIn = false, onSignIn = {}, onLater = { done = true }) }

        composeTestRule.onNodeWithText(sem.signedInAsBob).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.signInAction).assertDoesNotExist()

        composeTestRule.onNodeWithText(sem.done).assertIsDisplayed().performClick()
        assertTrue(done)
    }

    @Test
    fun signin_in_progress_button_is_labelled_and_disabled() {
        setContent { SignInSlide(loggedIn = false, username = "", signingIn = true, onSignIn = {}, onLater = {}) }

        // The spinner-only button still carries an accessible name and is disabled while in flight.
        composeTestRule.onNodeWithContentDescription(sem.signingIn).assertIsNotEnabled()
    }

    // --- Analytics consent slide --------------------------------------------------------------------

    @Test
    fun analytics_consent_off_offers_turn_on() {
        setContent { AnalyticsConsentSlide(enabled = false, onEnable = {}, onOpenPrivacyPolicy = {}) }

        composeTestRule.onNodeWithText(sem.analyticsTurnOn).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.analyticsOn).assertDoesNotExist()
    }

    @Test
    fun analytics_consent_on_shows_confirmation_only() {
        var enabled = false
        setContent { AnalyticsConsentSlide(enabled = true, onEnable = { enabled = true }, onOpenPrivacyPolicy = {}) }

        composeTestRule.onNodeWithText(sem.analyticsOn).assertIsDisplayed()
        composeTestRule.onNodeWithText(sem.analyticsTurnOn).assertDoesNotExist()
        assertTrue(!enabled) // already on: no enable affordance to click
    }

    // --- Page indicator -----------------------------------------------------------------------------

    @Test
    fun page_indicator_announces_step_position_as_one_node() {
        setContent { PageIndicator(pageCount = 7, currentPage = 2) }

        composeTestRule.onNodeWithContentDescription(sem.stepThreeOfSeven).assertIsDisplayed()
    }

    // --- Region picker ------------------------------------------------------------------------------

    @Test
    fun region_picker_rows_are_radio_buttons_and_select() {
        var selected: Country? = null
        setContent {
            OnboardingRegionPicker(
                countries = persistentListOf(us, gb),
                selectedCode = us.code,
                onSelect = { selected = it },
                onDismiss = {},
            )
        }

        // Each country row announces as a radio button (the a11y role), one per supported country.
        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertCountEquals(2)

        composeTestRule.onNodeWithText(gb.name).performClick()
        assertTrue(selected == gb)
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            sem = Semantics.load()
            GameDealsTheme { content() }
        }
    }

    private data class Semantics(
        val alertsOn: String,
        val turnOn: String,
        val off: String,
        val denied: String,
        val openSettings: String,
        val analyticsTurnOn: String,
        val analyticsOn: String,
        val signInAction: String,
        val signedInAsBob: String,
        val signingIn: String,
        val done: String,
        val stepThreeOfSeven: String,
    ) {
        companion object {
            @Composable
            fun load(): Semantics = Semantics(
                alertsOn = stringResource(Res.string.onboarding_notifications_enabled),
                turnOn = stringResource(Res.string.onboarding_notifications_enable),
                off = stringResource(Res.string.onboarding_notifications_off),
                denied = stringResource(Res.string.onboarding_notifications_denied),
                openSettings = stringResource(Res.string.onboarding_open_settings),
                analyticsTurnOn = stringResource(Res.string.onboarding_analytics_enable),
                analyticsOn = stringResource(Res.string.onboarding_analytics_enabled),
                signInAction = stringResource(Res.string.onboarding_signin_action),
                signedInAsBob = stringResource(Res.string.onboarding_signin_signed_in_as, "bob"),
                signingIn = stringResource(Res.string.onboarding_signing_in),
                done = stringResource(Res.string.onboarding_done),
                stepThreeOfSeven = stringResource(Res.string.onboarding_page_indicator, 3, 7),
            )
        }
    }
}
