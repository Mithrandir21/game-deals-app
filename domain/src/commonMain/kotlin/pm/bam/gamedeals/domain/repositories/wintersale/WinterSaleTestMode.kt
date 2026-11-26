package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.version.AppInfo

/**
 * Developer switch that points the Winter Sale event check at the test document
 * (`/events/winter-sale-2026-test.json`, event ID `steam-winter-2026-test`) instead of the real one.
 *
 * It lets the whole guide, its Home card and the reminder be tested on a phone after the app is public, while the
 * real document stays switched off for everyone else. It is set from the developer screen, which only debug builds
 * show, and [isOn] is independently gated on [AppInfo.isDebug], so a release build always reads the real document
 * even if a value somehow ended up in storage.
 *
 * The test document has its own event ID, so its reminder record is replaced, and any pending test reminder
 * cancelled, as soon as the real document is read again.
 */
interface WinterSaleTestMode {
    /** Emits whether the test document is in use. Always false in a release build. */
    fun observe(): Flow<Boolean>

    /** Whether the next event check should read the test document. Always false in a release build. */
    suspend fun isOn(): Boolean

    suspend fun set(on: Boolean)
}

internal class WinterSaleTestModeImpl(private val storage: Storage, private val appInfo: AppInfo) : WinterSaleTestMode {

    private val stored = MutableStateFlow<Boolean?>(null)

    override fun observe(): Flow<Boolean> = stored
        .onStart { if (stored.value == null) stored.value = load() }
        .filterNotNull()
        .map { it && appInfo.isDebug }

    override suspend fun isOn(): Boolean {
        if (!appInfo.isDebug) return false
        return stored.value ?: load().also { stored.value = it }
    }

    override suspend fun set(on: Boolean) {
        if (on) storage.save(TEST_MODE_KEY, true) else storage.remove(TEST_MODE_KEY)
        stored.value = on
    }

    private suspend fun load(): Boolean = try {
        storage.getNullable<Boolean>(TEST_MODE_KEY) == true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }
}

internal const val TEST_MODE_KEY = "debug_winter_sale_test_document"
