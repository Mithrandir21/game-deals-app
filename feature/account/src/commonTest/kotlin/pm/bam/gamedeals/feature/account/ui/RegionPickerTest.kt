package pm.bam.gamedeals.feature.account.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit coverage for [flagEmoji], the pure ISO-code → flag-emoji helper behind the region picker rows.
 * Method names are snake_case (DEX-safe): :feature:account has device tests, so commonTest is dexed at
 * minSdk 26 where backtick names with spaces are illegal.
 */
class RegionPickerTest {

    @Test
    fun valid_code_maps_to_its_flag_emoji() {
        assertEquals("🇺🇸", flagEmoji("US"))
        assertEquals("🇬🇧", flagEmoji("GB"))
    }

    @Test
    fun flag_is_two_regional_indicator_surrogate_pairs() {
        assertEquals(4, flagEmoji("US").length)
    }

    @Test
    fun code_is_normalised_case_insensitively() {
        assertEquals(flagEmoji("US"), flagEmoji("us"))
        assertEquals(flagEmoji("GB"), flagEmoji("gb"))
    }

    @Test
    fun wrong_length_returns_empty() {
        assertEquals("", flagEmoji("USA"))
        assertEquals("", flagEmoji("U"))
        assertEquals("", flagEmoji(""))
    }

    @Test
    fun non_letters_return_empty() {
        assertEquals("", flagEmoji("U1"))
        assertEquals("", flagEmoji("12"))
        assertEquals("", flagEmoji("_!"))
    }
}
