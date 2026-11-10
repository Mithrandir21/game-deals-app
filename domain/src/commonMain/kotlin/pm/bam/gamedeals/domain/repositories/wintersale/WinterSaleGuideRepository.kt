package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

enum class WinterSaleGuideSectionKind { Waitlist, Series, Popular }

data class WinterSaleGuideCard(
    val candidate: WinterSaleCandidate,
    val reasons: List<String>,
    val adultAllowedOff: Boolean,
    /** The followed series this game belongs to, in follow order; the guide groups the Series section by the first. */
    val seriesNames: List<String> = emptyList(),
)

data class WinterSaleGuideSection(
    val kind: WinterSaleGuideSectionKind,
    val cards: List<WinterSaleGuideCard>,
    val incomplete: Boolean = false,
    val unavailable: Boolean = false,
    val signInRequired: Boolean = false,
    val checking: Boolean = false,
    val shortFeed: Boolean = false,
    val moreSeriesFollowed: Boolean = false,
)

data class WinterSaleGuideContent(
    val country: String,
    val checkedAtEpochMillis: Long,
    val waitlist: WinterSaleGuideSection,
    val series: WinterSaleGuideSection,
    val popular: WinterSaleGuideSection,
) {
    val sections: List<WinterSaleGuideSection> get() = listOf(waitlist, series, popular)
    val hasCards: Boolean get() = sections.any { it.cards.isNotEmpty() }
    val hasFailure: Boolean get() = sections.any { it.incomplete || it.unavailable }

    fun forAdultSetting(includeAdult: Boolean): WinterSaleGuideContent = if (includeAdult) this else copy(
        waitlist = waitlist.copy(cards = waitlist.cards.filter { it.adultAllowedOff }),
        series = series.copy(cards = series.cards.filter { it.adultAllowedOff }),
        popular = popular.copy(cards = popular.cards.filter { it.adultAllowedOff },
            shortFeed = popular.shortFeed || popular.cards.count { it.adultAllowedOff } < 30),
    )
}

interface WinterSaleGuideRepository {
    suspend fun load(country: String, includeAdult: Boolean, saleStartEpochMs: Long, saleActive: Boolean): WinterSaleGuideContent
}

internal class WinterSaleGuideRepositoryImpl(
    private val candidatesRepository: WinterSaleCandidatesRepository,
    private val seriesRepository: WinterSaleSeriesRepository,
    private val offersRepository: WinterSaleOffersRepository,
    private val logger: Logger,
) : WinterSaleGuideRepository {
    override suspend fun load(country: String, includeAdult: Boolean, saleStartEpochMs: Long, saleActive: Boolean): WinterSaleGuideContent = coroutineScope {
        val waitlist = async {
            try { candidatesRepository.waitlistCandidates(country, includeAdult, saleStartEpochMs, saleActive) }
            catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                WinterSaleRawSection(emptyList(), unavailable = true)
            }
        }
        val series = async {
            try { seriesRepository.candidates(includeAdult) }
            catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                WinterSaleSeriesRawSection(emptyList(), checking = false, unavailable = true, moreSeriesFollowed = false)
            }
        }
        val popular = async {
            try { candidatesRepository.popularCandidates(country, includeAdult) }
            catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                WinterSaleRawSection(emptyList(), unavailable = true)
            }
        }
        val wait = waitlist.await()
        val followed = series.await()
        val ranked = popular.await()
        val ids = (wait.games.map { it.gameId } + followed.games.map { it.game.gameId } + ranked.games.map { it.gameId }).distinct()
        val offers = offersRepository.load(ids, country)
        assemble(country, wait, followed, ranked, offers)
    }

    private fun assemble(
        country: String,
        wait: WinterSaleRawSection,
        followed: WinterSaleSeriesRawSection,
        ranked: WinterSaleRawSection,
        offers: WinterSaleOfferLoad,
    ): WinterSaleGuideContent {
        val reasons = linkedMapOf<String, MutableList<String>>()
        val adultAllowed = linkedMapOf<String, Boolean>()
        val seriesNames = linkedMapOf<String, MutableList<String>>()
        fun add(game: WinterSaleRawCandidate, labels: List<String>) {
            reasons.getOrPut(game.gameId) { mutableListOf() }.addAll(labels)
            adultAllowed[game.gameId] = (adultAllowed[game.gameId] ?: true) && game.adult == false
        }
        wait.games.forEach { add(it, listOf("On your waitlist")) }
        followed.games.forEach {
            add(it.game, it.seriesNames.map { name -> "From $name" })
            seriesNames.getOrPut(it.game.gameId) { mutableListOf() }.addAll(it.seriesNames)
        }
        ranked.games.forEach { add(it, listOf("Popular on ITAD")) }
        val seen = mutableSetOf<String>()
        fun cards(raw: List<WinterSaleRawCandidate>): List<WinterSaleGuideCard> = raw.mapNotNull { game ->
            val offer = (offers.perGame[game.gameId] as? WinterSalePriceStatus.Priced)?.offer?.takeIf { it.isDiscounted }
                ?: return@mapNotNull null
            WinterSaleGuideCard(WinterSaleCandidate(game.gameId, game.title, game.artwork, offer, game.adult),
                reasons[game.gameId].orEmpty().distinct(), adultAllowed[game.gameId] == true,
                seriesNames[game.gameId].orEmpty().distinct())
        }.distinctBy { it.candidate.gameId }
            .sortedWith(compareByDescending<WinterSaleGuideCard> { it.candidate.offer.isAtHistoricalLow }
                .thenByDescending { it.candidate.offer.cutPercent }.thenBy { it.candidate.gameId })
            .filter { it.candidate.gameId !in seen }.take(SECTION_LIMIT).also { selected ->
                seen.addAll(selected.map { it.candidate.gameId })
            }

        fun failed(ids: List<String>) = ids.any { offers.perGame[it] == WinterSalePriceStatus.Unavailable }
        val waitCards = cards(wait.games)
        val seriesCards = cards(followed.games.map { it.game })
        val popularCards = cards(ranked.games)
        return WinterSaleGuideContent(
            country = country,
            checkedAtEpochMillis = offers.refreshedAtEpochMillis,
            waitlist = WinterSaleGuideSection(WinterSaleGuideSectionKind.Waitlist, waitCards,
                incomplete = wait.incomplete || failed(wait.games.map { it.gameId }), unavailable = wait.unavailable,
                signInRequired = wait.signInRequired),
            series = WinterSaleGuideSection(WinterSaleGuideSectionKind.Series, seriesCards,
                incomplete = failed(followed.games.map { it.game.gameId }), unavailable = followed.unavailable,
                checking = followed.checking, moreSeriesFollowed = followed.moreSeriesFollowed),
            popular = WinterSaleGuideSection(WinterSaleGuideSectionKind.Popular, popularCards,
                incomplete = failed(ranked.games.map { it.gameId }), unavailable = ranked.unavailable,
                shortFeed = ranked.shortFeed || popularCards.size < SECTION_LIMIT),
        )
    }

    private companion object { const val SECTION_LIMIT = 30 }
}
