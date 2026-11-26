package pm.bam.gamedeals.feature.appupdate.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.common.version.isBelowMinimum
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.appupdate.AppUpdateDebugOverride
import pm.bam.gamedeals.feature.appupdate.parseForceUpdateConfig
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * Decides whether the running build is below the remotely published minimum version, and therefore whether the
 * app shell should show the update prompt.
 *
 * The whole thing is built to **fail open**. A flag that is off, a payload that is absent or malformed, a
 * version string on either side that won't parse, or an exception anywhere in the chain all resolve to
 * [AppUpdateState.Hidden]. Getting this wrong in the other direction would brick the app for everyone via a
 * remote-config typo, with no way to recover except shipping a new release — so every unknown means "don't
 * prompt".
 *
 * Flag reads work regardless of analytics consent (opt-out suppresses `capture`, not flag loading), so the
 * gate reaches users who declined analytics. The analytics events emitted here are ordinary events and stay
 * consent-gated, which is the intended asymmetry.
 */
internal class AppUpdateViewModel(
    private val featureFlags: FeatureFlags,
    private val settings: SettingsRepository,
    private val appInfo: AppInfo,
    private val clock: Clock,
    private val analytics: Analytics,
    private val debugOverride: AppUpdateDebugOverride,
) : ViewModel() {

    /** The store listing id to hand to `PlatformActions.openStoreListing`; may be empty (see [AppInfo]). */
    val storeId: String = appInfo.storeId

    // In a debug build a locally-set override stands in for the remote payload, since builds without a flag
    // provider bind NoOpFeatureFlags and would otherwise never see one. Release builds never read it.
    private val overrides = if (appInfo.isDebug) debugOverride.observe() else flowOf(null)

    val state: StateFlow<AppUpdateState> =
        combine(
            featureFlags.observe(FeatureFlag.ForceUpdate),
            featureFlags.observePayload(FeatureFlag.ForceUpdate),
            settings.observeUpdatePromptDismissedAt(),
            overrides,
        ) { enabled, payload, dismissedAt, override ->
            // An override implies "on": the flag itself is stuck at its default in a debug build.
            if (override != null) resolve(enabled = true, payloadJson = override, dismissedAtMillis = dismissedAt)
            else resolve(enabled, payload, dismissedAt)
        }
            .distinctUntilChanged()
            .catch { emit(AppUpdateState.Hidden) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUpdateState.Hidden)

    private fun resolve(enabled: Boolean, payloadJson: String?, dismissedAtMillis: Long?): AppUpdateState {
        if (!enabled) return AppUpdateState.Hidden
        val config = parseForceUpdateConfig(payloadJson) ?: return AppUpdateState.Hidden
        if (!isBelowMinimum(current = appInfo.versionName, minimum = config.minimumVersion)) return AppUpdateState.Hidden

        // A blocking gate ignores any past dismissal — there is no dismiss button to have pressed.
        if (config.blocking) return AppUpdateState.Prompt(config.minimumVersion, blocking = true)

        if (dismissedAtMillis != null) {
            val elapsed = clock.nowMillis() - dismissedAtMillis
            // A negative elapsed means the stored dismissal is in the future — the device clock moved
            // backwards. Treat that as "no valid dismissal" and prompt, rather than staying silent forever.
            if (elapsed in 0 until DISMISSAL_WINDOW_MILLIS) return AppUpdateState.Hidden
        }
        return AppUpdateState.Prompt(config.minimumVersion, blocking = false)
    }

    fun onPromptShown(prompt: AppUpdateState.Prompt) =
        analytics.capture(AnalyticsEvents.APP_UPDATE_PROMPT_SHOWN, prompt.properties())

    fun onUpdateTapped(prompt: AppUpdateState.Prompt) =
        analytics.capture(AnalyticsEvents.APP_UPDATE_PROMPT_UPDATE_TAPPED, prompt.properties())

    /**
     * Records a dismissal of the *nudge* variant, silencing it for [DISMISSAL_WINDOW_MILLIS]. Never reachable
     * from the blocking variant, which renders no dismiss affordance.
     */
    fun onDismissed(prompt: AppUpdateState.Prompt) {
        analytics.capture(AnalyticsEvents.APP_UPDATE_PROMPT_DISMISSED, prompt.properties())
        viewModelScope.launch { settings.setUpdatePromptDismissedAt(clock.nowMillis()) }
    }

    private fun AppUpdateState.Prompt.properties(): Map<String, Any> =
        mapOf("minimum_version" to minimumVersion, "blocking" to blocking)
}

/** How long a dismissed nudge stays silent: one day. */
internal const val DISMISSAL_WINDOW_MILLIS: Long = 24L * 60 * 60 * 1000
