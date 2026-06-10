package pm.bam.gamedeals.remote.itad.mappers

import pm.bam.gamedeals.remote.itad.models.RemoteItadDealEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadPrice
import pm.bam.gamedeals.remote.itad.models.RemoteItadShopRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItadMappersTest {

    @Test
    fun isHistoryLowFlag_true_only_for_new_or_at_historical_low() {
        assertTrue("N".isHistoryLowFlag()) // price just hit a new historical low
        assertTrue("H".isHistoryLowFlag()) // currently at the historical low
    }

    @Test
    fun isHistoryLowFlag_false_for_other_or_missing_flags() {
        assertFalse("S".isHistoryLowFlag()) // other ITAD marker
        assertFalse(null.isHistoryLowFlag()) // no flag
        assertFalse("".isHistoryLowFlag())
        assertFalse("n".isHistoryLowFlag()) // case-sensitive; ITAD emits uppercase
    }

    @Test
    fun isNewHistoryLowFlag_true_only_for_new_low() {
        assertTrue("N".isNewHistoryLowFlag())
        assertFalse("H".isNewHistoryLowFlag()) // at the low, but not *new*
        assertFalse("S".isNewHistoryLowFlag())
        assertFalse(null.isNewHistoryLowFlag())
        assertFalse("n".isNewHistoryLowFlag()) // case-sensitive
    }

    @Test
    fun isStoreLowFlag_true_only_for_store_low() {
        assertTrue("S".isStoreLowFlag())
        assertFalse("N".isStoreLowFlag())
        assertFalse("H".isStoreLowFlag())
        assertFalse(null.isStoreLowFlag())
        assertFalse("s".isStoreLowFlag()) // case-sensitive
    }

    @Test
    fun isVoucherPresent_true_only_for_non_blank_code() {
        assertTrue("SUMMER10".isVoucherPresent())
        assertFalse(null.isVoucherPresent())
        assertFalse("".isVoucherPresent())
        assertFalse("   ".isVoucherPresent()) // blank-only is treated as absent
    }

    @Test
    fun toItadDeal_maps_flag_and_voucher_to_discrete_badge_signals() {
        // flag "N" + a voucher → new historical low (which is also a historical low) + voucher present.
        dealEntry(flag = "N", voucher = "SUMMER10").toItadDeal(gameId = "uuid", gameTitle = "Game").let {
            assertTrue(it.isLowestEver)
            assertTrue(it.isNewHistoricalLow)
            assertFalse(it.isStoreLow)
            assertTrue(it.hasVoucher)
        }
        // flag "H" → at the historical low, but not new, and no voucher.
        dealEntry(flag = "H", voucher = null).toItadDeal(gameId = "uuid", gameTitle = "Game").let {
            assertTrue(it.isLowestEver)
            assertFalse(it.isNewHistoricalLow)
            assertFalse(it.isStoreLow)
            assertFalse(it.hasVoucher)
        }
        // flag "S" → store low only (not a historical low).
        dealEntry(flag = "S", voucher = null).toItadDeal(gameId = "uuid", gameTitle = "Game").let {
            assertFalse(it.isLowestEver)
            assertFalse(it.isNewHistoricalLow)
            assertTrue(it.isStoreLow)
            assertFalse(it.hasVoucher)
        }
        // No flag, no voucher → no badge signals.
        dealEntry(flag = null, voucher = null).toItadDeal(gameId = "uuid", gameTitle = "Game").let {
            assertFalse(it.isLowestEver)
            assertFalse(it.isNewHistoricalLow)
            assertFalse(it.isStoreLow)
            assertFalse(it.hasVoucher)
        }
    }

    private fun dealEntry(flag: String?, voucher: String?): RemoteItadDealEntry =
        RemoteItadDealEntry(
            shop = RemoteItadShopRef(id = 61, name = "Steam"),
            price = RemoteItadPrice(amount = 9.99, currency = "USD"),
            regular = RemoteItadPrice(amount = 19.99, currency = "USD"),
            cut = 50,
            url = "https://store/game",
            flag = flag,
            voucher = voucher,
        )
}
