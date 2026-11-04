package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.GameIdMappingDao
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

data class WinterSaleSeriesGame(val igdbId: Long, val title: String, val steamAppId: Int?, val erotic: Boolean?, val coverImageId: String? = null)

interface WinterSaleSeriesSource {
    suspend fun fetch(franchiseId: Long, limit: Int): List<WinterSaleSeriesGame>
}

@Serializable
data class WinterSaleSeriesMappedGame(val itadId: String, val title: String, val erotic: Boolean?, val coverImageId: String? = null)

@Serializable
data class WinterSaleSeriesMapEntry(
    val franchiseId: Long,
    val followedAtEpochMs: Long,
    val builtAtEpochMs: Long,
    val complete: Boolean,
    val games: List<WinterSaleSeriesMappedGame>,
)

interface WinterSaleSeriesMapStore {
    suspend fun get(): List<WinterSaleSeriesMapEntry>
    suspend fun replace(entries: List<WinterSaleSeriesMapEntry>)
}

internal class WinterSaleSeriesMapStoreImpl(private val storage: Storage, private val logger: Logger) : WinterSaleSeriesMapStore {
    override suspend fun get(): List<WinterSaleSeriesMapEntry> = try {
        storage.getNullable<List<WinterSaleSeriesMapEntry>>(KEY).orEmpty()
    } catch (error: CancellationException) {
        throw error
    } catch (t: Throwable) {
        error(logger, t)
        emptyList()
    }

    override suspend fun replace(entries: List<WinterSaleSeriesMapEntry>) {
        storage.save(KEY, entries)
    }

    private companion object { const val KEY = "winter_sale_series_id_map" }
}

data class WinterSaleSeriesCandidate(val game: WinterSaleCandidate, val seriesNames: List<String>)

data class WinterSaleSeriesRawCandidate(val game: WinterSaleRawCandidate, val seriesNames: List<String>)

data class WinterSaleSeriesRawSection(
    val games: List<WinterSaleSeriesRawCandidate>,
    val checking: Boolean,
    val unavailable: Boolean,
    val moreSeriesFollowed: Boolean,
)

data class WinterSaleSeriesSection(
    val games: List<WinterSaleSeriesCandidate>,
    val checking: Boolean,
    val unavailable: Boolean,
    val incompletePrices: Boolean,
    val moreSeriesFollowed: Boolean,
)

interface WinterSaleSeriesRepository {
    suspend fun candidates(includeAdult: Boolean): WinterSaleSeriesRawSection
    suspend fun load(country: String, includeAdult: Boolean): WinterSaleSeriesSection
}

