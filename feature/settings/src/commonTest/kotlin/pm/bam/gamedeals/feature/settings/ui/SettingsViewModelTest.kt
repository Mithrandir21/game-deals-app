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

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun on_country_selected_persists_region() = runTest {
        val viewModel = SettingsViewModel(regionRepository)
        val germany = Country("DE", "Germany")

        viewModel.onCountrySelected(germany)
        advanceUntilIdle()

        // Region keying (Phase 2) means no cache clear is needed — just persist the new region.
        verifySuspend(exactly(1)) { regionRepository.setSelectedCountry(germany) }
    }

    @Test
    fun exposes_supported_countries() {
        val viewModel = SettingsViewModel(regionRepository)

        assertEquals(SUPPORTED_COUNTRIES.size, viewModel.countries.size)
        assertEquals(SUPPORTED_COUNTRIES.first().code, viewModel.countries.first().code)
    }
}
