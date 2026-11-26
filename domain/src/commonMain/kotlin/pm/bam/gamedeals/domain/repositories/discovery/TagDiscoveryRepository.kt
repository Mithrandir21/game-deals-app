package pm.bam.gamedeals.domain.repositories.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.IgdbTagEntry
import pm.bam.gamedeals.domain.db.dao.IgdbTagDao
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/** One discovery result page (mirrors the deals feed page size). */
const val DISCOVERY_PAGE_SIZE = 30

/**
 * Safety cap on IGDB pages scanned per [TagDiscoveryRepository.discover] call. Since untracked /
 * Steam-only games are filtered out, a page of IGDB games can yield fewer tracked results; we fetch
 * further IGDB pages to refill — but bound it so a console-heavy tag query can't scan endlessly.
 */
private const val MAX_IGDB_PAGES_PER_DISCOVER = 5

/** Tag vocabulary is near-static, so a long TTL is plenty — a monthly self-heal picks up IGDB changes. */
internal const val IGDB_TAG_VOCABULARY_TTL_MILLIS = millisInDay * 30

/**
 * One page of discovery results plus the IGDB cursor to resume from. Because untracked / Steam-only
 * games are filtered out, the number of [results] no longer matches the number of IGDB games scanned,
 * so the next page must resume from [nextOffset] (an IGDB-space offset), not from `results.size`.
 */
data class DiscoveryPage(
    val results: List<TagDiscoveryResult>,
    val nextOffset: Int,
    val endReached: Boolean,
)

interface TagDiscoveryRepository {

    /**
     * The curated tag-picker vocabulary (epic #307): genres + themes + game-modes + player
     * perspectives + the curated keyword allow-list, as one flat dimension-tagged list.
     */
    suspend fun getTagVocabulary(): List<IgdbTag>

    /**
     * One page of **ITAD-tracked** discovery results for an AND-combined tag [filter], starting at
     * IGDB [offset]. Untracked games (no Steam app id) are dropped before any ITAD lookup; Steam games
     * ITAD doesn't track are dropped after the (unavoidable) lookup. Further IGDB pages are fetched to
     * refill up to [pageSize] tracked results (bounded). Returns an empty page for an empty filter.
     */
    suspend fun discover(filter: IgdbTagFilter, offset: Int, pageSize: Int = DISCOVERY_PAGE_SIZE): DiscoveryPage
}

internal class TagDiscoveryRepositoryImpl(
    private val logger: Logger,
    private val igdbRepository: IgdbRepository,
    private val gamesRepository: GamesRepository,
    private val igdbTagDao: IgdbTagDao,
    private val clock: Clock,
) : TagDiscoveryRepository {

    /**
     * Reads the cached vocabulary; on a cold/expired cache it refetches from IGDB and replaces the
     * table. If the refetch fails but a (stale) cache exists, the stale vocabulary is served rather
     * than failing the picker — the vocabulary is near-static, so stale is fine.
     */
    override suspend fun getTagVocabulary(): List<IgdbTag> {
        val now = clock.nowMillis()
        val cached = igdbTagDao.getAll()
        if (cached.isNotEmpty() && cached.all { it.expires > now }) {
            return cached.mapNotNull { it.toIgdbTagOrNull() }
        }
        return runCatching {
            val fetched = igdbRepository.fetchTagVocabulary() + igdbRepository.fetchCuratedKeywords(CURATED_KEYWORD_SLUGS)
            if (fetched.isNotEmpty()) {
                val expires = now + IGDB_TAG_VOCABULARY_TTL_MILLIS
                igdbTagDao.clear()
                igdbTagDao.upsertAll(fetched.map { it.toEntry(expires) })
            }
            fetched
        }.getOrElse { error ->
            if (cached.isNotEmpty()) cached.mapNotNull { it.toIgdbTagOrNull() } else throw error
        }
    }

    override suspend fun discover(filter: IgdbTagFilter, offset: Int, pageSize: Int): DiscoveryPage {
        if (filter.isEmpty()) return DiscoveryPage(results = emptyList(), nextOffset = offset, endReached = true)

        val collected = mutableListOf<TagDiscoveryResult>()
        var igdbOffset = offset
        var endReached = false
        var igdbFetches = 0

        // Filtering can thin a page, so keep pulling IGDB pages until we've collected pageSize tracked
        // results (or IGDB is exhausted, or we hit the scan cap). nextOffset is the IGDB cursor to resume.
        while (collected.size < pageSize && !endReached && igdbFetches < MAX_IGDB_PAGES_PER_DISCOVER) {
            val games = igdbRepository.fetchGamesByTags(filter, limit = pageSize, offset = igdbOffset)
            igdbFetches++
            debug(logger) { "Tag discovery: ${games.size} IGDB games at offset $igdbOffset" }
            if (games.isEmpty()) {
                endReached = true
                break
            }
            igdbOffset += games.size
            if (games.size < pageSize) endReached = true
            collected += trackedResults(games)
        }

        return DiscoveryPage(results = collected, nextOffset = igdbOffset, endReached = endReached)
    }

    /**
     * Resolves a batch of IGDB games to ITAD-tracked results, dropping the two unwanted classes as
     * early as possible:
     *  - **Untracked** (no Steam app id): dropped *before* any ITAD lookup — they can never resolve.
     *  - **Steam-only** (has a Steam app id but ITAD doesn't track it): the lookup is the only signal,
     *    so it's unavoidable, but these are dropped right after it and never reach the price batch.
     */
    private suspend fun trackedResults(games: List<IgdbGame>): List<TagDiscoveryResult> = coroutineScope {
        // Drop untracked games up front — no Steam app id means no possible ITAD lookup.
        val withSteamId = games.filter { it.steamAppId != null }
        if (withSteamId.isEmpty()) return@coroutineScope emptyList()

        // Resolve ITAD ids concurrently (ITAD client's limiter + 429 retry throttle; 30-day cached),
        // keeping only the games that resolve — that drops the Steam-only games before pricing.
        val tracked: List<Pair<IgdbGame, String>> = withSteamId
            .map { game -> async { game to gamesRepository.findGameIdBySteamAppId(game.steamAppId!!, game.name) } }
            .awaitAll()
            .mapNotNull { (game, itadId) -> itadId?.let { game to it } }
        if (tracked.isEmpty()) return@coroutineScope emptyList()

        // One batched price query for the tracked ids only.
        val priceByItadId: Map<String, BundleGamePrice> =
            gamesRepository.getGamePrices(tracked.map { it.second }.distinct()).associateBy { it.gameId }

        tracked.map { (game, itadId) ->
            TagDiscoveryResult(
                igdbId = game.id,
                gameId = itadId,
                title = game.name,
                coverImageUrl = game.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
                price = priceByItadId[itadId],
            )
        }
    }

    private fun IgdbTag.toEntry(expires: Long): IgdbTagEntry =
        IgdbTagEntry(dimension = dimension.name, igdbId = igdbId, name = name, slug = slug, expires = expires)

    // Drops a row whose persisted dimension no longer maps to a known enum value (defensive — survives
    // an enum rename without crashing the picker; the row is simply re-fetched on the next refresh).
    private fun IgdbTagEntry.toIgdbTagOrNull(): IgdbTag? {
        val dim = IgdbTagDimension.entries.firstOrNull { it.name == dimension } ?: return null
        return IgdbTag(dimension = dim, igdbId = igdbId, name = name, slug = slug)
    }
}
