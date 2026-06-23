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
}
