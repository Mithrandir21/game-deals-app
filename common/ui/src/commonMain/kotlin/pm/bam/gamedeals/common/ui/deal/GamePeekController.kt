package pm.bam.gamedeals.common.ui.deal

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.withMinimumDuration
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Loads the game-centric peek sheet ([GamePeekSheetData]). Keyed by `gameId`, it reads the game's
 * current deals via [GamesRepository.getGameDetails] (the same source as the unified Game Page), builds
 * the best deal + the other stores, and emits the sheet state.
 *
 * Shared by Home and Deals. [igdbRepository] is optional and only needed by callers that peek a
 * **title** (Home's "New releases", which carry no game id) via [loadByTitle]; deal/ranked rows already
 * carry a `gameId` and use [load].
 */
class GamePeekController(
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository,
    private val logger: Logger,
    private val igdbRepository: IgdbRepository? = null,
) {
    val data: StateFlow<GamePeekSheetData?>
        field = MutableStateFlow<GamePeekSheetData?>(null)

    private var loadJob: Job? = null

    /** Peek a game by its ITAD id (deal / ranked rows). */
    fun load(scope: CoroutineScope, gameId: String, gameName: String, thumb: String?) {
        loadJob?.cancel()
        loadJob = scope.launch {
            data.emit(GamePeekSheetData.Loading(gameId, gameName, thumb))
            emitGameDetails(gameId, gameName, thumb)
        }
    }

    /**
     * Peek a game by title (Home's "New releases", which are IGDB-sourced and carry no ITAD id).
     * Resolves the title → ITAD id via the Steam-appid bridge; an unresolved title (e.g. a not-yet-sold
     * game) emits the "upcoming" state so the sheet still shows the title + "View game page".
     */
    fun loadByTitle(scope: CoroutineScope, title: String, thumb: String?) {
        loadJob?.cancel()
        loadJob = scope.launch {
            data.emit(GamePeekSheetData.Loading(gameId = "", gameName = title, thumb = thumb))
            try {
                val gameId = resolveGameId(title)
                if (gameId != null) emitGameDetails(gameId, title, thumb)
                else data.emit(upcoming(gameId = "", gameName = title, thumb = thumb))
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                fatal(logger, t) { "Game peek by title failed" }
                data.emit(GamePeekSheetData.Error(gameId = "", gameName = title, thumb = thumb))
            }
        }
    }

    private suspend fun emitGameDetails(gameId: String, gameName: String, thumb: String?) {
        try {
            val result = withMinimumDuration(MIN_LOADING_MILLIS) {
                val details = gamesRepository.getGameDetails(gameId)
                val pairs = details.deals
                    .sortedBy { it.priceValue }
                    .map { StoreDealPair(store = storesRepository.getStore(it.storeID), deal = it) }
                val best = pairs.firstOrNull()
                GamePeekSheetData.Data(
                    gameId = gameId,
                    gameName = details.info.title.ifBlank { gameName },
                    thumb = details.info.artwork.thumbnail?.takeIf { it.isNotBlank() } ?: thumb,
                    bestDeal = best,
                    otherStores = pairs.drop(1).take(MAX_OTHER_STORES).toImmutableList(),
                    cheapestPriceEver = details.cheapestPriceEver.takeIf { best != null },
                    upcoming = best == null,
                )
            }
            data.emit(result)
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            fatal(logger, t) { "Game peek load failed for $gameId" }
            data.emit(GamePeekSheetData.Error(gameId, gameName, thumb))
        }
    }

    private suspend fun resolveGameId(title: String): String? {
        val igdb = igdbRepository?.let { runCatchingNonCancellation { it.fetchGameDetailsByTitle(title) } }
        val steamAppId = igdb?.steamAppId ?: return null
        return runCatchingNonCancellation { gamesRepository.findGameIdBySteamAppId(steamAppId, igdb.name) }
    }

    private fun upcoming(gameId: String, gameName: String, thumb: String?) = GamePeekSheetData.Data(
        gameId = gameId,
        gameName = gameName,
        thumb = thumb,
        bestDeal = null,
        otherStores = persistentListOf(),
        cheapestPriceEver = null,
        upcoming = true,
    )

    private suspend fun <T> runCatchingNonCancellation(block: suspend () -> T?): T? = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }

    fun dismiss(scope: CoroutineScope) {
        loadJob?.cancel()
        scope.launch { data.emit(null) }
    }

    companion object {
        /** Cap the "other stores" list so a game on many shops doesn't produce an endless sheet. */
        const val MAX_OTHER_STORES = 5

        /** Keep the loading spinner up briefly so a fast cache hit doesn't flash. */
        private const val MIN_LOADING_MILLIS = 600L
    }
}
