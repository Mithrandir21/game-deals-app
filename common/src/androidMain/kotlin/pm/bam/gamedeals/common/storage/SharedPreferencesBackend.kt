package pm.bam.gamedeals.common.storage

import android.content.SharedPreferences
import androidx.annotation.WorkerThread

internal class SharedPreferencesBackend(
    private val prefs: SharedPreferences,
) : KeyValueBackend {

    override fun readString(key: String): String? = prefs.getString(key, null)

    // commit() rather than apply(): callers run this off-thread inside withContext(IO) and
    // expect a truthful Boolean reflecting whether the write actually succeeded.
    @WorkerThread
    override fun writeString(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()

    override fun contains(key: String): Boolean = prefs.contains(key)

    @WorkerThread
    override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()
}