internal class WinterSaleSeriesRepositoryImpl(
    private val followedRepository: FollowedFranchiseRepository,
    private val seriesSource: WinterSaleSeriesSource,
    private val gamesRepository: GamesRepository,
    private val mappingDao: GameIdMappingDao,
    private val mapStore: WinterSaleSeriesMapStore,
    private val offersRepository: WinterSaleOffersRepository,
    private val clock: Clock,
    private val logger: Logger,
) : WinterSaleSeriesRepository {
    override suspend fun candidates(includeAdult: Boolean): WinterSaleSeriesRawSection {
        val allFollowed = followedRepository.getFollowed().sortedWith(compareByDescending<FollowedFranchise> { it.addedAtMs }.thenBy { it.franchiseId })
        val selected = allFollowed.take(MAX_SERIES)
        val now = clock.nowMillis()
        val stored = mapStore.get().associateBy { it.franchiseId }
        val maps = linkedMapOf<Long, WinterSaleSeriesMapEntry>()
        var lookupCalls = 0
        var unavailable = false
        var checking = false
        val toBuild = mutableListOf<FollowedFranchise>()
        for (franchise in selected) {
            val cached = stored[franchise.franchiseId]?.takeIf { it.followedAtEpochMs == franchise.addedAtMs }
            val fresh = cached?.takeIf { it.complete && it.builtAtEpochMs <= now && now - it.builtAtEpochMs < MAP_TTL_MS }
            if (fresh != null) {
                maps[franchise.franchiseId] = fresh
                continue
            }
            if (toBuild.size >= MAX_FRANCHISE_CALLS) {
                checking = true
                if (cached != null) maps[franchise.franchiseId] = cached
                continue
            }
            toBuild += franchise
        }
        // The franchise calls are independent, so they run together (IGDB allows 4 a second; at most 3 here).
        // The Steam-id lookups below stay sequential so the lookup budget is spent in follow order.
        val fetched = coroutineScope {
            toBuild.map { franchise ->
                async {
                    try { seriesSource.fetch(franchise.franchiseId, MAX_GAMES_PER_SERIES) }
                    catch (error: CancellationException) { throw error } catch (t: Throwable) {
                        error(logger, t)
                        null
                    }
                }
            }.awaitAll()
        }
        for ((franchise, games) in toBuild.zip(fetched)) {
            val cached = stored[franchise.franchiseId]?.takeIf { it.followedAtEpochMs == franchise.addedAtMs }
            if (games == null) {
                unavailable = true
                checking = true
                if (cached != null) maps[franchise.franchiseId] = cached
                continue
            }
            val resolved = mutableListOf<WinterSaleSeriesMappedGame>()
            var complete = true
            for (game in games.take(MAX_GAMES_PER_SERIES)) {
                val steamId = game.steamAppId ?: continue
                val cachedId = try { mappingDao.get(steamId)?.takeIf { it.expires > now }?.gameId }
                catch (error: CancellationException) { throw error } catch (t: Throwable) {
                    error(logger, t)
                    unavailable = true
                    null
                }
                val id = if (cachedId != null) cachedId else {
                    if (lookupCalls >= MAX_UNCACHED_LOOKUPS) {
                        complete = false
                        continue
                    }
                    lookupCalls++
                    try { gamesRepository.findGameIdBySteamAppId(steamId, game.title) }
                    catch (error: CancellationException) { throw error } catch (t: Throwable) {
                        error(logger, t)
                        unavailable = true
                        complete = false
                        null
                    }
                }
                if (id != null) resolved += WinterSaleSeriesMappedGame(id, game.title, game.erotic, game.coverImageId)
            }
            val mapped = resolved.groupBy { it.itadId }.values.map { matches ->
                matches.first().copy(erotic = matches.map { it.erotic }.combinedAdultState())
            }
            val entry = WinterSaleSeriesMapEntry(franchise.franchiseId, franchise.addedAtMs, now, complete, mapped)
            maps[franchise.franchiseId] = entry
            if (!complete) checking = true
        }
        try { mapStore.replace(maps.values.toList()) } catch (error: CancellationException) { throw error } catch (t: Throwable) {
            error(logger, t)
            unavailable = true
        }
        val byGameId = linkedMapOf<String, Pair<WinterSaleSeriesMappedGame, MutableList<String>>>()
        for (franchise in selected) {
            for (game in maps[franchise.franchiseId]?.games.orEmpty()) {
                val existing = byGameId[game.itadId]
                if (existing == null) {
                    byGameId[game.itadId] = game to mutableListOf(franchise.name)
                } else {
                    existing.second += franchise.name
                    byGameId[game.itadId] = existing.first.copy(erotic = listOf(existing.first.erotic, game.erotic).combinedAdultState()) to existing.second
                }
            }
        }
        val candidates = byGameId.filter { (_, pair) -> includeAdult || pair.first.erotic == false }.map { (id, pair) -> WinterSaleSeriesRawCandidate(
            WinterSaleRawCandidate(id, pair.first.title, seriesArtwork(id, pair.first.coverImageId),
                pair.first.erotic), pair.second,
        ) }
        return WinterSaleSeriesRawSection(candidates, checking, unavailable, allFollowed.size > MAX_SERIES)
    }

    override suspend fun load(country: String, includeAdult: Boolean): WinterSaleSeriesSection {
        val raw = candidates(includeAdult)
        val prices = offersRepository.load(raw.games.map { it.game.gameId }, country)
        val games = raw.games.mapNotNull { item ->
            val offer = (prices.perGame[item.game.gameId] as? WinterSalePriceStatus.Priced)?.offer?.takeIf { it.isDiscounted }
                ?: return@mapNotNull null
            WinterSaleSeriesCandidate(WinterSaleCandidate(item.game.gameId, item.game.title, item.game.artwork, offer, item.game.adult), item.seriesNames)
        }.sortedWith(compareByDescending<WinterSaleSeriesCandidate> { it.game.offer.isAtHistoricalLow }
            .thenByDescending { it.game.offer.cutPercent }.thenBy { it.game.gameId }).take(SECTION_LIMIT)
        return WinterSaleSeriesSection(games, raw.checking, raw.unavailable,
            prices.perGame.values.any { it == WinterSalePriceStatus.Unavailable }, raw.moreSeriesFollowed)
    }

    private companion object {
        /**
         * ITAD serves its wide banner at a path built from the game id, so series games get the same art as the
         * waitlist and popular rows without another request; the IGDB cover stays as the portrait fallback.
         */
        fun seriesArtwork(itadId: String, igdbCoverId: String?) = GameArtwork(
            banner300 = "https://assets.isthereanydeal.com/$itadId/banner300.jpg",
            boxart = igdbCoverId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
        )

        const val MAX_SERIES = 10
        const val MAX_GAMES_PER_SERIES = 30
        const val MAX_FRANCHISE_CALLS = 3
        const val MAX_UNCACHED_LOOKUPS = 60
        const val SECTION_LIMIT = 30
        const val MAP_TTL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}

private fun List<Boolean?>.combinedAdultState(): Boolean? = when {
    any { it == true } -> true
    any { it == null } -> null
    else -> false
}
