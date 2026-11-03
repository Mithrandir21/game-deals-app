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

data class WinterSaleCandidate(val gameId: String, val title: String, val artwork: GameArtwork, val offer: WinterSaleOffer)

data class WinterSaleCandidateSection(
    val games: List<WinterSaleCandidate>,
    val incomplete: Boolean = false,
    val unavailable: Boolean = false,
    val signInRequired: Boolean = false,
    val moreCandidatesAvailable: Boolean = false,
)

data class WinterSalePopularGame(val gameId: String, val title: String, val artwork: GameArtwork, val type: String?, val mature: Boolean?)

interface WinterSalePopularSource {
    suspend fun fetch(country: String, includeAdult: Boolean, limit: Int): List<WinterSalePopularGame>
}

interface WinterSaleCandidatesRepository {
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
) : WinterSaleCandidatesRepository {
    override suspend fun waitlist(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleCandidateSection {
        if (authTokenStore.getAccessToken() == null) return WinterSaleCandidateSection(emptyList(), signInRequired = true)
        val entries = try { waitlistRepository.getWaitlist() } catch (error: CancellationException) { throw error } catch (_: Throwable) {
            return WinterSaleCandidateSection(emptyList(), unavailable = true)
        }
        var snapshot = waitlistRepository.observeWaitlistDisplay().first()
        val now = clock.nowMillis()
        val stale = snapshot == null || snapshot.regionCode != country || snapshot.refreshedAtEpochMs > now ||
            now - snapshot.refreshedAtEpochMs >= WAITLIST_MAX_AGE_MS ||
            (saleActive && saleStartEpochMs != null && snapshot.refreshedAtEpochMs < saleStartEpochMs) ||
            snapshot.items.map { it.gameId }.toSet() != entries.map { it.gameId }.toSet()
        var refreshFailed = false
        if (stale) {
            try { waitlistRepository.refreshWaitlistDisplay() } catch (error: CancellationException) { throw error } catch (_: Throwable) { refreshFailed = true }
            snapshot = waitlistRepository.observeWaitlistDisplay().first()
        }
        if (snapshot == null || snapshot.regionCode != country || snapshot.items.map { it.gameId }.toSet() != entries.map { it.gameId }.toSet()) {
            return WinterSaleCandidateSection(emptyList(), incomplete = refreshFailed, unavailable = true)
        }
        val ranked = snapshot.items.asSequence()
            .filter { it.type == "game" && (includeAdult || it.mature == false) && it.hasDeal && (it.discountPercent ?: 0) > 0 }
            .sortedWith(compareByDescending<WaitlistDisplayItem> { it.isAtHistoricalLow }.thenByDescending { it.discountPercent ?: 0 }.thenBy { it.gameId })
            .take(SECTION_CANDIDATE_LIMIT).toList()
        val prices = offersRepository.load(ranked.map { it.gameId }, country)
        val games = ranked.mapNotNull { item -> item.toCandidate(prices.perGame[item.gameId]) }
        return WinterSaleCandidateSection(games, incomplete = snapshot.priceFetchIncomplete || refreshFailed || prices.hasUnavailable())
    }

    override suspend fun popular(country: String, includeAdult: Boolean): WinterSaleCandidateSection {
        val fetched = try { popularSource.fetch(country, includeAdult, POPULAR_CANDIDATE_LIMIT) }
        catch (error: CancellationException) { throw error } catch (_: Throwable) { return WinterSaleCandidateSection(emptyList(), unavailable = true) }
        val signedIn = authTokenStore.getAccessToken() != null
        val excluded = if (signedIn) {
            collectionRepository.observeCollectionIds().first() + waitlistRepository.observeWaitlistIds().first() + ignoredRepository.observeIgnoredIds().first()
        } else emptySet()
        val candidates = fetched.asSequence()
            .filter { it.type == "game" && (includeAdult || it.mature == false) && it.gameId !in excluded }
            .distinctBy { it.gameId }.toList()
        val prices = offersRepository.load(candidates.map { it.gameId }, country)
        val games = candidates.mapNotNull { item -> item.toCandidate(prices.perGame[item.gameId]) }
            .sortedWith(compareByDescending<WinterSaleCandidate> { it.offer.isAtHistoricalLow }
                .thenByDescending { it.offer.cutPercent }.thenBy { it.gameId })
            .take(SECTION_CANDIDATE_LIMIT)
        return WinterSaleCandidateSection(games, incomplete = prices.hasUnavailable(), moreCandidatesAvailable = games.size < SECTION_CANDIDATE_LIMIT)
    }

    private fun WaitlistDisplayItem.toCandidate(status: WinterSalePriceStatus?): WinterSaleCandidate? =
        (status as? WinterSalePriceStatus.Priced)?.offer?.takeIf { it.isDiscounted }?.let { WinterSaleCandidate(gameId, title, artwork, it) }

    private fun WinterSalePopularGame.toCandidate(status: WinterSalePriceStatus?): WinterSaleCandidate? =
        (status as? WinterSalePriceStatus.Priced)?.offer?.takeIf { it.isDiscounted }?.let { WinterSaleCandidate(gameId, title, artwork, it) }

    private fun WinterSaleOfferLoad.hasUnavailable(): Boolean = perGame.values.any { it == WinterSalePriceStatus.Unavailable }

    private companion object {
        const val WAITLIST_MAX_AGE_MS = 3 * 60 * 60 * 1000L
        const val POPULAR_CANDIDATE_LIMIT = 100
        const val SECTION_CANDIDATE_LIMIT = 30
    }
}
