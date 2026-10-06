package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountryTest {

    @Test
    fun pickerListsItadsPriceRegionsByNameThenRestOfWorld() {
        assertEquals(18, SUPPORTED_COUNTRIES.size)
        assertEquals(REST_OF_WORLD, SUPPORTED_COUNTRIES.last())
        val named = SUPPORTED_COUNTRIES.dropLast(1).map { it.name }
        assertEquals(named.sorted(), named)
    }

    @Test
    fun regionIdsAreUnique() {
        assertEquals(SUPPORTED_COUNTRIES.size, SUPPORTED_COUNTRIES.map { it.id }.toSet().size)
    }

    @Test
    fun defaultCountryIsTheUs() {
        assertEquals("US", DEFAULT_COUNTRY.code)
        assertTrue(DEFAULT_COUNTRY in SUPPORTED_COUNTRIES)
    }

    @Test
    fun aTrackedCountryMapsToItsOwnRegion() {
        assertEquals(DEFAULT_COUNTRY, regionForCountry("US"))
        assertEquals("JP", regionForCountry("jp").code)
        assertEquals("GB", regionForCountry("GB").code)
    }

    @Test
    fun countriesItadPricesAsAnotherRegionMapToThatRegion() {
        assertEquals(EUROPE, regionForCountry("DE"))
        assertEquals(EUROPE, regionForCountry("FR"))
        assertEquals(EUROPE, regionForCountry("NO"))
        assertEquals("AR", regionForCountry("EC").code)
        assertEquals(REST_OF_WORLD, regionForCountry("MX"))
        assertEquals(REST_OF_WORLD, regionForCountry("ZA"))
    }

    @Test
    fun restOfWorldSaysWhosePricesItUses() {
        assertEquals("US prices", REST_OF_WORLD.priceNote)
        assertEquals("EUR", EUROPE.priceNote)
    }
}
