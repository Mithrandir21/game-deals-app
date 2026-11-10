package pm.bam.gamedeals.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DealBadgeSemanticsTest {

    @Test
    fun no_flags_produces_no_suffixes() {
        assertEquals(
            emptyList(),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = false, hasVoucher = false),
        )
    }

    @Test
    fun each_flag_maps_to_its_suffix() {
        assertEquals(
            listOf(DealBadgeSuffix.NEW_LOW),
            dealBadgeSuffixes(isNewHistoricalLow = true, isStoreLow = false, hasVoucher = false),
        )
        assertEquals(
            listOf(DealBadgeSuffix.STORE_LOW),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = true, hasVoucher = false),
        )
        assertEquals(
            listOf(DealBadgeSuffix.VOUCHER),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = false, hasVoucher = true),
        )
    }

    @Test
    fun new_low_precedes_voucher_in_announcement_order() {
        assertEquals(
            listOf(DealBadgeSuffix.NEW_LOW, DealBadgeSuffix.VOUCHER),
            dealBadgeSuffixes(isNewHistoricalLow = true, isStoreLow = false, hasVoucher = true),
        )
    }

    @Test
    fun store_low_precedes_voucher_in_announcement_order() {
        assertEquals(
            listOf(DealBadgeSuffix.STORE_LOW, DealBadgeSuffix.VOUCHER),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = true, hasVoucher = true),
        )
    }

    @Test
    fun an_all_time_low_that_is_not_new_is_announced() {
        assertEquals(
            listOf(DealBadgeSuffix.ALL_TIME_LOW),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = false, hasVoucher = false, isLowestEver = true),
        )
    }

    @Test
    fun a_new_low_is_not_also_announced_as_an_all_time_low() {
        assertEquals(
            listOf(DealBadgeSuffix.NEW_LOW),
            dealBadgeSuffixes(isNewHistoricalLow = true, isStoreLow = false, hasVoucher = false, isLowestEver = true),
        )
    }

    @Test
    fun one_low_price_letter_wins_new_then_all_time_then_store() {
        assertEquals(DealLowFlag.NEW_LOW, dealLowFlag(isNewHistoricalLow = true, isLowestEver = true, isStoreLow = true))
        assertEquals(DealLowFlag.ALL_TIME_LOW, dealLowFlag(isNewHistoricalLow = false, isLowestEver = true, isStoreLow = true))
        assertEquals(DealLowFlag.STORE_LOW, dealLowFlag(isNewHistoricalLow = false, isLowestEver = false, isStoreLow = true))
        assertEquals(null, dealLowFlag(isNewHistoricalLow = false, isLowestEver = false, isStoreLow = false))
    }
}
