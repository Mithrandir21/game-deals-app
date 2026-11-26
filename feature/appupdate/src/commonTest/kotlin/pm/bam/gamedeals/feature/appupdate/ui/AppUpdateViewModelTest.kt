@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.appupdate.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.appupdate.AppUpdateDebugOverride
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.testing.FakeFeatureFlags
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val NOW = 1_000_000_000_000L
private const val DAY = 24L * 60 * 60 * 1000

class AppUpdateViewModelTest : MainDispatcherTest() {

    @BeforeTest fun setUp() = installMainDispatcher()

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel(
        currentVersion: String = "1.1.3",
        flagEnabled: Boolean = true,
        payload: String? = null,
        dismissedAt: Long? = null,
        now: Long = NOW,
        isDebug: Boolean = false,
        overrideJson: String? = null,
    ): AppUpdateViewModel {
        val flags = FakeFeatureFlags(initial = mapOf(FeatureFlag.ForceUpdate to flagEnabled))
        flags.setPayload(FeatureFlag.ForceUpdate, payload)

        val settings = mock<SettingsRepository>(MockMode.autoUnit) {
            every { observeUpdatePromptDismissedAt() } returns flowOf(dismissedAt)
        }
        val override = mock<AppUpdateDebugOverride>(MockMode.autoUnit) {
            every { observe() } returns flowOf(overrideJson)
        }
        return AppUpdateViewModel(
            featureFlags = flags,
            settings = settings,
            appInfo = AppInfo(versionName = currentVersion, versionCode = 1L, storeId = "pm.bam.gamedeals", isDebug = isDebug),
            clock = Clock { now },
            analytics = mock<Analytics>(MockMode.autoUnit),
            debugOverride = override,
        )
    }

    /**
     * Settles [AppUpdateViewModel.state] and returns its resolved value.
     *
     * The state is a `WhileSubscribed` `stateIn`, so it sits at its `Hidden` seed until something actually
     * collects it. Reading `.value` straight off it would assert the seed, and every "should prompt" case
     * would pass for the wrong reason.
     */
    private fun TestScope.resolved(viewModel: AppUpdateViewModel): AppUpdateState {
        val emissions = viewModel.state.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()
        return emissions.last()
    }

    // --- happy paths ---

    @Test
    fun `prompts when below the floor`() = runTest {
        assertEquals(
            AppUpdateState.Prompt("1.2.0", blocking = false),
            resolved(viewModel(currentVersion = "1.1.3", payload = """{"minimum_version":"1.2.0"}""")),
        )
    }

    @Test
    fun `blocking flag produces a blocking prompt`() = runTest {
        assertEquals(
            AppUpdateState.Prompt("1.2.0", blocking = true),
            resolved(viewModel(payload = """{"minimum_version":"1.2.0","blocking":true}""")),
        )
    }

    @Test
    fun `blocking defaults to false when the field is absent`() = runTest {
        val state = resolved(viewModel(payload = """{"minimum_version":"1.2.0"}"""))
        assertEquals(false, (state as AppUpdateState.Prompt).blocking)
    }

    @Test
    fun `hidden at or above the floor`() = runTest {
        assertEquals(
            AppUpdateState.Hidden,
            resolved(viewModel(currentVersion = "1.2.0", payload = """{"minimum_version":"1.2.0"}""")),
        )
        assertEquals(
            AppUpdateState.Hidden,
            resolved(viewModel(currentVersion = "1.3.0", payload = """{"minimum_version":"1.2.0"}""")),
        )
    }

    @Test
    fun `hidden when the flag is off even with a valid payload`() = runTest {
        assertEquals(
            AppUpdateState.Hidden,
            resolved(viewModel(flagEnabled = false, payload = """{"minimum_version":"9.9.9","blocking":true}""")),
        )
    }

    @Test
    fun `unknown payload keys are ignored rather than failing the parse`() = runTest {
        assertEquals(
            AppUpdateState.Prompt("1.2.0", blocking = true),
            resolved(viewModel(payload = """{"minimum_version":"1.2.0","blocking":true,"future_field":"whatever"}""")),
        )
    }

