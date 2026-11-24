package pm.bam.gamedeals.domain.repositories.salecalendar

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaleCalendarRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val day = 24L * 60 * 60 * 1000

    private val document = """
        {"schemaVersion": 1, "sales": [
          {"id": "epic-holiday-2026", "store": "Epic Games Store", "name": "Epic Holiday Sale", "start": "2026-12-10T16:00:00Z", "end": "2027-01-07T16:00:00Z"},
          {"id": "steam-winter-2026", "store": "Steam", "name": "Steam Winter Sale", "kind": "big", "start": "2026-12-17T18:00:00Z", "end": "2027-01-04T18:00:00Z"},
          {"id": "broken", "store": "GOG", "name": "Broken", "start": "soon", "end": "2027-01-04T18:00:00Z"},
          {"id": "backwards", "store": "GOG", "name": "Backwards", "start": "2027-01-04T18:00:00Z", "end": "2026-12-04T18:00:00Z"}
        ]}
    """.trimIndent()

    private fun at(iso: String) = kotlin.time.Instant.parse(iso).toEpochMilliseconds()
    private val epic = StoreSale("epic-holiday-2026", "Epic Games Store", "Epic Holiday Sale", at("2026-12-10T16:00:00Z"), at("2027-01-07T16:00:00Z"))
    private val steam = StoreSale("steam-winter-2026", "Steam", "Steam Winter Sale", at("2026-12-17T18:00:00Z"), at("2027-01-04T18:00:00Z"))

    @Test
    fun the_document_is_read_and_bad_sales_skipped() {
        assertEquals(listOf(epic, steam), parseSales(document, json))
    }

    @Test
    fun a_sale_is_big_unless_marked_as_a_fest() {
        val text = """
            {"schemaVersion": 1, "sales": [
              {"id": "a", "store": "Steam", "name": "A", "kind": "fest", "start": "2027-01-14T18:00:00Z", "end": "2027-01-18T18:00:00Z"},
              {"id": "b", "store": "Steam", "name": "B", "kind": "big", "start": "2027-01-14T18:00:00Z", "end": "2027-01-18T18:00:00Z"},
              {"id": "c", "store": "Steam", "name": "C", "start": "2027-01-14T18:00:00Z", "end": "2027-01-18T18:00:00Z"},
              {"id": "d", "store": "Steam", "name": "D", "kind": "mega", "start": "2027-01-14T18:00:00Z", "end": "2027-01-18T18:00:00Z"}
            ]}
        """.trimIndent()
        assertEquals(listOf(SaleKind.FEST, SaleKind.BIG, SaleKind.BIG, SaleKind.BIG), parseSales(text, json).map { it.kind })
    }

    @Test
    fun the_dates_built_into_the_app_all_read() {
        val sales = parseSales(BUNDLED_SALES_JSON, json)
        assertEquals(Regex("\"id\"").findAll(BUNDLED_SALES_JSON).count(), sales.size, "no sale dropped")
        assertTrue(sales.any { it.kind == SaleKind.FEST })
    }

    @Test
    fun an_unknown_schema_is_refused() {
        assertFailsWith<IllegalArgumentException> { parseSales("""{"schemaVersion": 2, "sales": []}""", json) }
    }

    @Test
    fun the_countdown_is_the_running_sale_ending_first_else_the_next_to_start() {
        val sales = listOf(epic, steam)
        assertEquals(SaleCountdown.Upcoming(epic), sales.countdownAt(at("2026-12-01T00:00:00Z")))
        assertEquals(SaleCountdown.Running(epic), sales.countdownAt(at("2026-12-12T00:00:00Z")))
        // Both running: Steam's ends first.
        assertEquals(SaleCountdown.Running(steam), sales.countdownAt(at("2026-12-20T00:00:00Z")))
        assertEquals(SaleCountdown.Running(epic), sales.countdownAt(steam.endEpochMs))
        assertNull(sales.countdownAt(epic.endEpochMs))
        assertNull(emptyList<StoreSale>().countdownAt(0))
    }

    private val rhythm = StoreSale("rhythm", "Steam", "Rhythm Fest", at("2027-03-08T18:00:00Z"), at("2027-03-15T17:00:00Z"), SaleKind.FEST)
    private val spring = StoreSale("spring", "Steam", "Steam Spring Sale", at("2027-03-18T17:00:00Z"), at("2027-03-25T17:00:00Z"))
    private val racing = StoreSale("racing", "Steam", "Racing Fest", at("2027-03-17T17:00:00Z"), at("2027-03-30T17:00:00Z"), SaleKind.FEST)

    @Test
    fun a_running_fest_shows_until_a_big_sale_is_a_week_away() {
        val sales = listOf(rhythm, spring)
        assertEquals(SaleCountdown.Upcoming(rhythm), sales.countdownAt(at("2027-03-01T00:00:00Z")))
        assertEquals(SaleCountdown.Running(rhythm), sales.countdownAt(spring.startEpochMs - BIG_SALE_LEAD_MS - 1))
        assertEquals(SaleCountdown.Upcoming(spring), sales.countdownAt(spring.startEpochMs - BIG_SALE_LEAD_MS))
        assertEquals(SaleCountdown.Running(spring), sales.countdownAt(spring.startEpochMs))
    }

    @Test
    fun a_big_sale_a_week_away_beats_a_fest_starting_sooner_and_a_running_big_sale_beats_a_fest() {
        val sales = listOf(racing, spring)
        assertEquals(SaleCountdown.Upcoming(spring), sales.countdownAt(at("2027-03-16T00:00:00Z")))
        // Racing Fest runs on past the big sale's start; the big sale wins while it's on.
        assertEquals(SaleCountdown.Running(spring), sales.countdownAt(at("2027-03-20T00:00:00Z")))
        assertEquals(SaleCountdown.Running(racing), sales.countdownAt(spring.endEpochMs))
    }

    /** A Storage that keeps values in memory, encoded the way the real one does. */
    private class MemoryStorage : Storage {
        val values = mutableMapOf<String, String>()
        private val json = Json
        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
            values[storageKey]?.let { json.decodeFromString(deserializationStrategy, it) } ?: defaultValue
        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            values[storageKey] = json.encodeToString(serializationStrategy, data)
            return true
        }
        override suspend fun remove(storageKey: String): Boolean = values.remove(storageKey) != null
        override suspend fun containsKey(storageKey: String): Boolean = storageKey in values
        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("missing $storageKey")
    }

    private var now = at("2026-12-01T00:00:00Z")
    private var fetches = 0
    private val source: SaleCalendarSource = mock(MockMode.autoUnit) {
        everySuspend { fetch() } calls { fetches++; document }
    }
    private val storage = MemoryStorage()
    private val repository = SaleCalendarRepositoryImpl(source, storage, json, Clock { now }, TestingLoggingListener())

    @Test
    fun the_dates_are_kept_on_the_phone_and_refreshed_after_six_hours() = runTest {
        assertEquals(listOf(epic, steam), repository.getSales())
        now += 5 * 60 * 60 * 1000
        repository.getSales()
        assertEquals(1, fetches)
        now += 60 * 60 * 1000
        repository.getSales()
        assertEquals(2, fetches)
    }

    @Test
    fun a_failed_refresh_keeps_the_copy_it_has() = runTest {
        repository.getSales()
        now += day
        everySuspend { source.fetch() } throws IllegalStateException("offline")
        assertEquals(listOf(epic, steam), repository.getSales())
    }

    @Test
    fun with_nothing_cached_a_failure_falls_back_to_the_dates_built_into_the_app() = runTest {
        everySuspend { source.fetch() } throws IllegalStateException("offline")
        val withBuiltIn = SaleCalendarRepositoryImpl(source, storage, json, Clock { now }, TestingLoggingListener(), bundledJson = document)
        assertEquals(listOf(epic, steam), withBuiltIn.getSales())
        verifySuspend(exactly(1)) { source.fetch() }
    }

    @Test
    fun unreadable_built_in_dates_mean_no_sales() = runTest {
        everySuspend { source.fetch() } throws IllegalStateException("offline")
        val broken = SaleCalendarRepositoryImpl(source, storage, json, Clock { now }, TestingLoggingListener(), bundledJson = "{")
        assertEquals(emptyList(), broken.getSales())
    }

    @Test
    fun the_current_countdown_uses_the_clock() = runTest {
        now = at("2026-12-20T00:00:00Z")
        assertEquals(SaleCountdown.Running(steam), repository.currentCountdown())
    }
}
