package pm.bam.gamedeals.domain.repositories.featureflags

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeatureFlagsTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val minute = 60_000L

    private fun file(flags: String) = """{"schemaVersion":1,"flags":{$flags}}"""

    /** Answers each fetch with the next scripted answer (the last one repeats); a Throwable is thrown. */
    private class ScriptedSource(vararg answers: Any) : FeatureFlagsSource {
        private val answers = answers.toList()
        var calls = 0
            private set

        override suspend fun fetch(): String {
            val answer = answers[minOf(calls, answers.lastIndex)]
            calls++
            if (answer is Throwable) throw answer
            return answer as String
        }
    }

    /** Keeps values as JSON text, like the real key-value storage, so what is saved must read back. */
    private class MemoryStorage(private val json: Json) : Storage {
        val values = mutableMapOf<String, String>()
        var unreadable = false

        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
            if (unreadable) error("disk")
            return values[storageKey]?.let { json.decodeFromString(deserializationStrategy, it) } ?: defaultValue
        }

        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            values[storageKey] = json.encodeToString(serializationStrategy, data)
            return true
        }

        override suspend fun containsKey(storageKey: String) = storageKey in values
        override suspend fun remove(storageKey: String) = values.remove(storageKey) != null
    }

    private val storage = MemoryStorage(json)
    private val logger = RecordingLogger()
    private var now = 1_000_000L

    private fun TestScope.flags(source: FeatureFlagsSource, isDebug: Boolean = false, scope: CoroutineScope = backgroundScope) =
        RemoteFeatureFlags(source, storage, json, Clock { now }, logger, isDebug, scope)

    @Test
    fun before_anything_loads_every_switch_takes_its_default() = runTest {
        val flags = flags(ScriptedSource(RuntimeException("offline")))
        runCurrent()

        FeatureFlag.entries.forEach { assertEquals(it.default, flags.isEnabled(it), it.key) }
        assertNull(flags.payload(FeatureFlag.ForceUpdate))
    }

    @Test
    fun a_good_fetch_replaces_the_values_and_the_next_start_uses_them_offline() = runTest {
        val first = flags(ScriptedSource(file(""""deal_swipe":false,"year_in_deals":true""")))
        first.awaitRefresh()
        assertFalse(first.isEnabled(FeatureFlag.DealSwipe))

        val offline = flags(ScriptedSource(RuntimeException("offline")))
        runCurrent()

        assertFalse(offline.isEnabled(FeatureFlag.DealSwipe), "the saved copy applies at start")
        assertTrue(offline.isEnabled(FeatureFlag.YearInDeals))
    }

    @Test
    fun a_failed_or_unreadable_answer_keeps_the_last_good_values() = runTest {
        val source = ScriptedSource(
            file(""""deal_swipe":false"""),
            RuntimeException("503: the file was mistyped"),
            "<html>support page</html>",
            """{"schemaVersion":2,"flags":{}}""",
            """{"schemaVersion":"1","flags":{}}""",
            """{"schemaVersion":1}""",
            "[]",
        )
        val flags = flags(source)

        repeat(7) {
            flags.awaitRefresh()
            assertFalse(flags.isEnabled(FeatureFlag.DealSwipe), "after answer ${source.calls}")
            now += 15 * minute
        }
        assertEquals(7, source.calls)
        assertEquals(6, logger.at(LogLevel.WARN).size, "each failure is noted, quietly")
    }

    @Test
    fun a_switch_the_new_file_no_longer_lists_goes_back_to_its_default() = runTest {
        val flags = flags(ScriptedSource(file(""""deal_swipe":false"""), file("")))
        flags.awaitRefresh()
        assertFalse(flags.isEnabled(FeatureFlag.DealSwipe))

        now += 15 * minute
        flags.awaitRefresh()

        assertTrue(flags.isEnabled(FeatureFlag.DealSwipe))
    }

    @Test
    fun the_file_is_fetched_at_most_every_fifteen_minutes_unless_asked_to_now() = runTest {
        val source = ScriptedSource(file(""))
        val flags = flags(source)

        flags.awaitRefresh()
        now += 14 * minute
        flags.awaitRefresh()
        flags.refresh()
        runCurrent()
        assertEquals(1, source.calls)

        flags.fetchNow()
        assertEquals(2, source.calls)

        now += 15 * minute
        flags.awaitRefresh()
        assertEquals(3, source.calls)
    }

    @Test
    fun the_gap_counts_from_the_saved_fetch_after_a_restart() = runTest {
        flags(ScriptedSource(file(""))).awaitRefresh()

        val source = ScriptedSource(file(""))
        now += 5 * minute
        flags(source).awaitRefresh()

        assertEquals(0, source.calls)
    }

    @Test
    fun a_failed_fetch_is_tried_again_at_the_next_chance() = runTest {
        val source = ScriptedSource(RuntimeException("offline"), file(""""bundle_watch":false"""))
        val flags = flags(source)

        flags.awaitRefresh()
        flags.awaitRefresh()

        assertEquals(2, source.calls)
        assertFalse(flags.isEnabled(FeatureFlag.BundleWatch))
    }

    @Test
    fun a_clock_set_back_does_not_block_fetches() = runTest {
        val source = ScriptedSource(file(""))
        val flags = flags(source)
        flags.awaitRefresh()

        now -= 60 * minute
        flags.awaitRefresh()

        assertEquals(2, source.calls)
    }

    @Test
    fun unknown_keys_are_ignored_and_an_unreadable_switch_takes_its_default() = runTest {
        val flags = flags(
            ScriptedSource(
                file(
                    """
                    "deal_swipe":"false",
                    "bundle_watch":{"payload":{}},
                    "game_signals":0,
                    "sale_countdown":null,
                    "wishlist_links":[false],
                    "year_in_deals":{"enabled":false},
                    "a_switch_from_the_future":false
                    """.trimIndent(),
                ),
            ),
        )
        flags.awaitRefresh()

        assertTrue(flags.isEnabled(FeatureFlag.DealSwipe))
        assertTrue(flags.isEnabled(FeatureFlag.BundleWatch))
        assertTrue(flags.isEnabled(FeatureFlag.GameSignals))
        assertTrue(flags.isEnabled(FeatureFlag.SaleCountdown))
        assertTrue(flags.isEnabled(FeatureFlag.WishlistLinks))
        assertFalse(flags.isEnabled(FeatureFlag.YearInDeals))
    }

    @Test
    fun the_force_update_settings_pass_through_as_json() = runTest {
        val flags = flags(
            ScriptedSource(file(""""force_update":{"enabled":true,"payload":{"minimum_version":"1.7.0","blocking":true}}""")),
        )
        flags.awaitRefresh()

        assertTrue(flags.isEnabled(FeatureFlag.ForceUpdate))
        assertEquals(
            json.parseToJsonElement("""{"minimum_version":"1.7.0","blocking":true}"""),
            json.parseToJsonElement(flags.payload(FeatureFlag.ForceUpdate)!!),
        )
        assertEquals(flags.payload(FeatureFlag.ForceUpdate), flags.observePayload(FeatureFlag.ForceUpdate).first())
        assertNull(flags.payload(FeatureFlag.DealSwipe))
    }

    @Test
    fun observing_a_switch_follows_each_change() = runTest {
        val flags = flags(ScriptedSource(file(""""deal_swipe":false"""), file(""""deal_swipe":true""")))
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { flags.observe(FeatureFlag.DealSwipe).collect { seen += it } }
        runCurrent()

        flags.awaitRefresh()
        runCurrent()
        now += 15 * minute
        flags.awaitRefresh()
        runCurrent()

        assertEquals(listOf(true, false, true), seen)
    }

    @Test
    fun a_fetch_is_never_overwritten_by_the_older_saved_copy() = runTest {
        flags(ScriptedSource(file(""""deal_swipe":false"""))).awaitRefresh()
        now += 15 * minute

        // The new instance's restore is queued; the fetch takes the lock first and must win.
        val flags = flags(ScriptedSource(file(""""deal_swipe":true""")))
        flags.awaitRefresh()
        runCurrent()

        assertTrue(flags.isEnabled(FeatureFlag.DealSwipe))
    }

    @Test
    fun unreadable_saved_values_fall_back_to_defaults_and_still_fetch() = runTest {
        storage.unreadable = true
        val source = ScriptedSource(file(""""deal_swipe":false"""))
        val flags = flags(source)
        runCurrent()
        assertTrue(flags.isEnabled(FeatureFlag.DealSwipe))

        flags.awaitRefresh()

        assertEquals(1, source.calls)
        assertFalse(flags.isEnabled(FeatureFlag.DealSwipe))
    }

    @Test
    fun a_debug_build_can_force_a_switch_and_keeps_it_across_starts() = runTest {
        val flags = flags(ScriptedSource(file(""""deal_swipe":true,"bundle_watch":false""")), isDebug = true)
        flags.awaitRefresh()

        flags.setOverride(FeatureFlag.DealSwipe, false)
        flags.setOverride(FeatureFlag.BundleWatch, true)
        assertFalse(flags.isEnabled(FeatureFlag.DealSwipe))
        assertTrue(flags.isEnabled(FeatureFlag.BundleWatch))
        assertEquals(mapOf(FeatureFlag.DealSwipe to false, FeatureFlag.BundleWatch to true), flags.observeOverrides().first())

        val restarted = flags(ScriptedSource(RuntimeException("offline")), isDebug = true)
        runCurrent()
        assertFalse(restarted.isEnabled(FeatureFlag.DealSwipe))

        restarted.setOverride(FeatureFlag.DealSwipe, null)
        assertTrue(restarted.isEnabled(FeatureFlag.DealSwipe), "follows the file again")
    }

    @Test
    fun a_release_build_ignores_overrides_stored_or_new() = runTest {
        flags(ScriptedSource(file("")), isDebug = true).setOverride(FeatureFlag.DealSwipe, false)

        val release = flags(ScriptedSource(file("")))
        runCurrent()
        assertTrue(release.isEnabled(FeatureFlag.DealSwipe))

        release.setOverride(FeatureFlag.YearInDeals, false)
        assertTrue(release.isEnabled(FeatureFlag.YearInDeals))
        assertEquals(emptyMap(), release.observeOverrides().first())
    }

    @Test
    fun the_file_reader_refuses_anything_but_a_switch_file() {
        assertFailsWith<IllegalArgumentException> { parseFlags("""{"schemaVersion":1,"flags":[]}""", json) }
        assertFailsWith<IllegalArgumentException> { parseFlags("""{"schemaVersion":true,"flags":{}}""", json) }
        assertFailsWith<IllegalArgumentException> { parseFlags("[1]", json) }
        assertEquals(
            mapOf("deal_swipe" to RemoteFlag(false), "force_update" to RemoteFlag(true, null)),
            parseFlags(file(""""deal_swipe":false,"force_update":{"enabled":true,"payload":null}"""), json),
        )
        assertEquals("{}", parseFlags(file(""""force_update":{"enabled":true,"payload":{}}"""), json)["force_update"]?.payload)
    }
}
