package pm.bam.gamedeals.domain.repositories.dealswipe

import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSortField
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.signals.GameSignalsRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.warn

/** One page of the swipe deck. [nextOffset] is where the next page starts; null once the deck has run out. */
data class DealSwipePage(val deals: List<Deal>, val nextOffset: Int?)

/**
 * The deal-swipe deck (next-versions M8): ITAD's hottest deals, the order the Deals tab opens with, minus every game
 * the user already waitlisted, owns or put on the ITAD ignore list, and minus games they skipped here in the last
 * [SWIPE_SKIP_MS]. A skip only hides the game from this deck, on this phone; ITAD never hears about it.
 */
interface DealSwipeRepository {
    /** The deals of the hottest-first page starting at [offset] that the user hasn't seen to. May be empty. */
    suspend fun nextPage(offset: Int): DealSwipePage

    /** Hides [gameId] from the deck for [SWIPE_SKIP_MS]. */
    suspend fun skip(gameId: String)

    /** Takes back a [skip] (the user undid it). */
    suspend fun unskip(gameId: String)

    /**
     * A short description of [deal]'s game: IGDB's summary, found by the game's Steam app id (from the Worker's
     * signals) or else by an exact title match. Null when there is none or the lookup failed. Remembered per game.
     */
    suspend fun description(deal: Deal): String?
}

internal const val SWIPE_PAGE_SIZE = 50

/** How deep into the hottest list the deck goes; past this the deals are no longer hot. */
internal const val SWIPE_MAX_OFFSET = 1000

/** How long a skipped game stays out of the deck. */
internal const val SWIPE_SKIP_MS = 30 * millisInDay

/** The most skips kept; the oldest go first. */
internal const val SWIPE_SKIP_CAP = 2000

internal const val DEAL_SWIPE_SKIPS_KEY = "deal_swipe_skips"

/** The most descriptions kept in memory; the oldest go first. */
internal const val SWIPE_DESCRIPTION_CAP = 200

private val skipsSerializer = MapSerializer(String.serializer(), Long.serializer())

internal class DealSwipeRepositoryImpl(
    private val dealsRepository: DealsRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val settingsRepository: SettingsRepository,
    private val storage: Storage,
    private val clock: Clock,
    private val gameSignalsRepository: GameSignalsRepository,
    private val igdbRepository: IgdbRepository,
    private val logger: Logger,
) : DealSwipeRepository {

    // Quick swipes write the skips one after another; the lock keeps one write from losing another's game.
    private val skipsLock = Mutex()

    override suspend fun nextPage(offset: Int): DealSwipePage {
        if (offset >= SWIPE_MAX_OFFSET) return DealSwipePage(emptyList(), null)
        val page = dealsRepository.getDeals(
            DealsQuery(
                sortField = DealsSortField.Hottest,
                sortDirection = DealsSortField.Hottest.defaultDirection,
                mature = settingsRepository.getMatureOptIn(),
                offset = offset,
                limit = SWIPE_PAGE_SIZE,
            )
        )
        val hidden = waitlistRepository.observeWaitlistIds().first() +
            collectionRepository.observeCollectionIds().first() +
            ignoredRepository.observeIgnoredIds().first() +
            activeSkips().keys
        val deals = page
            .filter { it.gameID.isNotBlank() && it.gameID !in hidden }
            .distinctBy { it.gameID }
        val next = (offset + SWIPE_PAGE_SIZE).takeIf { page.size >= SWIPE_PAGE_SIZE && it < SWIPE_MAX_OFFSET }
        return DealSwipePage(deals, next)
    }

    override suspend fun skip(gameId: String) = skipsLock.withLock {
        val now = clock.nowMillis()
        val kept = activeSkips(now) + (gameId to now)
        val capped = if (kept.size <= SWIPE_SKIP_CAP) kept
        else kept.entries.sortedByDescending { it.value }.take(SWIPE_SKIP_CAP).associate { it.key to it.value }
        storage.save(DEAL_SWIPE_SKIPS_KEY, capped, skipsSerializer)
        Unit
    }

    override suspend fun unskip(gameId: String) = skipsLock.withLock {
        val skips = activeSkips()
        if (gameId in skips) storage.save(DEAL_SWIPE_SKIPS_KEY, skips - gameId, skipsSerializer)
        Unit
    }

    // Game id → its description, or null when the game has none. Insertion order, so the oldest is dropped first.
    private val descriptions = LinkedHashMap<String, String?>()
    private val descriptionsLock = Mutex()

    override suspend fun description(deal: Deal): String? {
        descriptionsLock.withLock { if (deal.gameID in descriptions) return descriptions[deal.gameID] }
        val found = try {
            val steamAppId = gameSignalsRepository.getSignals(listOf(deal.gameID))[deal.gameID]?.steamAppId
            val game = steamAppId?.let { igdbRepository.fetchGameBySteamId(it) }
                ?: igdbRepository.fetchGameDetailsByTitle(deal.title)?.takeIf { sameTitle(it.name, deal.title) }
            game?.summary?.trim()?.takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // Not remembered, so the card can try again next time it comes up.
            warn(logger, failure) { "Couldn't look up a swipe card's description" }
            return null
        }
        descriptionsLock.withLock {
            descriptions[deal.gameID] = found
            while (descriptions.size > SWIPE_DESCRIPTION_CAP) descriptions.remove(descriptions.keys.first())
        }
        return found
    }

    /** Skips younger than [SWIPE_SKIP_MS]; an unreadable store counts as none. */
    private suspend fun activeSkips(now: Long = clock.nowMillis()): Map<String, Long> =
        (runCatching { storage.getNullable(DEAL_SWIPE_SKIPS_KEY, skipsSerializer) }.getOrNull() ?: emptyMap())
            .filterValues { now - it < SWIPE_SKIP_MS }
}

/** The same title, ignoring case, punctuation and trademark signs; a looser match could show another game's text. */
internal fun sameTitle(a: String, b: String): Boolean = normalizedTitle(a) == normalizedTitle(b)

private fun normalizedTitle(title: String): String =
    title.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
