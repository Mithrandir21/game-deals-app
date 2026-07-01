package pm.bam.gamedeals.domain.repositories.region

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Region
import pm.bam.gamedeals.domain.RecordingAnalytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import kotlin.test.Test
import kotlin.test.assertEquals

class RegionRepositoryTest {

    // Minimal in-memory [Storage] — RegionRepositoryImpl only touches getNullable/save with String values.
    private val backing = mutableMapOf<String, String>()
    private val storage = object : Storage {
        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
            (backing[storageKey] as T?) ?: defaultValue

        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            backing[storageKey] = data as String
            return true
        }

        override suspend fun containsKey(storageKey: String): Boolean = backing.containsKey(storageKey)
        override suspend fun remove(storageKey: String): Boolean = backing.remove(storageKey) != null
    }

    private val analytics = RecordingAnalytics()
    private val repository = RegionRepositoryImpl(storage, analytics)

    @Test
    fun defaults_to_US_when_nothing_stored() = runTest {
        assertEquals("US", repository.getSelectedCountryCode())
        assertEquals("US", repository.observeSelectedCountry().first().code)
    }

    @Test
    fun set_country_persists_and_is_reflected() = runTest {
        val germany = Country("DE", "Germany", Region.EUROPE)
        repository.setSelectedCountry(germany)

        assertEquals("DE", repository.getSelectedCountryCode())
        assertEquals(germany, repository.observeSelectedCountry().first())
        assertEquals("DE", backing[SELECTED_COUNTRY_KEY])
        assertEquals(mapOf("country" to "DE"), analytics.propsOf(AnalyticsEvents.REGION_CHANGED))
    }

    @Test
    fun loads_a_previously_persisted_code_into_its_country() = runTest {
        backing[SELECTED_COUNTRY_KEY] = "GB"

        val country = repository.observeSelectedCountry().first()
        assertEquals("GB", country.code)
        assertEquals("United Kingdom", country.name)
    }

    @Test
    fun unknown_stored_code_falls_back_to_default() = runTest {
        backing[SELECTED_COUNTRY_KEY] = "ZZ"

        assertEquals("US", repository.getSelectedCountryCode())
    }
}
