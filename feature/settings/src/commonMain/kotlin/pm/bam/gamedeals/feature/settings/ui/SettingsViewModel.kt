package pm.bam.gamedeals.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository

/**
 * Backs the Settings screen's region picker (epic #205, Phase 3b — #212).
 *
 * Selecting a country clears the cached deals BEFORE persisting the new region, so the Home/Store
 * screens (which observe the region) reload and re-fetch regional prices instead of re-reading the
 * previous region's stale cache.
 */
internal class SettingsViewModel(
    private val regionRepository: RegionRepository,
    private val dealsRepository: DealsRepository,
) : ViewModel() {

    val countries: ImmutableList<Country> = regionRepository.supportedCountries.toImmutableList()

    val selectedCountryCode: StateFlow<String?> = regionRepository.observeSelectedCountry()
        .map { it.code }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onCountrySelected(country: Country) {
        viewModelScope.launch {
            dealsRepository.clearCachedDeals()
            regionRepository.setSelectedCountry(country)
        }
    }
}
