package pm.bam.gamedeals.domain.repositories.appupdate

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save

/**
 * A locally-stored stand-in for the `force_update` flag payload, so the minimum-version gate can be exercised
 * on a device **without** a remote flag provider.
 *
 * This exists because builds without a remote flag provider bind `NoOpFeatureFlags` — every flag resolves to its
 * default, so the prompt could otherwise never appear outside a release talking to a live provider. That would
 * mean shipping this UI having never seen it run.
 *
 * It lives in `:domain` rather than in `:feature:appupdate` for two reasons. It is persisted state, which is
 * what `:domain` is for (it sits beside `SettingsRepository`, sharing the same [Storage] seam). And it is
 * written by one feature and read by another — the Account hub sets it, the update gate consumes it — so
 * parking it here keeps `:feature:account` from having to depend on `:feature:appupdate`.
 *
 * It is deliberately *not* a decorator inside `:logging`, which sits below `:common` in the module graph and
 * cannot reach [Storage] at all. Consumption is gated on `AppInfo.isDebug`, so a release build never reads it
 * even if a value somehow ended up in storage.
 *
 * The stored value is the same JSON a remote flag payload would carry:
 * `{"minimum_version": "99.0.0", "blocking": true}`. Setting `null` clears it and hands control back to the
 * real flag.
 */
interface AppUpdateDebugOverride {

    /** Emits the stored override JSON, or `null` when none is set. */
    fun observe(): Flow<String?>

    /** Sets the override payload JSON, or clears it with `null`. */
    suspend fun set(json: String?)
}

internal class AppUpdateDebugOverrideImpl(private val storage: Storage) : AppUpdateDebugOverride {

    // Wrapper for the same reason SettingsRepositoryImpl needs one: "not yet read" and "read, but unset" are
    // both null and would otherwise re-read storage on every collection.
    private val override = MutableStateFlow<Snapshot?>(null)

    override fun observe(): Flow<String?> =
        override
            .onStart { if (override.value == null) override.value = loadFromStorage() }
            .filterNotNull()
            .map { it.json }

    override suspend fun set(json: String?) {
        if (json == null) storage.remove(OVERRIDE_KEY) else storage.save(OVERRIDE_KEY, json)
        override.value = Snapshot(json)
    }

    private suspend fun loadFromStorage(): Snapshot =
        Snapshot(runCatching { storage.getNullable<String>(OVERRIDE_KEY) }.getOrNull())

    private class Snapshot(val json: String?)
}

internal const val OVERRIDE_KEY = "debug_force_update_override"
