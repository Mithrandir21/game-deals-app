package pm.bam.gamedeals.remote.igdb

import kotlin.test.Test
import kotlin.test.assertEquals

class TitleNormalizerTest {

    @Test
    fun title_with_no_decoration_is_returned_unchanged() {
        assertEquals("Halo Infinite", normalizeTitleForLookup("Halo Infinite"))
    }

    @Test
    fun trailing_digital_deluxe_edition_is_stripped() {
        assertEquals(
            "Suicide Squad: Kill the Justice League",
            normalizeTitleForLookup("Suicide Squad: Kill the Justice League - Digital Deluxe Edition"),
        )
    }

    @Test
    fun trailing_game_of_the_year_edition_is_stripped() {
        assertEquals(
            "The Witcher 3: Wild Hunt",
            normalizeTitleForLookup("The Witcher 3: Wild Hunt - Game of the Year Edition"),
        )
    }

    @Test
    fun goty_abbreviation_is_stripped() {
        assertEquals("Borderlands 3", normalizeTitleForLookup("Borderlands 3 - GOTY"))
    }

    @Test
    fun directors_cut_is_stripped() {
        assertEquals("Death Stranding", normalizeTitleForLookup("Death Stranding - Director's Cut"))
    }

    @Test
    fun remaster_suffix_is_stripped() {
        assertEquals("Mass Effect", normalizeTitleForLookup("Mass Effect - Remastered"))
    }

    @Test
    fun em_dash_delimiter_is_stripped() {
        assertEquals(
            "Cyberpunk 2077",
            normalizeTitleForLookup("Cyberpunk 2077 — Ultimate Edition"),
        )
    }

    @Test
    fun trailing_parenthetical_decoration_is_stripped() {
        assertEquals("Half-Life 2", normalizeTitleForLookup("Half-Life 2 (Steam)"))
    }

    @Test
    fun combined_parenthetical_and_dash_decorations_are_both_stripped() {
        assertEquals(
            "Hades",
            normalizeTitleForLookup("Hades - Standard Edition (Digital)"),
        )
    }

    @Test
    fun dash_in_middle_without_edition_keyword_is_preserved() {
        assertEquals("Half-Life 2", normalizeTitleForLookup("Half-Life 2"))
    }

    @Test
    fun real_title_containing_edition_word_without_dash_is_preserved() {
        // Pokemon Yellow: Special Pikachu Edition is the IGDB-canonical name; no dash, no strip.
        assertEquals(
            "Pokemon Yellow Version: Special Pikachu Edition",
            normalizeTitleForLookup("Pokemon Yellow Version: Special Pikachu Edition"),
        )
    }

    @Test
    fun blank_input_returns_blank() {
        assertEquals("", normalizeTitleForLookup(""))
        assertEquals("", normalizeTitleForLookup("   "))
    }

    @Test
    fun leading_and_trailing_whitespace_is_trimmed() {
        assertEquals("Halo Infinite", normalizeTitleForLookup("  Halo Infinite  "))
    }
}
