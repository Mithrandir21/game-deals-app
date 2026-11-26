package pm.bam.gamedeals.domain.repositories.featureflags

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.warn

/** Fetches the switch file, `GET https://gamedeals.bam.pm/api/flags/v1` (`store/site/flags-routes.mjs`), as text. Throws on a failed call. */
interface FeatureFlagsSource {
    suspend fun fetch(): String
}

/** Debug builds only: force a switch on or off on this phone, whatever the switch file says. */
interface FeatureFlagOverrides {

    /** The switches forced on this phone. Always empty in a release build. */
    fun observeOverrides(): Flow<Map<FeatureFlag, Boolean>>

    /** Force [flag] on or off, or follow the switch file again with null. Ignored in a release build. */
    suspend fun setOverride(flag: FeatureFlag, enabled: Boolean?)

    /** Fetch the switch file now, even within [RemoteFeatureFlags.REFRESH_INTERVAL_MS] of the last fetch. */
    suspend fun fetchNow()
}

/** One switch as the switch file last said: on or off, and its settings as JSON text when it carries any. */
@Serializable
internal data class RemoteFlag(val enabled: Boolean, val payload: String? = null)

/**
 * The apps' [FeatureFlags]: the Worker's switch file, so a feature can be turned off for everyone without a release.
 *
 * - **Before the first load** every switch takes its [FeatureFlag.default]. The last good values are saved, so from
 *   the next start they apply at once, offline included.
 * - **A failed fetch** (offline, a 503 for a mistyped file, an unreadable answer) keeps the values as they were.
 * - **A good fetch** replaces them all: a switch the file doesn't list goes back to its default. Keys this app doesn't
 *   know are ignored.
 * - **Fetches** happen at most once per [REFRESH_INTERVAL_MS]; [refresh] and [awaitRefresh] skip a fetch inside it.
 * - **Debug builds** may force switches ([FeatureFlagOverrides]); release builds ignore any stored override.
 */
internal class RemoteFeatureFlags(
    private val source: FeatureFlagsSource,
    private val storage: Storage,
    private val json: Json,
    private val clock: Clock,
    private val logger: Logger,
    private val isDebug: Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : FeatureFlags, FeatureFlagOverrides {

    private val remote = MutableStateFlow<Map<String, RemoteFlag>>(emptyMap())
    private val overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    // Guards the saved-copy restore, fetches and override writes, so a fetch can't be overwritten by an older copy.
    private val mutex = Mutex()
    private var restored = false
    private var fetchedAtEpochMs: Long? = null

    init {
        scope.launch { mutex.withLock { restore() } }
    }

    override fun isEnabled(flag: FeatureFlag): Boolean = enabled(remote.value, overrides.value, flag)

    override fun observe(flag: FeatureFlag): Flow<Boolean> =
        combine(remote, overrides) { values, forced -> enabled(values, forced, flag) }.distinctUntilChanged()

    override fun payload(flag: FeatureFlag): String? = remote.value[flag.key]?.payload

    override fun observePayload(flag: FeatureFlag): Flow<String?> = remote.map { it[flag.key]?.payload }.distinctUntilChanged()

    override fun refresh() {
        scope.launch { awaitRefresh() }
    }

    override suspend fun awaitRefresh() = load(force = false)

    override suspend fun fetchNow() = load(force = true)

    override fun observeOverrides(): Flow<Map<FeatureFlag, Boolean>> = overrides.map { forced ->
        FeatureFlag.entries.mapNotNull { flag -> forced[flag.key]?.let { flag to it } }.toMap()
    }

    override suspend fun setOverride(flag: FeatureFlag, enabled: Boolean?) {
        if (!isDebug) return
        mutex.withLock {
            restore()
            val next = if (enabled == null) overrides.value - flag.key else overrides.value + (flag.key to enabled)
            overrides.value = next
            try {
                storage.save(OVERRIDES_KEY, next)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                warn(logger, error) { "A switch override couldn't be saved" }
            }
        }
    }

    private suspend fun load(force: Boolean) = mutex.withLock {
        restore()
        val now = clock.nowMillis()
        val last = fetchedAtEpochMs
        if (!force && last != null && now - last in 0 until REFRESH_INTERVAL_MS) return@withLock
        val flags = try {
            parseFlags(source.fetch(), json)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warn(logger, error) { "The feature switches couldn't be loaded" }
            return@withLock
        }
        remote.value = flags
        fetchedAtEpochMs = now
        try {
            storage.save(SAVED_KEY, SavedFlags(now, flags))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warn(logger, error) { "The feature switches couldn't be saved" }
        }
    }

    /** Puts back the values saved by the last good fetch, and a debug build's overrides. Once; callers hold [mutex]. */
    private suspend fun restore() {
        if (restored) return
        restored = true
        try {
            storage.getNullable<SavedFlags>(SAVED_KEY)?.let { saved ->
                remote.value = saved.flags
                fetchedAtEpochMs = saved.fetchedAtEpochMs
            }
            if (isDebug) overrides.value = storage.getNullable<Map<String, Boolean>>(OVERRIDES_KEY) ?: emptyMap()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warn(logger, error) { "The saved feature switches couldn't be read" }
        }
    }

    private fun enabled(values: Map<String, RemoteFlag>, forced: Map<String, Boolean>, flag: FeatureFlag): Boolean =
        forced[flag.key] ?: values[flag.key]?.enabled ?: flag.default

    @Serializable
    private data class SavedFlags(val fetchedAtEpochMs: Long, val flags: Map<String, RemoteFlag>)

    companion object {
        /** The switch file is fetched at most this often (15 minutes). */
        const val REFRESH_INTERVAL_MS: Long = 15 * 60 * 1000

        private const val SAVED_KEY = "feature_switches"
        private const val OVERRIDES_KEY = "feature_switch_overrides"
    }
}

/**
 * Reads the switch file: `{"schemaVersion": 1, "flags": {"<key>": true | false | {"enabled": bool, "payload": {…}}}}`.
 * A file of another schema, or not a switch file at all, throws (the current values stay). A single switch that can't
 * be read is left out, so it takes its default, rather than failing the rest.
 */
internal fun parseFlags(text: String, json: Json): Map<String, RemoteFlag> {
    val root = json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("Not a switch file")
    val schema = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
    require(schema == 1) { "Unsupported switch file schema $schema" }
    val flags = root["flags"] as? JsonObject ?: throw IllegalArgumentException("The switch file has no flags")
    return buildMap {
        for ((key, value) in flags) {
            when (value) {
                is JsonPrimitive -> value.strictBoolean()?.let { put(key, RemoteFlag(it)) }
                is JsonObject -> {
                    val enabled = (value["enabled"] as? JsonPrimitive)?.strictBoolean() ?: continue
                    put(key, RemoteFlag(enabled, (value["payload"] as? JsonObject)?.toString()))
                }
                else -> Unit
            }
        }
    }
}

/** `true` or `false` written as JSON booleans; the strings "true" and "false" don't count. */
private fun JsonPrimitive.strictBoolean(): Boolean? = if (isString) null else booleanOrNull
