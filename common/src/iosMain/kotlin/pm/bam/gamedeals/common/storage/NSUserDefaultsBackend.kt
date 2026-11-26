package pm.bam.gamedeals.common.storage

import platform.Foundation.NSUserDefaults

internal class NSUserDefaultsBackend(
    private val defaults: NSUserDefaults,
) : KeyValueBackend {

    override fun readString(key: String): String? = defaults.stringForKey(key)

    // NSUserDefaults has no synchronous commit; `synchronize()` has been deprecated since
    // iOS 12 and the API exposes no failure signal — always return true.
    override fun writeString(key: String, value: String): Boolean {
        defaults.setObject(value, forKey = key)
        return true
    }

    override fun contains(key: String): Boolean = defaults.objectForKey(key) != null

    override fun remove(key: String): Boolean {
        defaults.removeObjectForKey(key)
        return true
    }
}