    // --- fail-open paths: a remote-config mistake must never gate anyone ---

    @Test
    fun `hidden when the payload is missing`() = runTest {
        assertEquals(AppUpdateState.Hidden, resolved(viewModel(payload = null)))
    }

    @Test
    fun `hidden when the payload is malformed or incomplete`() = runTest {
        listOf(
            "not json at all",
            "{",
            "{}",
            """{"minimum_version":""}""",
            """{"minimum_version":"not a version"}""",
            """{"minimum_version":null}""",
        ).forEach { payload ->
            assertEquals(AppUpdateState.Hidden, resolved(viewModel(payload = payload)), "payload: $payload")
        }
    }

    @Test
    fun `hidden when the running version is unparseable`() = runTest {
        assertEquals(
            AppUpdateState.Hidden,
            resolved(viewModel(currentVersion = "garbage", payload = """{"minimum_version":"1.2.0"}""")),
        )
    }

    // --- the 24h nudge window ---

    @Test
    fun `nudge stays hidden within a day of dismissal`() = runTest {
        assertEquals(
            AppUpdateState.Hidden,
            resolved(viewModel(payload = """{"minimum_version":"1.2.0"}""", dismissedAt = NOW - DAY + 1)),
        )
    }

    @Test
    fun `nudge returns after a day`() = runTest {
        val state = resolved(viewModel(payload = """{"minimum_version":"1.2.0"}""", dismissedAt = NOW - DAY))
        assertTrue(state is AppUpdateState.Prompt)
    }

    @Test
    fun `blocking gate ignores a recent dismissal`() = runTest {
        assertEquals(
            AppUpdateState.Prompt("1.2.0", blocking = true),
            resolved(viewModel(payload = """{"minimum_version":"1.2.0","blocking":true}""", dismissedAt = NOW - 1)),
        )
    }

    @Test
    fun `a dismissal in the future is treated as clock skew and still prompts`() = runTest {
        // Otherwise moving the device clock forward, dismissing, then moving it back would silence the nudge
        // permanently.
        val state = resolved(viewModel(payload = """{"minimum_version":"1.2.0"}""", dismissedAt = NOW + DAY))
        assertTrue(state is AppUpdateState.Prompt)
    }

    @Test
    fun `dismissing records the current time`() = runTest {
        val settings = mock<SettingsRepository>(MockMode.autoUnit) {
            every { observeUpdatePromptDismissedAt() } returns flowOf(null)
        }
        everySuspend { settings.setUpdatePromptDismissedAt(any()) } returns Unit
        val vm = AppUpdateViewModel(
            featureFlags = FakeFeatureFlags(),
            settings = settings,
            appInfo = AppInfo("1.1.3", 1L, "pm.bam.gamedeals", isDebug = false),
            clock = Clock { NOW },
            analytics = mock<Analytics>(MockMode.autoUnit),
            debugOverride = mock<AppUpdateDebugOverride>(MockMode.autoUnit) { every { observe() } returns flowOf(null) },
        )

        vm.onDismissed(AppUpdateState.Prompt("1.2.0", blocking = false))
        advanceUntilIdle()

        verifySuspend { settings.setUpdatePromptDismissedAt(NOW) }
    }

    // --- the debug override ---

    @Test
    fun `debug override stands in for an absent remote payload`() = runTest {
        assertEquals(
            AppUpdateState.Prompt("9.9.9", blocking = true),
            resolved(
                viewModel(
                    flagEnabled = false,
                    payload = null,
                    isDebug = true,
                    overrideJson = """{"minimum_version":"9.9.9","blocking":true}""",
                )
            ),
        )
    }

    @Test
    fun `debug override is ignored in a release build`() = runTest {
        assertEquals(
            AppUpdateState.Hidden,
            resolved(
                viewModel(
                    flagEnabled = false,
                    payload = null,
                    isDebug = false,
                    overrideJson = """{"minimum_version":"9.9.9","blocking":true}""",
                )
            ),
        )
    }
}
