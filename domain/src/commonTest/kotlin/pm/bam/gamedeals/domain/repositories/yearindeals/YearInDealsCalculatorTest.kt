package pm.bam.gamedeals.domain.repositories.yearindeals

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.GameHistoryLow
import pm.bam.gamedeals.domain.models.WaitlistEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearInDealsCalculatorTest {

    private val zone = TimeZone.UTC

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime(year, month, day, hour, 0).toInstant(zone).toEpochMilliseconds()

    private fun collected(id: String, added: Long?) = CollectionEntry(gameId = id, title = "Game $id", addedEpochMs = added)
    private fun waitlisted(id: String, added: Long?) = WaitlistEntry(gameId = id, title = "Game $id", addedEpochMs = added)
    private fun low(id: String, price: Double, regular: Double?, cut: Int, setAt: Long?) =
        GameHistoryLow(gameId = id, price = price, regular = regular, cutPercent = cut, currency = "USD", setAtEpochMs = setAt)
    private fun price(id: String, regular: Double?, currency: String? = "USD") = BundleGamePrice(
        gameId = id, bestShopName = "Steam", bestPriceValue = regular, bestPriceDenominated = null, bestCutPercent = 0,
        historicalLowValue = null, historicalLowDenominated = null, currency = currency, bestRegularValue = regular,
    )

    private val now = at(2026, 12, 15)

    @Test
    fun counts_only_games_added_this_year_newest_first() {
        val result = YearInDealsCalculator.collectedThisYear(
            2026, zone,
            listOf(collected("old", at(2025, 12, 31, 23)), collected("a", at(2026, 1, 1, 0)), collected("b", at(2026, 6, 1)), collected("none", null)),
        )
        assertEquals(listOf("b", "a"), result.map { it.gameId })
    }

    @Test
    fun a_day_with_more_additions_than_the_threshold_is_treated_as_an_import_and_left_out() {
        val importDay = (1..YearInDealsCalculator.BULK_DAY_THRESHOLD + 1).map { collected("import$it", at(2026, 3, 10)) }
        val busyButReal = (1..YearInDealsCalculator.BULK_DAY_THRESHOLD).map { collected("busy$it", at(2026, 7, 4)) }
        val result = YearInDealsCalculator.collectedThisYear(2026, zone, importDay + busyButReal + collected("solo", at(2026, 9, 9)))
        assertEquals(YearInDealsCalculator.BULK_DAY_THRESHOLD + 1, result.size)
        assertTrue(result.none { it.gameId.startsWith("import") })
    }

    @Test
    fun the_full_price_value_sums_regular_prices_of_the_counted_games_only() {
        val value = YearInDealsCalculator.fullPriceValue(
            setOf("a", "b", "c"),
            listOf(price("a", 19.99), price("b", 30.01), price("c", null), price("not-counted", 100.0)),
        )
        assertEquals("$50.00", value)
    }

    @Test
    fun the_full_price_value_ignores_a_stray_currency_and_is_absent_without_prices() {
        assertEquals("$10.00", YearInDealsCalculator.fullPriceValue(setOf("a", "b", "c"), listOf(price("a", 5.0), price("b", 5.0), price("c", 99.0, "EUR"))))
        assertNull(YearInDealsCalculator.fullPriceValue(setOf("a"), emptyList()))
        assertNull(YearInDealsCalculator.fullPriceValue(setOf("a"), listOf(price("a", 0.0))))
    }

    @Test
    fun waitlist_wins_are_lows_set_this_year_on_waitlisted_games_with_the_deepest_cut_as_the_biggest_drop() {
        val waitlist = listOf(waitlisted("w1", at(2024, 1, 1)), waitlisted("w2", at(2026, 2, 1)), waitlisted("w3", at(2025, 5, 5)))
        val wins = YearInDealsCalculator.waitlistWins(
            2026, zone, waitlist,
            listOf(
                low("w1", 4.99, 19.99, 75, at(2026, 6, 26)),
                low("w2", 9.99, 59.99, 83, at(2026, 11, 27)),
                low("w3", 1.0, 10.0, 90, at(2025, 11, 1)), // set last year
                low("not-waitlisted", 0.99, 9.99, 90, at(2026, 1, 1)),
            ),
        )!!
        assertEquals(2, wins.count)
        assertEquals("w2", wins.biggestDrop!!.game.gameId)
        assertEquals(83, wins.biggestDrop!!.cutPercent)
        assertEquals("$9.99", wins.biggestDrop!!.lowDenominated)
        assertEquals("$59.99", wins.biggestDrop!!.regularDenominated)
    }

    @Test
    fun a_tie_on_cut_goes_to_the_bigger_money_drop_and_free_or_undated_lows_do_not_count() {
        val waitlist = listOf(waitlisted("cheap", null), waitlisted("pricey", null), waitlisted("free", null), waitlisted("undated", null))
        val wins = YearInDealsCalculator.waitlistWins(
            2026, zone, waitlist,
            listOf(
                low("cheap", 2.5, 10.0, 75, at(2026, 3, 1)),
                low("pricey", 15.0, 60.0, 75, at(2026, 3, 1)),
                low("free", 0.0, 0.0, 0, at(2026, 3, 1)),
                low("undated", 1.0, 10.0, 90, null),
            ),
        )!!
        assertEquals(2, wins.count)
        assertEquals("pricey", wins.biggestDrop!!.game.gameId)
    }

    @Test
    fun no_wins_means_no_section() {
        assertNull(YearInDealsCalculator.waitlistWins(2026, zone, listOf(waitlisted("w", null)), emptyList()))
    }

    @Test
    fun the_longest_wait_is_the_oldest_dated_waitlist_entry_in_whole_days() {
        val wait = YearInDealsCalculator.longestWait(
            now,
            listOf(waitlisted("new", at(2026, 12, 1)), waitlisted("oldest", at(2024, 12, 15)), waitlisted("undated", null)),
        )!!
        assertEquals("oldest", wait.game.gameId)
        assertEquals(730, wait.days)
    }

    @Test
    fun a_wait_under_a_day_or_an_undated_waitlist_has_no_longest_wait() {
        assertNull(YearInDealsCalculator.longestWait(now, listOf(waitlisted("today", now - 1000))))
        assertNull(YearInDealsCalculator.longestWait(now, listOf(waitlisted("undated", null))))
        assertNull(YearInDealsCalculator.longestWait(now, emptyList()))
    }

    @Test
    fun compute_puts_it_together_and_an_empty_account_is_empty() {
        val year = YearInDealsCalculator.compute(
            year = 2026, zone = zone, nowEpochMs = now,
            collection = (1..6).map { collected("c$it", at(2026, it, 1)) },
            waitlist = listOf(waitlisted("w", at(2025, 1, 1))),
            lows = listOf(low("w", 5.0, 20.0, 75, at(2026, 8, 1))),
            prices = (1..6).map { price("c$it", 10.0) },
        )
        assertEquals(6, year.collected!!.count)
        assertEquals(YearInDealsCalculator.HIGHLIGHT_COUNT, year.collected!!.highlights.size)
        assertEquals("c6", year.collected!!.highlights.first().gameId)
        assertEquals("$60.00", year.fullPriceValue)
        assertEquals(1, year.waitlistWins!!.count)
        assertEquals("w", year.longestWait!!.game.gameId)

        val empty = YearInDealsCalculator.compute(2026, zone, now, emptyList(), emptyList(), emptyList(), emptyList())
        assertTrue(empty.isEmpty)
        assertNull(empty.fullPriceValue)
    }

    @Test
    fun the_card_is_in_season_in_december_and_january_only() {
        assertTrue(YearInDealsCalculator.isSeasonOpen(at(2026, 12, 1, 0), zone))
        assertTrue(YearInDealsCalculator.isSeasonOpen(at(2027, 1, 31, 23), zone))
        assertEquals(false, YearInDealsCalculator.isSeasonOpen(at(2026, 11, 30, 23), zone))
        assertEquals(false, YearInDealsCalculator.isSeasonOpen(at(2027, 2, 1, 0), zone))
    }

    @Test
    fun december_reports_this_year_and_january_reports_last_year() {
        assertEquals(2026, YearInDealsCalculator.reportYear(at(2026, 12, 20), zone))
        assertEquals(2026, YearInDealsCalculator.reportYear(at(2027, 1, 15), zone))
    }

    @Test
    fun the_year_boundary_follows_the_users_time_zone() {
        val oslo = TimeZone.of("Europe/Oslo")
        // 23:30 UTC on 31 Dec 2025 is already 00:30 on 1 Jan 2026 in Oslo.
        val lateNewYearsEve = LocalDateTime(2025, 12, 31, 23, 30).toInstant(TimeZone.UTC).toEpochMilliseconds()
        assertEquals(1, YearInDealsCalculator.collectedThisYear(2026, oslo, listOf(collected("x", lateNewYearsEve))).size)
        assertEquals(0, YearInDealsCalculator.collectedThisYear(2026, TimeZone.UTC, listOf(collected("x", lateNewYearsEve))).size)
    }
}
