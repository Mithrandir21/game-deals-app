package pm.bam.gamedeals.logging.implementations

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import io.sentry.kotlin.multiplatform.Scope
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.logging.LogLevel
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * JVM-host coverage for how log levels map onto Sentry: low levels become breadcrumbs, ERROR/FATAL become
 * captured events (exception when a throwable is present, otherwise a message). The global `Sentry` object
 * is intercepted with mockk (mirrors AndroidNotificationSchedulerTest in :domain).
 */
class SentryLoggingListenerTest {

    private val listener = SentryLoggingListener()

    @Before
    fun setUp() {
        mockkObject(Sentry)
        every { Sentry.addBreadcrumb(any()) } returns Unit
        every { Sentry.captureException(any(), any()) } returns mockk()
        every { Sentry.captureMessage(any(), any()) } returns mockk()
        every { Sentry.isEnabled() } returns true
    }

    @After
    fun tearDown() = unmockkObject(Sentry)

    @Test
    fun warn_becomes_a_warning_breadcrumb_carrying_message_and_tag() {
        val crumb = slot<Breadcrumb>()

        listener.onLog(LogLevel.WARN, "low disk", tag = "Storage", throwable = null)

        verify(exactly = 1) { Sentry.addBreadcrumb(capture(crumb)) }
        assertEquals(SentryLevel.WARNING, crumb.captured.level)
        assertEquals("low disk", crumb.captured.message)
        assertEquals("Storage", crumb.captured.category)
        verify(exactly = 0) { Sentry.captureException(any(), any()) }
        verify(exactly = 0) { Sentry.captureMessage(any(), any()) }
    }

    @Test
    fun info_becomes_an_info_breadcrumb() {
        val crumb = slot<Breadcrumb>()

        listener.onLog(LogLevel.INFO, "started", tag = null, throwable = null)

        verify { Sentry.addBreadcrumb(capture(crumb)) }
        assertEquals(SentryLevel.INFO, crumb.captured.level)
    }

    @Test
    fun verbose_and_debug_are_not_sent_to_sentry() {
        listener.onLog(LogLevel.VERBOSE, "v", tag = null, throwable = null)
        listener.onLog(LogLevel.DEBUG, "d", tag = null, throwable = null)

        verify(exactly = 0) { Sentry.addBreadcrumb(any()) }
    }

    @Test
    fun error_with_a_throwable_captures_the_exception_at_error_level() {
        val boom = IllegalStateException("boom")
        val scopeBlock = slot<(Scope) -> Unit>()

        listener.onLog(LogLevel.ERROR, "ignored-message", tag = null, throwable = boom)

        verify(exactly = 1) { Sentry.captureException(boom, capture(scopeBlock)) }
        verify(exactly = 0) { Sentry.captureMessage(any(), any()) }
        // The scope callback stamps the level — apply it to a scope and confirm.
        val scope = mockk<Scope>(relaxed = true)
        scopeBlock.captured(scope)
        verify { scope.level = SentryLevel.ERROR }
    }

    @Test
    fun error_from_a_connectivity_failure_becomes_a_breadcrumb_not_an_issue() {
        // Offline users shouldn't manufacture Sentry issues: the app degraded exactly as designed, it just
        // had no network (Sentry KOTLIN-M/K/P/G/H were all `handled`).
        val crumb = slot<Breadcrumb>()

        listener.onLog(LogLevel.ERROR, "deals fetch failed", tag = "DealsRepo", throwable = UnknownHostException("api.isthereanydeal.com"))

        verify(exactly = 1) { Sentry.addBreadcrumb(capture(crumb)) }
        assertEquals(SentryLevel.WARNING, crumb.captured.level)
        assertEquals("deals fetch failed", crumb.captured.message)
        assertEquals("DealsRepo", crumb.captured.category)
        // The breadcrumb is the only record a downgraded failure leaves, so it has to say which failure
        // it was — "deals fetch failed" alone can't distinguish offline from a stalled socket.
        assertEquals("UnknownHostException", crumb.captured.getData()?.get("exception"))
        assertEquals("api.isthereanydeal.com", crumb.captured.getData()?.get("reason"))
        verify(exactly = 0) { Sentry.captureException(any(), any()) }
        verify(exactly = 0) { Sentry.captureMessage(any(), any()) }
    }

