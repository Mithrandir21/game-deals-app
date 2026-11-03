package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTime::class)
class WinterSaleEventRepositoryTest {
    private var deviceNow = millis("2026-12-10T12:00:00Z")
    private val storage = MemoryStorage()
    private val source = FakeSource(WinterSaleEventPayload(ENABLED, deviceNow))
    private fun repository() = WinterSaleEventRepositoryImpl(source, storage, Clock { deviceNow }, Json { ignoreUnknownKeys = true })

    @Test
    fun server_date_controls_visibility_and_sale_phase() = runTest {
        deviceNow = millis("2030-01-01T00:00:00Z")
        source.response = WinterSaleEventPayload(ENABLED, millis("2026-12-09T23:59:59Z"))
        assertEquals(WinterSaleHiddenReason.BeforeVisibility, (repository().refresh() as WinterSaleEventState.Hidden).reason)

        source.response = WinterSaleEventPayload(ENABLED, millis("2026-12-17T17:59:59Z"))
        assertEquals(WinterSalePhase.BeforeSale, (repository().refresh() as WinterSaleEventState.Available).phase)

        source.response = WinterSaleEventPayload(ENABLED, millis("2026-12-17T18:00:00Z"))
        assertEquals(WinterSalePhase.OnSale, (repository().refresh() as WinterSaleEventState.Available).phase)

        source.response = WinterSaleEventPayload(ENABLED, millis("2027-01-04T18:00:00Z"))
        assertEquals(WinterSaleHiddenReason.Ended, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun offline_restart_uses_enabled_document_for_less_than_twelve_hours() = runTest {
        repository().refresh()
        source.fail = true
        deviceNow += 11 * HOUR

        val cached = repository().refresh() as WinterSaleEventState.Available
        assertTrue(cached.usingCachedDocument)
        assertEquals(WinterSalePhase.BeforeSale, cached.phase)

        deviceNow += HOUR
        assertEquals(WinterSaleHiddenReason.Unavailable, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun backward_device_clock_fails_closed() = runTest {
        repository().refresh()
        source.fail = true
        deviceNow -= 1

        assertEquals(WinterSaleHiddenReason.Unavailable, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun offline_cache_hides_the_guide_after_event_end() = runTest {
        deviceNow = millis("2027-01-04T17:59:00Z")
        source.response = WinterSaleEventPayload(ENABLED, deviceNow)
        repository().refresh()
        source.fail = true
        deviceNow += 60_000

        assertEquals(WinterSaleHiddenReason.Ended, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun fetched_disabled_document_overrides_cache_and_survives_offline_restart() = runTest {
        repository().refresh()
        source.response = WinterSaleEventPayload("""{"enabled":false}""", null)

        assertEquals(WinterSaleHiddenReason.Disabled, (repository().refresh() as WinterSaleEventState.Hidden).reason)
        source.fail = true
        assertEquals(WinterSaleHiddenReason.Disabled, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun malformed_or_unsupported_document_never_crashes_and_can_use_recent_cache() = runTest {
        source.response = WinterSaleEventPayload("not JSON", deviceNow)
        assertEquals(WinterSaleHiddenReason.Unavailable, (repository().refresh() as WinterSaleEventState.Hidden).reason)

        source.response = WinterSaleEventPayload(ENABLED, deviceNow)
        repository().refresh()
        source.response = WinterSaleEventPayload(ENABLED.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), deviceNow)
        assertTrue((repository().refresh() as WinterSaleEventState.Available).usingCachedDocument)
    }

    @Test
    fun missing_server_date_does_not_create_a_new_enabled_cache() = runTest {
        source.response = WinterSaleEventPayload(ENABLED, null)
        assertEquals(WinterSaleHiddenReason.Unavailable, (repository().refresh() as WinterSaleEventState.Hidden).reason)
        assertFalse(storage.containsKey("winter_sale_event_document_v1"))
    }

    @Test
    fun cold_fetch_times_out_without_showing_the_guide() = runTest {
        source.delayMillis = 3_000

        assertEquals(WinterSaleHiddenReason.Unavailable, (repository().refresh() as WinterSaleEventState.Hidden).reason)
    }

    @Test
    fun old_extra_fields_are_tolerated() = runTest {
        source.response = WinterSaleEventPayload(ENABLED.replace("\"enabled\":true,", "\"enabled\":true,\"handPicks\":[\"unused\"],"), deviceNow)
        assertEquals(WinterSalePhase.BeforeSale, (repository().refresh() as WinterSaleEventState.Available).phase)
    }

    private fun millis(value: String) = Instant.parse(value).toEpochMilliseconds()

    private companion object {
        const val HOUR = 60L * 60 * 1_000
        val ENABLED = """
            {"schemaVersion":1,"eventId":"steam-winter-2026","enabled":true,
            "visibleFrom":"2026-12-10T00:00:00Z","saleStart":"2026-12-17T18:00:00Z",
            "end":"2027-01-04T18:00:00Z","copy":{"title":"Winter Sale guide","beforeSale":"Soon","duringSale":"Now"}}
        """.trimIndent()
    }
}

private class FakeSource(var response: WinterSaleEventPayload) : WinterSaleEventSource {
    var fail = false
    var delayMillis = 0L
    override suspend fun fetch(): WinterSaleEventPayload {
        delay(delayMillis)
        if (fail) error("offline")
        return response
    }
}

private class MemoryStorage : Storage {
    private val values = mutableMapOf<String, Any>()

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("Missing $storageKey")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        values[storageKey] as? T ?: defaultValue

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        values[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = storageKey in values
    override suspend fun remove(storageKey: String): Boolean = values.remove(storageKey) != null
}
