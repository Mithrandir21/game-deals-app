package pm.bam.gamedeals.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DealBadgeSemanticsTest {

    @Test
    fun `no flags produces no suffixes`() {
        assertEquals(
            emptyList(),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = false, hasVoucher = false),
        )
    }

    @Test
    fun `each flag maps to its suffix`() {
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
    fun `new-low precedes voucher in announcement order`() {
        assertEquals(
            listOf(DealBadgeSuffix.NEW_LOW, DealBadgeSuffix.VOUCHER),
            dealBadgeSuffixes(isNewHistoricalLow = true, isStoreLow = false, hasVoucher = true),
        )
    }

    @Test
    fun `store-low precedes voucher in announcement order`() {
        assertEquals(
            listOf(DealBadgeSuffix.STORE_LOW, DealBadgeSuffix.VOUCHER),
            dealBadgeSuffixes(isNewHistoricalLow = false, isStoreLow = true, hasVoucher = true),
        )
    }
}
