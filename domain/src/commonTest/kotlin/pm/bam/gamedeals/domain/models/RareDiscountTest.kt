package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RareDiscountTest {

    private val week = 7L * 24 * 60 * 60 * 1000
    private val updated = 1_800_000_000_000L

    /** 104 weeks, oldest first, with the given cuts set by "weeks ago" (0 = the latest week). */
    private fun signals(vararg cutsByWeeksAgo: Pair<Int, Int>): GameSignals {
        val weeks = MutableList(104) { 0 }
        cutsByWeeksAgo.forEach { (ago, cut) -> weeks[103 - ago] = cut }
        return GameSignals(gameId = "g", updatedEpochMs = updated, weeklyMaxCut = weeks)
    }

    @Test
    fun the_deepest_discount_in_two_years_is_rare_with_no_earlier_date() {
        val game = signals(0 to 75, 30 to 50, 80 to 60)
        assertEquals(RareDiscount(lastThisDeepEpochMs = null), game.rareDiscount(75))
    }

    @Test
    fun as_deep_more_than_a_year_ago_is_rare_and_says_when() {
        val game = signals(0 to 75, 70 to 75, 90 to 80)
        assertEquals(RareDiscount(lastThisDeepEpochMs = updated - 70 * week), game.rareDiscount(75))
    }

    @Test
    fun as_deep_within_the_year_before_this_sale_is_not_rare() {
        assertNull(signals(0 to 75, 40 to 75).rareDiscount(75))
        assertNull(signals(0 to 75, 52 to 80).rareDiscount(75), "52 weeks before a one-week sale is inside the year")
    }

    @Test
    fun the_weeks_of_this_sale_do_not_count_against_it() {
        val twoWeekSale = signals(0 to 60, 1 to 60, 2 to 0, 60 to 60)
        assertEquals(RareDiscount(updated - 60 * week), twoWeekSale.rareDiscount(60))
    }

    @Test
    fun a_discount_that_has_lasted_more_than_six_weeks_is_the_usual_price_not_rare() {
        val long = signals(*(0..6).map { it to 50 }.toTypedArray())
        assertNull(long.rareDiscount(50))
        val six = signals(*(0..5).map { it to 50 }.toTypedArray())
        assertEquals(RareDiscount(null), six.rareDiscount(50))
    }

    @Test
    fun a_sale_newer_than_the_workers_copy_is_judged_on_the_weeks_before_it() {
        // The Worker fetched before this sale began, so its latest week shows no discount yet.
        assertEquals(RareDiscount(null), signals(10 to 40).rareDiscount(50))
        assertNull(signals(10 to 50).rareDiscount(50))
    }

    @Test
    fun small_discounts_are_never_rare() {
        assertNull(signals(0 to 15).rareDiscount(15))
        assertEquals(RareDiscount(null), signals(0 to 20).rareDiscount(20))
    }

    @Test
    fun too_little_history_tells_nothing() {
        assertNull(GameSignals("g", updated, weeklyMaxCut = emptyList()).rareDiscount(80))
        assertNull(GameSignals("g", updated, weeklyMaxCut = List(52) { 0 }).rareDiscount(80))
    }
}
