package pm.bam.gamedeals.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class StoreLogosTest {

    @Test
    fun known_stores_resolve_to_a_bundled_logo() {
        listOf("Steam", "GOG", "Epic Game Store", "Humble Store", "Ubisoft Store", "Battle.net", "itch.io")
            .forEach { assertNotNull(storeLogoFor(it), "expected a bundled logo for \"$it\"") }
    }

    @Test
    fun matching_is_case_and_punctuation_insensitive() {
        assertSame(storeLogoFor("Steam"), storeLogoFor("steam"))
        assertSame(storeLogoFor("GOG"), storeLogoFor("GOG.com"))
        assertSame(storeLogoFor("itch.io"), storeLogoFor("Itch IO"))
    }

    @Test
    fun spelling_and_brand_variants_resolve_to_the_same_logo() {
        assertSame(storeLogoFor("Epic Game Store"), storeLogoFor("Epic Games Store"))
        assertSame(storeLogoFor("Battle.net"), storeLogoFor("Blizzard"))
        assertSame(storeLogoFor("Ubisoft Store"), storeLogoFor("Uplay"))
    }

    @Test
    fun unknown_store_has_no_bundled_logo() {
        assertNull(storeLogoFor("Fanatical"))
        assertNull(storeLogoFor("Some Unlisted Store"))
        assertNull(storeLogoFor(""))
    }

    @Test
    fun distinct_stores_resolve_to_distinct_logos() {
        assertEquals(false, storeLogoFor("Steam") == storeLogoFor("GOG"))
    }
}
