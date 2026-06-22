package pm.bam.gamedeals.common.storage

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * A [KeyValueBackend] backed by the iOS Keychain (generic-password items scoped by [service]) — the
 * secure store for secrets like the ITAD auth token (#239), mirroring the Android Keystore-encrypted
 * backend. The Keychain encrypts items at rest under the device's hardware key; a missing item reads as
 * null (→ re-login for the refreshable auth token). Items are marked
 * `AfterFirstUnlockThisDeviceOnly` so they survive backgrounding but never sync off-device.
 *
 * NOTE: Kotlin/Native iOS target — cannot be compiled on the Linux dev box; verify with
 * `:iosApp:compileKotlinIosSimulatorArm64` on macOS (same caveat as SecureRandom.ios.kt).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainBackend(
    private val service: String,
) : KeyValueBackend {

    override fun readString(key: String): String? = memScoped {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceRef,
            kSecAttrAccount to accountRef,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val out = alloc<CFTypeRefVar>()
        try {
            if (SecItemCopyMatching(query, out.ptr) != errSecSuccess) return@memScoped null
            (CFBridgingRelease(out.value) as? NSData)
                ?.let { NSString.create(it, NSUTF8StringEncoding) as String? }
        } finally {
            CFRelease(query)
            CFBridgingRelease(serviceRef)
            CFBridgingRelease(accountRef)
        }
    }

    override fun writeString(key: String, value: String): Boolean = memScoped {
        remove(key)
        val data = value.encodeToByteArray().toNSData()
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        val dataRef = CFBridgingRetain(data)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceRef,
            kSecAttrAccount to accountRef,
            kSecValueData to dataRef,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        )
        try {
            SecItemAdd(query, null) == errSecSuccess
        } finally {
            CFRelease(query)
            CFBridgingRelease(serviceRef)
            CFBridgingRelease(accountRef)
            CFBridgingRelease(dataRef)
        }
    }

    override fun contains(key: String): Boolean = memScoped {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceRef,
            kSecAttrAccount to accountRef,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        try {
            SecItemCopyMatching(query, null) == errSecSuccess
        } finally {
            CFRelease(query)
            CFBridgingRelease(serviceRef)
            CFBridgingRelease(accountRef)
        }
    }

    override fun remove(key: String): Boolean = memScoped {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = CFBridgingRetain(key)
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceRef,
            kSecAttrAccount to accountRef,
        )
        try {
            val status = SecItemDelete(query)
            status == errSecSuccess || status == errSecItemNotFound
        } finally {
            CFRelease(query)
            CFBridgingRelease(serviceRef)
            CFBridgingRelease(accountRef)
        }
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

    /** Builds a (CoreFoundation, +1 retained) query dictionary from CF key/value pairs. */
    private fun MemScope.cfDictionaryOf(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
        val count = pairs.size
        val keys = allocArray<COpaquePointerVar>(count)
        val values = allocArray<COpaquePointerVar>(count)
        pairs.forEachIndexed { index, pair ->
            // Indexed-set has no candidate for an opaque-pointer array, so write through each slot's var.
            (keys + index)!!.pointed.value = pair.first
            (values + index)!!.pointed.value = pair.second
        }
        return CFDictionaryCreate(
            kCFAllocatorDefault,
            keys,
            values,
            count.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
    }
}
