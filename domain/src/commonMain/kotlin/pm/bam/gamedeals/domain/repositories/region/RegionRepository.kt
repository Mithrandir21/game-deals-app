package pm.bam.gamedeals.domain.repositories.region

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.SUPPORTED_COUNTRIES

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
        storage.save(SELECTED_COUNTRY_KEY, country.code, String.serializer())
        selected.value = country
    }

    private suspend fun loadFromStorage(): Country {
        val code = runCatching { storage.getNullable(SELECTED_COUNTRY_KEY, String.serializer()) }.getOrNull()
        return SUPPORTED_COUNTRIES.firstOrNull { it.code == code } ?: DEFAULT_COUNTRY
    }
}