    @Test
    fun a_connectivity_failure_wrapped_in_an_app_exception_is_still_recognised() {
        listener.onLog(
            LogLevel.ERROR,
            "wrapped",
            tag = null,
            throwable = IllegalStateException("load failed", SocketTimeoutException("read timed out")),
        )

        verify(exactly = 1) { Sentry.addBreadcrumb(any()) }
        verify(exactly = 0) { Sentry.captureException(any(), any()) }
    }

    @Test
    fun a_genuine_defect_still_captures_even_alongside_the_network_downgrade() {
        val boom = IllegalStateException("query returned no rows")

        listener.onLog(LogLevel.ERROR, "store lookup", tag = null, throwable = boom)

        verify(exactly = 1) { Sentry.captureException(boom, any()) }
        verify(exactly = 0) { Sentry.addBreadcrumb(any()) }
    }

    @Test
    fun fatal_is_never_downgraded_even_for_a_connectivity_failure() {
        val offline = UnknownHostException("api.isthereanydeal.com")

        listener.onLog(LogLevel.FATAL, "fatal", tag = null, throwable = offline)

        verify(exactly = 1) { Sentry.captureException(offline, any()) }
        verify(exactly = 0) { Sentry.addBreadcrumb(any()) }
    }

    @Test
    fun error_without_a_throwable_captures_the_message_at_error_level() {
        val scopeBlock = slot<(Scope) -> Unit>()

        listener.onLog(LogLevel.ERROR, "no throwable", tag = null, throwable = null)

        verify(exactly = 1) { Sentry.captureMessage("no throwable", capture(scopeBlock)) }
        verify(exactly = 0) { Sentry.captureException(any(), any()) }
        val scope = mockk<Scope>(relaxed = true)
        scopeBlock.captured(scope)
        verify { scope.level = SentryLevel.ERROR }
    }

    @Test
    fun fatal_with_a_throwable_captures_the_exception_at_fatal_level() {
        val boom = IllegalStateException("fatal boom")
        val scopeBlock = slot<(Scope) -> Unit>()

        listener.onLog(LogLevel.FATAL, "ignored", tag = null, throwable = boom)

        verify(exactly = 1) { Sentry.captureException(boom, capture(scopeBlock)) }
        verify(exactly = 0) { Sentry.captureMessage(any(), any()) }
        val scope = mockk<Scope>(relaxed = true)
        scopeBlock.captured(scope)
        verify { scope.level = SentryLevel.FATAL }
    }

    @Test
    fun fatal_without_a_throwable_captures_the_message_at_fatal_level() {
        val scopeBlock = slot<(Scope) -> Unit>()

        listener.onLog(LogLevel.FATAL, "fatal note", tag = null, throwable = null)

        verify(exactly = 1) { Sentry.captureMessage("fatal note", capture(scopeBlock)) }
        val scope = mockk<Scope>(relaxed = true)
        scopeBlock.captured(scope)
        verify { scope.level = SentryLevel.FATAL }
    }

    @Test
    fun onFatalThrowable_captures_the_exception_at_fatal_level() {
        val crash = RuntimeException("crash")
        val scopeBlock = slot<(Scope) -> Unit>()

        listener.onFatalThrowable(tag = "Boot", throwable = crash)

        verify(exactly = 1) { Sentry.captureException(crash, capture(scopeBlock)) }
        val scope = mockk<Scope>(relaxed = true)
        scopeBlock.captured(scope)
        verify { scope.level = SentryLevel.FATAL }
    }

    @Test
    fun isEnabled_delegates_to_the_sdk() {
        every { Sentry.isEnabled() } returns false
        assertTrue(!listener.isEnabled())

        every { Sentry.isEnabled() } returns true
        assertTrue(listener.isEnabled())
    }
}
