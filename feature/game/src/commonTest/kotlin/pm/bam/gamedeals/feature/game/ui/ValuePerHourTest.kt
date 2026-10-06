package pm.bam.gamedeals.feature.game.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Names here avoid backticks-with-spaces: `commonTest` is part of the `test` source-set tree, so these
 * classes are also dexed into the `androidDeviceTest` APK, and D8 rejects spaces in method names below
 * DEX 040 (minSdk 35). The rest of this module's tests already use this style.
 */
class ValuePerHourTest {

    @Test
    fun scales_a_prefix_symbol_price_to_two_decimals() {
        // $59.99 over 30h (108000s) → $2.00/h
        assertEquals("$2.00", perHourDenominated("$59.99", 59.99, 108_000))
    }

    @Test
    fun sub_unit_per_hour_keeps_two_decimals() {
        // €40.00 over 100h (360000s) → €0.40/h
        assertEquals("€0.40", perHourDenominated("€40.00", 40.0, 360_000))
    }

    @Test
    fun zero_decimal_currency_stays_whole() {
        // ¥6,800 over 30h (108000s) → ¥227/h (no minor unit)
        assertEquals("¥227", perHourDenominated("¥6,800", 6800.0, 108_000))
    }

    @Test
    fun grouped_prices_are_read_whole_and_the_result_is_grouped() {
        // 1,071,247 IDR over 10h → 107,125 IDR/h; $1,299.00 over 1000h → $1.30/h
        assertEquals("107,125 IDR", perHourDenominated("1,071,247 IDR", 1_071_247.0, 36_000))
        assertEquals("$1.30", perHourDenominated("$1,299.00", 1299.0, 3_600_000))
        assertEquals("1,519.82 ARS", perHourDenominated("91,189.40 ARS", 91_189.4, 216_000))
    }

    @Test
    fun trailing_currency_code_is_preserved() {
        // "40.00 PLN" over 100h → "0.40 PLN"
        assertEquals("0.40 PLN", perHourDenominated("40.00 PLN", 40.0, 360_000))
    }

    @Test
    fun null_when_playtime_missing_or_zero() {
        assertNull(perHourDenominated("$59.99", 59.99, 0))
        assertNull(perHourDenominated("$59.99", 59.99, -1))
    }

    @Test
    fun null_for_free_games() {
        assertNull(perHourDenominated("$0.00", 0.0, 108_000))
    }
}
