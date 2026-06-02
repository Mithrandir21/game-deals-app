package pm.bam.gamedeals.remote.igdb

import com.skydoves.sandwich.getOrThrow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.mappers.toIgdbCandidateList
import pm.bam.gamedeals.remote.igdb.mappers.toIgdbGameOrNull
import pm.bam.gamedeals.remote.igdb.mappers.toReleaseOrNull
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

internal class IgdbSourceImpl(
    private val logger: Logger,
    private val igdbGamesApi: IgdbGamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val clock: Clock,
) : IgdbSource {

    override suspend fun fetchNewReleases(): List<Release> {
        val nowEpochSeconds = clock.nowMillis() / 1000
        return igdbGamesApi.fetchNewReleases(nowEpochSeconds = nowEpochSeconds, limit = NEW_RELEASES_LIMIT)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .mapNotNull { it.toReleaseOrNull() }
    }

    override suspend fun fetchGameBySteamId(steamId: Int): IgdbGame? =
        igdbGamesApi.fetchGameBySteamId(steamId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsBySteamId(steamId: Int): IgdbGame? =
        igdbGamesApi.fetchGameDetailsBySteamId(steamId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsByIgdbId(igdbGameId: Long): IgdbGame? =
        igdbGamesApi.fetchGameDetailsByIgdbId(igdbGameId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchGameDetailsByTitle(title: String): IgdbGame? {
        if (title.isBlank()) return null

        // Cascade: exact-original → exact-normalized (skip if same) → search-normalized.
        // CheapShark deal titles often append edition decorations IGDB doesn't carry, so the
        // normalized attempt catches "X - Digital Deluxe Edition" → "X" without needing a fuzzy
        // search round-trip in the happy path.
        val normalized = normalizeTitleForLookup(title)

        val exactOriginal = fetchExact(title)
        if (exactOriginal != null) return exactOriginal

        if (normalized != title && normalized.isNotBlank()) {
            val exactNormalized = fetchExact(normalized)
            if (exactNormalized != null) return exactNormalized
        }

        val searchTitle = normalized.ifBlank { title }
        return fetchSearch(searchTitle)
    }

    private suspend fun fetchExact(title: String): IgdbGame? =
        igdbGamesApi.fetchGameDetailsByExactName(title)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    private suspend fun fetchSearch(title: String): IgdbGame? =
        igdbGamesApi.fetchGameDetailsBySearch(title)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbGameOrNull()

    override suspend fun fetchSearchCandidatesByTitle(title: String): ImmutableList<IgdbGame.IgdbSimilarGame> {
        if (title.isBlank()) return persistentListOf()
        return igdbGamesApi.fetchSearchCandidatesByTitle(title)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toIgdbCandidateList()
    }

    internal companion object {
        internal const val NEW_RELEASES_LIMIT = 20
        private val TAG: String = IgdbSourceImpl::class.simpleName.orEmpty()
    }
}
