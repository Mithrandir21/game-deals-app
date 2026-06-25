package pm.bam.gamedeals.common.imaging

import coil3.util.Logger as CoilLogger
import pm.bam.gamedeals.logging.LogLevel
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * JVM-specific guard for the assumption baked into the shared classifier: on Android, Ktor connectivity
 * failures are `java.io.IOException` subclasses, and `okio.IOException` is a typealias to it — so they
 * must land on the WARN (breadcrumb) arm, not the ERROR (issue) arm. commonTest can't reference these
 * `java.*` types, so this locks the behavior on the real JVM exception hierarchy.
 */
class CoilLoggingAndroidTest {

    @Test
    fun unknown_host_is_downgraded_to_warn() =
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Error, UnknownHostException("no DNS")))

    @Test
    fun socket_timeout_is_downgraded_to_warn() =
        assertEquals(LogLevel.WARN, coilLogLevel(CoilLogger.Level.Error, SocketTimeoutException("timed out")))
}
