package pm.bam.gamedeals.logging.featureflags

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Sentry reports carry the switch values, and follow a switch flipped mid-session. */
class SwitchesOnCrashReportsTest {

    private val scope: Scope = mockk(relaxed = true)

    private class Flags : FeatureFlags {
        val values = MutableStateFlow(FeatureFlag.entries.associateWith { it.default })
        override fun isEnabled(flag: FeatureFlag) = values.value.getValue(flag)
        override fun observe(flag: FeatureFlag): Flow<Boolean> = values.map { it.getValue(flag) }.distinctUntilChanged()
        override fun payload(flag: FeatureFlag): String? = null
        override fun observePayload(flag: FeatureFlag): Flow<String?> = flowOf(null)
        override fun refresh() = Unit
        override suspend fun awaitRefresh() = Unit
    }

    @Before
    fun setUp() {
        mockkObject(Sentry)
        every { Sentry.isEnabled() } returns true
        every { Sentry.configureScope(any()) } answers { firstArg<(Scope) -> Unit>().invoke(scope) }
    }

    @After
    fun tearDown() = unmockkObject(Sentry)

    @Test
    fun reports_list_every_switch_and_tag_the_ones_off_as_they_change() = runTest {
        val flags = Flags()
        backgroundScope.launch { reportSwitchesToSentry(flags) }
        runCurrent()

        verify { scope.setContext(SWITCHES_CONTEXT, switchReport(flags.values.value).context) }
        verify { scope.setTag(SWITCHES_OFF_TAG, "force_update") }

        flags.values.value = flags.values.value + (FeatureFlag.DealSwipe to false)
        runCurrent()

        verify { scope.setTag(SWITCHES_OFF_TAG, "force_update,deal_swipe") }
    }

    @Test
    fun nothing_is_set_when_sentry_is_off() = runTest {
        every { Sentry.isEnabled() } returns false

        reportSwitchesToSentry(Flags())

        verify(exactly = 0) { Sentry.configureScope(any()) }
    }
}
