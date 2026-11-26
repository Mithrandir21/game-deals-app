package pm.bam.gamedeals.domain.repositories.yearindeals

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.TimeZone
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.GameHistoryLow
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import kotlin.coroutines.cancellation.CancellationException

/**
 * The signed-in user's "Year in deals" (see [YearInDeals]). Offered on Home in December and January only, behind
 * [FeatureFlag.YearInDeals]; the card is about the current year in December and the previous year in January.
 */
interface YearInDealsRepository {

    /** True when the card should be offered right now: in season and the flag is on. Sign-in is checked by the caller. */
    fun isOffered(): Boolean

    /** The year the card is about right now: this year in December, last year in January. */
    fun reportYear(): Int

    /**
     * Builds the card from the user's ITAD collection and waitlist. Throws when those can't be loaded (signed out,
     * offline). Prices and all-time lows are best-effort: if they fail, their sections are simply left out.
     */
    suspend fun getYearInDeals(): YearInDeals
}

internal class YearInDealsRepositoryImpl(
    private val logger: Logger,
    private val collectionRepository: CollectionRepository,
    private val waitlistRepository: WaitlistRepository,
    private val dealsSource: DealsSource,
    private val featureFlags: FeatureFlags,
    private val sampleMode: YearInDealsSampleMode,
    private val clock: Clock,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) : YearInDealsRepository {

    override fun isOffered(): Boolean =
        featureFlags.isEnabled(FeatureFlag.YearInDeals) && YearInDealsCalculator.isSeasonOpen(clock.nowMillis(), timeZone())

    override fun reportYear(): Int = YearInDealsCalculator.reportYear(clock.nowMillis(), timeZone())

    override suspend fun getYearInDeals(): YearInDeals = coroutineScope {
        val now = clock.nowMillis()
        val zone = timeZone()
        val year = YearInDealsCalculator.reportYear(now, zone)
        if (sampleMode.isOn()) return@coroutineScope sampleYearInDeals(year)

        val collectionAsync = async { collectionRepository.getCollection() }
        val waitlistAsync = async { waitlistRepository.getWaitlist() }
        val collection = collectionAsync.await()
        val waitlist = waitlistAsync.await()

        val countedIds = YearInDealsCalculator.collectedThisYear(year, zone, collection).map { it.gameId }
        val pricesAsync = async { bestEffort("prices") { countedIds.chunked(BATCH).flatMap { dealsSource.fetchBundleGamePrices(it) } } }
        val lowsAsync = async { bestEffort("lows") { waitlist.map { it.gameId }.chunked(BATCH).flatMap { dealsSource.fetchHistoryLows(it) } } }

        YearInDealsCalculator.compute(
            year = year,
            zone = zone,
            nowEpochMs = now,
            collection = collection,
            waitlist = waitlist,
            lows = lowsAsync.await().orEmpty<GameHistoryLow>(),
            prices = pricesAsync.await().orEmpty<BundleGamePrice>(),
        )
    }

    private suspend fun <T> bestEffort(what: String, block: suspend () -> List<T>): List<T>? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.log(LogLevel.DEBUG, tag = TAG, throwable = e) { "Year in deals: $what unavailable" }
        null
    }

    private companion object {
        const val TAG = "YearInDeals"
        /** Same batch size as the waitlist price refresh. */
        const val BATCH = 100
    }
}
