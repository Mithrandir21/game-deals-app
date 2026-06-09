package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/**
 * Fills [size] bytes from the system CSPRNG via `SecRandomCopyBytes` (Security framework).
 *
 * NOTE: Kotlin/Native iOS target — cannot be compiled on the Linux dev box; verify with
 * `:iosApp:compileKotlinIosSimulatorArm64` on macOS.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun secureRandomBytes(size: Int): ByteArray {
    if (size == 0) return ByteArray(0)
    val bytes = ByteArray(size)
    bytes.usePinned { pinned ->
        val status = SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0))
        // errSecSuccess == 0
        check(status == 0) { "SecRandomCopyBytes failed with OSStatus $status" }
    }
    return bytes
}
