package pm.bam.gamedeals.remote.itad.mappers

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
}
