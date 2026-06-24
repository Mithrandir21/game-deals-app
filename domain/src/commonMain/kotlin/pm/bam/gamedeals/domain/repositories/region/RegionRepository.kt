package pm.bam.gamedeals.domain.repositories.region

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.SUPPORTED_COUNTRIES
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's selected storefront region (epic #205, Phase 3b — #212), persisted via [Storage] and
 * exposed reactively so the deal source reads the current country and the UI can re-fetch on change.
 *
 * Defaults to [DEFAULT_COUNTRY] (US) when nothing is stored. The ITAD source reads
 * [getSelectedCountryCode] at fetch time; Settings writes via [setSelectedCountry]; Home/Store observe
 * [observeSelectedCountry] to reload regional prices when the selection changes.
 */
interface RegionRepository {
    val supportedCountries: List<Country>
    fun observeSelectedCountry(): Flow<Country>
    suspend fun getSelectedCountryCode(): String
    suspend fun setSelectedCountry(country: Country)
}

internal const val SELECTED_COUNTRY_KEY = "selected_country_code"

internal class RegionRepositoryImpl(
    private val storage: Storage,
    private val analytics: Analytics,
) : RegionRepository {

    override val supportedCountries: List<Country> = SUPPORTED_COUNTRIES

    // Reactive source of truth, lazily seeded from [storage] on first access (null = not yet loaded).
    private val selected = MutableStateFlow<Country?>(null)

    override fun observeSelectedCountry(): Flow<Country> =
        selected
            .onStart { if (selected.value == null) selected.value = loadFromStorage() }
            .filterNotNull()

    override suspend fun getSelectedCountryCode(): String {
        if (selected.value == null) selected.value = loadFromStorage()
        return (selected.value ?: DEFAULT_COUNTRY).code
    }

    override suspend fun setSelectedCountry(country: Country) {
        storage.save(SELECTED_COUNTRY_KEY, country.code)
        selected.value = country
        analytics.capture(AnalyticsEvents.REGION_CHANGED, mapOf("country" to country.code))
    }

    private suspend fun loadFromStorage(): Country {
        val code = runCatching { storage.getNullable<String>(SELECTED_COUNTRY_KEY) }.getOrNull()
        return SUPPORTED_COUNTRIES.firstOrNull { it.code == code } ?: DEFAULT_COUNTRY
    }
}
