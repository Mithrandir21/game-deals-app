@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.settings.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.DEFAULT_COUNTRY
import pm.bam.gamedeals.domain.models.SUPPORTED_COUNTRIES
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsViewModelTest : MainDispatcherTest() {

    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        every { supportedCountries } returns SUPPORTED_COUNTRIES
        every { observeSelectedCountry() } returns flowOf(DEFAULT_COUNTRY)
    }
    private val dealsRepository: DealsRepository = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun on_country_selected_clears_cache_and_persists() = runTest {
        val viewModel = SettingsViewModel(regionRepository, dealsRepository)
        val germany = Country("DE", "Germany")

        viewModel.onCountrySelected(germany)
        advanceUntilIdle()

        // Cache must be cleared (so Home/Store reload regional prices) and the region persisted.
        verifySuspend(exactly(1)) { dealsRepository.clearCachedDeals() }
        verifySuspend(exactly(1)) { regionRepository.setSelectedCountry(germany) }
    }

    @Test
    fun exposes_supported_countries() {
        val viewModel = SettingsViewModel(regionRepository, dealsRepository)

        assertEquals(SUPPORTED_COUNTRIES.size, viewModel.countries.size)
        assertEquals(SUPPORTED_COUNTRIES.first().code, viewModel.countries.first().code)
    }
}
