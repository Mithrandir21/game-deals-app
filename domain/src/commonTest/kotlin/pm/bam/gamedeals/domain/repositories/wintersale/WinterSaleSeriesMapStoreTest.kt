package pm.bam.gamedeals.domain.repositories.wintersale

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.testing.RecordingLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The saved series maps go through real JSON, as on the phone, so a format change shows up here. */
class WinterSaleSeriesMapStoreTest {

    private val storage = JsonStorage()
    private val logger = RecordingLogger()
    private val store = WinterSaleSeriesMapStoreImpl(storage, logger)

    @Test
    fun saved_maps_read_back_unchanged() = runTest {
        val entries = listOf(
            WinterSaleSeriesMapEntry(493, followedAtEpochMs = 1, builtAtEpochMs = 2, complete = true, games = listOf(
                WinterSaleSeriesMappedGame("018d937f-03e2", "Grand Theft Auto V", erotic = false, coverImageId = "co2lbd"),
                WinterSaleSeriesMappedGame("018d937e-f829", "Grand Theft Auto: San Andreas", erotic = null),
            )),
            WinterSaleSeriesMapEntry(8190, followedAtEpochMs = 3, builtAtEpochMs = 4, complete = false, games = emptyList()),
        )

        store.replace(entries)

        assertEquals(entries, store.get())
    }

    @Test
    fun nothing_saved_reads_as_no_maps() = runTest {
        assertEquals(emptyList(), store.get())
    }

    @Test
    fun maps_saved_before_series_artwork_existed_still_load() = runTest {
        // The shape stored by builds before covers were kept: no coverImageId, plus a field a later build might drop.
        storage.raw["winter_sale_series_id_map"] =
            """[{"franchiseId":43,"followedAtEpochMs":1,"builtAtEpochMs":2,"complete":true,"removedLater":1,""" +
            """"games":[{"itadId":"018d9584-24d6","title":"Baldur's Gate III","erotic":false}]}]"""

        val loaded = store.get().single()

        assertEquals(43, loaded.franchiseId)
        assertEquals(listOf(WinterSaleSeriesMappedGame("018d9584-24d6", "Baldur's Gate III", erotic = false)), loaded.games)
    }

    @Test
    fun an_unreadable_save_is_logged_and_rebuilt_from_scratch() = runTest {
        storage.raw["winter_sale_series_id_map"] = "{not json"

        assertTrue(store.get().isEmpty())
        assertEquals(1, logger.at(LogLevel.ERROR).size)
    }
}

/** A storage that keeps JSON strings with the app's settings, like the on-device implementation. */
private class JsonStorage : Storage {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    val raw = mutableMapOf<String, String>()

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
        raw[storageKey]?.let { json.decodeFromString(deserializationStrategy, it) } ?: defaultValue

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        raw[storageKey] = json.encodeToString(serializationStrategy, data)
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = storageKey in raw
    override suspend fun remove(storageKey: String): Boolean = raw.remove(storageKey) != null
}
