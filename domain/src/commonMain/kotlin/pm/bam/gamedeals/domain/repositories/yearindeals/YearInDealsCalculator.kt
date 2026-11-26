package pm.bam.gamedeals.domain.repositories.yearindeals

import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.domain.models.BiggestDrop
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.CollectedGames
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.GameHistoryLow
import pm.bam.gamedeals.domain.models.LongestWait
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.models.WaitlistWins
import pm.bam.gamedeals.domain.models.YearGame
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.utils.formatMoney
import kotlin.time.Instant

/**
 * The pure arithmetic behind [YearInDeals]: no network, no clock, so every rule is pinned by tests.
 * [YearInDealsRepository] fetches the inputs and calls [compute].
 */
internal object YearInDealsCalculator {

    /**
     * More additions than this on one day reads as a library import or sync, not games collected one by one, so
     * that whole day is left out. Otherwise anyone who linked Steam this year would "collect" their entire library.
     */
    const val BULK_DAY_THRESHOLD = 15

    /** How many covers the collected section shows. */
    const val HIGHLIGHT_COUNT = 4

    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun compute(
        year: Int,
        zone: TimeZone,
        nowEpochMs: Long,
        collection: List<CollectionEntry>,
        waitlist: List<WaitlistEntry>,
        lows: List<GameHistoryLow>,
        prices: List<BundleGamePrice>,
    ): YearInDeals {
        val collectedThisYear = collectedThisYear(year, zone, collection)
        return YearInDeals(
            year = year,
            collected = collectedThisYear.takeIf { it.isNotEmpty() }?.let { games ->
                CollectedGames(
                    count = games.size,
                    highlights = games.take(HIGHLIGHT_COUNT).map { it.toYearGame() }.toImmutableList(),
                )
            },
            fullPriceValue = fullPriceValue(collectedThisYear.mapTo(HashSet()) { it.gameId }, prices),
            waitlistWins = waitlistWins(year, zone, waitlist, lows),
            longestWait = longestWait(nowEpochMs, waitlist),
        )
    }

    /** This year's collection additions, newest first, without bulk-import days. */
    fun collectedThisYear(year: Int, zone: TimeZone, collection: List<CollectionEntry>): List<CollectionEntry> {
        val inYear = collection.filter { entry -> entry.addedEpochMs?.let { yearOf(it, zone) == year } == true }
        val bulkDays = inYear.groupingBy { localDate(it.addedEpochMs!!, zone) }.eachCount()
            .filterValues { it > BULK_DAY_THRESHOLD }.keys
        return inYear
            .filterNot { localDate(it.addedEpochMs!!, zone) in bulkDays }
            .sortedByDescending { it.addedEpochMs }
    }

    /**
     * The sum of regular prices for [gameIds] in the most common currency (all prices come from one region, so
     * mixed currencies only happen if the region changed mid-fetch). Null when no game has a price.
     */
    fun fullPriceValue(gameIds: Set<String>, prices: List<BundleGamePrice>): String? {
        val priced = prices.filter { it.gameId in gameIds && it.bestRegularValue != null && it.currency != null }
            .distinctBy { it.gameId }
        val currency = priced.groupingBy { it.currency!! }.eachCount().maxByOrNull { it.value }?.key ?: return null
        val total = priced.filter { it.currency == currency }.sumOf { it.bestRegularValue!! }
        return if (total > 0.0) formatMoney(total, currency) else null
    }

    /** Waitlisted games whose all-time low was set during [year], and the deepest cut among them. */
    fun waitlistWins(year: Int, zone: TimeZone, waitlist: List<WaitlistEntry>, lows: List<GameHistoryLow>): WaitlistWins? {
        val waitlisted = waitlist.associateBy { it.gameId }
        val wins = lows.filter { low ->
            low.gameId in waitlisted && low.setAtEpochMs?.let { yearOf(it, zone) == year } == true && low.cutPercent > 0
        }.distinctBy { it.gameId }
        if (wins.isEmpty()) return null
        val deepest = wins.maxWith(compareBy<GameHistoryLow> { it.cutPercent }.thenBy { (it.regular ?: it.price) - it.price })
        return WaitlistWins(
            count = wins.size,
            biggestDrop = BiggestDrop(
                game = waitlisted.getValue(deepest.gameId).toYearGame(),
                cutPercent = deepest.cutPercent,
                lowDenominated = formatMoney(deepest.price, deepest.currency),
                regularDenominated = deepest.regular?.let { formatMoney(it, deepest.currency) },
            ),
        )
    }

    /** The waitlist's oldest entry, in whole days. Null when no entry has a date or the oldest is under a day old. */
    fun longestWait(nowEpochMs: Long, waitlist: List<WaitlistEntry>): LongestWait? {
        val oldest = waitlist.filter { it.addedEpochMs != null }.minByOrNull { it.addedEpochMs!! } ?: return null
        val days = ((nowEpochMs - oldest.addedEpochMs!!) / DAY_MS).toInt()
        return if (days >= 1) LongestWait(oldest.toYearGame(), days) else null
    }

    /** The card is offered from 1 December to 31 January in the user's time zone: year-end recap season. */
    fun isSeasonOpen(nowEpochMs: Long, zone: TimeZone): Boolean = localDate(nowEpochMs, zone).month.number.let { it == 12 || it == 1 }

    /** The year the card is about: this year in December, last year in January (and any other month). */
    fun reportYear(nowEpochMs: Long, zone: TimeZone): Int = localDate(nowEpochMs, zone).let { if (it.month.number == 12) it.year else it.year - 1 }

    fun yearStartEpochMs(year: Int, zone: TimeZone): Long = LocalDate(year, 1, 1).atStartOfDayIn(zone).toEpochMilliseconds()

    private fun yearOf(epochMs: Long, zone: TimeZone): Int = localDate(epochMs, zone).year

    private fun localDate(epochMs: Long, zone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date

    private fun CollectionEntry.toYearGame() = YearGame(gameId, title, artwork)
    private fun WaitlistEntry.toYearGame() = YearGame(gameId, title, artwork)
}
