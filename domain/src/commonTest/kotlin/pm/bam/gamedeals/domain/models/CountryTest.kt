package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountryTest {

    @Test
    fun everyCountryAppearsExactlyOnceInItsRegion() {
        val grouped = countriesByRegion()
        val flattened = grouped.flatMap { it.second }

        // Every supported country is present exactly once, filed under its own region.
        assertEquals(SUPPORTED_COUNTRIES.sortedBy { it.code }, flattened.sortedBy { it.code })
        grouped.forEach { (region, entries) ->
            assertTrue(entries.all { it.region == region }, "region mismatch in $region")
        }
    }

    @Test
    fun regionsAreInDeclarationOrderAndCountriesNameSorted() {
        val grouped = countriesByRegion()

        val regionOrder = grouped.map { it.first }
        assertEquals(regionOrder.sortedBy { it.ordinal }, regionOrder)

        grouped.forEach { (_, entries) ->
            assertEquals(entries.sortedBy { it.name }, entries)
        }
    }

    @Test
    fun emptyRegionsAreOmitted() {
        val onlyEurope = SUPPORTED_COUNTRIES.filter { it.region == Region.EUROPE }
        val grouped = countriesByRegion(onlyEurope)

        assertEquals(listOf(Region.EUROPE), grouped.map { it.first })
    }

    @Test
    fun defaultCountryIsUsInAmericas() {
        assertEquals("US", DEFAULT_COUNTRY.code)
        assertEquals(Region.AMERICAS, DEFAULT_COUNTRY.region)
        assertTrue(DEFAULT_COUNTRY in SUPPORTED_COUNTRIES)
    }
}
