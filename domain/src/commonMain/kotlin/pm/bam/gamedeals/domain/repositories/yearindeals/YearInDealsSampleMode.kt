package pm.bam.gamedeals.domain.repositories.yearindeals

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.domain.models.BiggestDrop
import pm.bam.gamedeals.domain.models.CollectedGames
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.LongestWait
import pm.bam.gamedeals.domain.models.WaitlistWins
import pm.bam.gamedeals.domain.models.YearGame
import pm.bam.gamedeals.domain.models.YearInDeals
import kotlin.coroutines.cancellation.CancellationException

/**
 * Developer switch: show the "Year in deals" entry on Home at any time of year, signed in or not, and fill the card
 * with [sampleYearInDeals] instead of the user's ITAD data. Lets the card's look and its sharing be checked on any
 * test phone. Set from the developer screen (debug builds only), and [isOn] is gated on [AppInfo.isDebug] as well,
 * so a release build never shows sample data.
 */
interface YearInDealsSampleMode {
    fun observe(): Flow<Boolean>
    suspend fun isOn(): Boolean
    suspend fun set(on: Boolean)
}

internal class YearInDealsSampleModeImpl(private val storage: Storage, private val appInfo: AppInfo) : YearInDealsSampleMode {

    private val stored = MutableStateFlow<Boolean?>(null)

    override fun observe(): Flow<Boolean> = stored
        .onStart { if (stored.value == null) stored.value = load() }
        .filterNotNull()
        .map { it && appInfo.isDebug }

    override suspend fun isOn(): Boolean {
        if (!appInfo.isDebug) return false
        return stored.value ?: load().also { stored.value = it }
    }

    override suspend fun set(on: Boolean) {
        if (on) storage.save(SAMPLE_MODE_KEY, true) else storage.remove(SAMPLE_MODE_KEY)
        stored.value = on
    }

    private suspend fun load(): Boolean = try {
        storage.getNullable<Boolean>(SAMPLE_MODE_KEY) == true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        false
    }
}

internal const val SAMPLE_MODE_KEY = "debug_year_in_deals_sample"

private fun sampleGame(id: String, title: String) =
    YearGame(id, title, GameArtwork(boxart = "https://assets.isthereanydeal.com/$id/boxart.jpg", banner300 = "https://assets.isthereanydeal.com/$id/banner300.jpg"))

/** Real games with real covers, made-up numbers. Never shown in a release build. */
internal fun sampleYearInDeals(year: Int) = YearInDeals(
    year = year,
    collected = CollectedGames(
        count = 23,
        highlights = persistentListOf(
            sampleGame("018d937f-33f0-7200-80fc-87f769196c84", "Hades"),
            sampleGame("018d937f-1ae9-734c-ba47-bd357cf07edd", "Hollow Knight"),
            sampleGame("018d937e-f835-710c-b95b-928e277b187e", "Stardew Valley"),
            sampleGame("018d937f-1382-7004-b65b-e6adfce11377", "Celeste"),
        ),
    ),
    fullPriceValue = "$412.77",
    waitlistWins = WaitlistWins(
        count = 7,
        biggestDrop = BiggestDrop(
            game = sampleGame("018d937f-2997-7131-b8b9-7c8af4825fa8", "Cyberpunk 2077"),
            cutPercent = 65,
            lowDenominated = "$20.99",
            regularDenominated = "$59.99",
        ),
    ),
    longestWait = LongestWait(sampleGame("018d937f-1ae9-734c-ba47-bd357cf07edd", "Hollow Knight"), days = 731),
)
