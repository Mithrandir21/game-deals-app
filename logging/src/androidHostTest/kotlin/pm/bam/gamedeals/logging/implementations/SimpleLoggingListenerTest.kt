package pm.bam.gamedeals.logging.implementations

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pm.bam.gamedeals.logging.LogLevel

/**
 * JVM-host coverage for the level → android.util.Log routing. `Log` is a static Android class stubbed at
 * runtime (throws "not mocked" otherwise), intercepted with mockkStatic.
 */
class SimpleLoggingListenerTest {

    private val listener = SimpleLoggingListener()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any(), any(), any()) } returns 0
        every { Log.d(any(), any(), any()) } returns 0
        every { Log.i(any(), any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.wtf(any(), any<String>(), any()) } returns 0
    }

    @After
    fun tearDown() = unmockkStatic(Log::class)

    @Test
    fun each_level_routes_to_its_log_method_forwarding_tag_message_and_throwable() {
        val t = RuntimeException("x")

        listener.onLog(LogLevel.VERBOSE, "mv", tag = "T", throwable = t)
        listener.onLog(LogLevel.DEBUG, "md", tag = "T", throwable = t)
        listener.onLog(LogLevel.INFO, "mi", tag = "T", throwable = t)
        listener.onLog(LogLevel.WARN, "mw", tag = "T", throwable = t)
        listener.onLog(LogLevel.ERROR, "me", tag = "T", throwable = t)
        listener.onLog(LogLevel.FATAL, "mf", tag = "T", throwable = t)

        verify(exactly = 1) { Log.v("T", "mv", t) }
        verify(exactly = 1) { Log.d("T", "md", t) }
        verify(exactly = 1) { Log.i("T", "mi", t) }
        verify(exactly = 1) { Log.w("T", "mw", t) }
        verify(exactly = 1) { Log.e("T", "me", t) }
        verify(exactly = 1) { Log.wtf("T", "mf", t) }
    }

    @Test
    fun a_null_tag_falls_back_to_the_class_simple_name() {
        listener.onLog(LogLevel.INFO, "msg", tag = null, throwable = null)

        verify(exactly = 1) { Log.i("SimpleLoggingListener", "msg", null) }
    }

    @Test
    fun every_level_falls_back_to_the_class_simple_name_when_tag_is_null() {
        val fallback = "SimpleLoggingListener"

        listener.onLog(LogLevel.VERBOSE, "mv", tag = null, throwable = null)
        listener.onLog(LogLevel.DEBUG, "md", tag = null, throwable = null)
        listener.onLog(LogLevel.WARN, "mw", tag = null, throwable = null)
        listener.onLog(LogLevel.ERROR, "me", tag = null, throwable = null)
        listener.onLog(LogLevel.FATAL, "mf", tag = null, throwable = null)

        verify(exactly = 1) { Log.v(fallback, "mv", null) }
        verify(exactly = 1) { Log.d(fallback, "md", null) }
        verify(exactly = 1) { Log.w(fallback, "mw", null) }
        verify(exactly = 1) { Log.e(fallback, "me", null) }
        verify(exactly = 1) { Log.wtf(fallback, "mf", null) }
    }

    @Test
    fun onFatalThrowable_writes_a_wtf_with_the_fatal_crash_marker() {
        val crash = RuntimeException("crash")

        listener.onFatalThrowable(tag = "Boot", throwable = crash)

        verify(exactly = 1) { Log.wtf("Boot", "Fatal crash", crash) }
    }

    @Test
    fun onFatalThrowable_falls_back_to_the_class_simple_name_when_tag_is_null() {
        val crash = RuntimeException("crash")

        listener.onFatalThrowable(tag = null, throwable = crash)

        verify(exactly = 1) { Log.wtf("SimpleLoggingListener", "Fatal crash", crash) }
    }

    @Test
    fun isEnabled_is_true_and_tag_is_the_class_name() {
        assertTrue(listener.isEnabled())
        assertEquals("SimpleLoggingListener", listener.getLoggerTag())
    }
}
