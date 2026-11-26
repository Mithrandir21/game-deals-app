package pm.bam.gamedeals.logging

import java.io.IOException

/**
 * On the JVM every connectivity failure the app can hit is an [IOException]: `UnknownHostException`
 * (no DNS — the offline case), `SocketException`/`SocketTimeoutException` (dropped or stalled
 * connection), `SSLException` (interrupted handshake, common on captive-portal Wi-Fi). Matching the
 * base type covers the family without enumerating OEM- and engine-specific leaves.
 */
internal actual fun Throwable.isPlatformNetworkFailure(): Boolean = this is IOException
