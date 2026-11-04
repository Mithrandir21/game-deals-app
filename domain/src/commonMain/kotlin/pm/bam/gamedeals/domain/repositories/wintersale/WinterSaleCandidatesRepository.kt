package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

data class WinterSaleCandidate(val gameId: String, val title: String, val artwork: GameArtwork, val offer: WinterSaleOffer, val adult: Boolean? = null)

data class WinterSaleRawCandidate(val gameId: String, val title: String, val artwork: GameArtwork, val adult: Boolean?)

data class WinterSaleRawSection(
    val games: List<WinterSaleRawCandidate>,
    val incomplete: Boolean = false,
    val unavailable: Boolean = false,
    val signInRequired: Boolean = false,
    val shortFeed: Boolean = false,
)

data class WinterSaleCandidateSection(
    val games: List<WinterSaleCandidate>,
    val incomplete: Boolean = false,
    val unavailable: Boolean = false,
    val signInRequired: Boolean = false,
    val shortFeed: Boolean = false,
)

data class WinterSalePopularGame(val gameId: String, val title: String, val artwork: GameArtwork, val type: String?, val mature: Boolean?)

interface WinterSalePopularSource {
    suspend fun fetch(country: String, includeAdult: Boolean, limit: Int): List<WinterSalePopularGame>
}

interface WinterSaleCandidatesRepository {
    suspend fun waitlistCandidates(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleRawSection
    suspend fun popularCandidates(country: String, includeAdult: Boolean): WinterSaleRawSection
    suspend fun waitlist(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleCandidateSection
    suspend fun popular(country: String, includeAdult: Boolean): WinterSaleCandidateSection
}

internal class WinterSaleCandidatesRepositoryImpl(
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val authTokenStore: AuthTokenStore,
    private val popularSource: WinterSalePopularSource,
    private val offersRepository: WinterSaleOffersRepository,
    private val clock: Clock,
    private val logger: Logger,
) : WinterSaleCandidatesRepository {
    override suspend fun waitlistCandidates(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleRawSection {
        if (authTokenStore.getAccessToken() == null) return WinterSaleRawSection(emptyList(), signInRequired = true)
        val entries = try { waitlistRepository.getWaitlist() } catch (error: CancellationException) { throw error } catch (t: Throwable) {
            error(logger, t)
            return WinterSaleRawSection(emptyList(), unavailable = true)
        }
        var snapshot = waitlistRepository.observeWaitlistDisplay().first()
        val now = clock.nowMillis()
        val stale = snapshot == null || snapshot.regionCode != country || snapshot.refreshedAtEpochMs > now ||
            now - snapshot.refreshedAtEpochMs >= WAITLIST_MAX_AGE_MS ||
            (saleActive && saleStartEpochMs != null && snapshot.refreshedAtEpochMs < saleStartEpochMs) ||
            snapshot.items.map { it.gameId }.toSet() != entries.map { it.gameId }.toSet()
        var refreshFailed = false
        if (stale) {
            try { waitlistRepository.refreshWaitlistDisplay() } catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                refreshFailed = true
            }
            snapshot = waitlistRepository.observeWaitlistDisplay().first()
        }
        if (snapshot == null || snapshot.regionCode != country || snapshot.items.map { it.gameId }.toSet() != entries.map { it.gameId }.toSet()) {
            return WinterSaleRawSection(emptyList(), incomplete = refreshFailed, unavailable = true)
        }
        val ranked = snapshot.items.asSequence()
            .filter { it.type == "game" && (includeAdult || it.mature == false) && it.hasDeal && (it.discountPercent ?: 0) > 0 }
            .sortedWith(compareByDescending<WaitlistDisplayItem> { it.isAtHistoricalLow }.thenByDescending { it.discountPercent ?: 0 }.thenBy { it.gameId })
            .take(WAITLIST_CANDIDATE_LIMIT).toList()
        return WinterSaleRawSection(ranked.map { WinterSaleRawCandidate(it.gameId, it.title, it.artwork, it.mature) },
            incomplete = snapshot.priceFetchIncomplete || refreshFailed)
    }

    override suspend fun popularCandidates(country: String, includeAdult: Boolean): WinterSaleRawSection {
        val fetched = try { popularSource.fetch(country, includeAdult, POPULAR_CANDIDATE_LIMIT) }
        catch (error: CancellationException) { throw error } catch (t: Throwable) {
            error(logger, t)
            return WinterSaleRawSection(emptyList(), unavailable = true)
        }
        val signedIn = authTokenStore.getAccessToken() != null
        val excluded = if (signedIn) {
            collectionRepository.observeCollectionIds().first() + waitlistRepository.observeWaitlistIds().first() + ignoredRepository.observeIgnoredIds().first()
        } else emptySet()
        val candidates = fetched.asSequence()
            .filter { it.type == "game" && (includeAdult || it.mature == false) && it.gameId !in excluded }
            .distinctBy { it.gameId }.toList()
        return WinterSaleRawSection(candidates.map { WinterSaleRawCandidate(it.gameId, it.title, it.artwork, it.mature) },
            shortFeed = candidates.size < SECTION_CANDIDATE_LIMIT)
    }

    override suspend fun waitlist(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleCandidateSection {
        val raw = waitlistCandidates(country, includeAdult, saleStartEpochMs, saleActive)
        return price(raw, country)
    }

    override suspend fun popular(country: String, includeAdult: Boolean): WinterSaleCandidateSection = price(popularCandidates(country, includeAdult), country)

    private suspend fun price(raw: WinterSaleRawSection, country: String): WinterSaleCandidateSection {
        val prices = offersRepository.load(raw.games.map { it.gameId }, country)
        val games = raw.games.mapNotNull { it.toCandidate(prices.perGame[it.gameId]) }
            .sortedWith(compareByDescending<WinterSaleCandidate> { it.offer.isAtHistoricalLow }
                .thenByDescending { it.offer.cutPercent }.thenBy { it.gameId }).take(SECTION_CANDIDATE_LIMIT)
        return WinterSaleCandidateSection(games, incomplete = raw.incomplete || prices.hasUnavailable(), unavailable = raw.unavailable,
            signInRequired = raw.signInRequired, shortFeed = raw.shortFeed || games.size < SECTION_CANDIDATE_LIMIT)
    }

    private fun WinterSaleRawCandidate.toCandidate(status: WinterSalePriceStatus?): WinterSaleCandidate? =
        (status as? WinterSalePriceStatus.Priced)?.offer?.takeIf { it.isDiscounted }?.let { WinterSaleCandidate(gameId, title, artwork, it, adult) }

    private fun WinterSaleOfferLoad.hasUnavailable(): Boolean = perGame.values.any { it == WinterSalePriceStatus.Unavailable }

    private companion object {
        const val WAITLIST_MAX_AGE_MS = 3 * 60 * 60 * 1000L
        const val POPULAR_CANDIDATE_LIMIT = 100
        const val SECTION_CANDIDATE_LIMIT = 30
        /**
         * Twice the section size: the snapshot's prices are older than the fresh price pass, so some top candidates may
         * no longer be discounted. The extra IDs ride in the same price batch (waitlist 60 + series 30 + popular 100 < 200).
         */
        const val WAITLIST_CANDIDATE_LIMIT = 60
    }
}
