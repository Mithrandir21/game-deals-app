package pm.bam.gamedeals.domain.repositories.stats

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.StatsRankingsCacheEntry
import pm.bam.gamedeals.domain.db.dao.StatsRankingsCacheDao
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.StatsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsRepositoryTest {

    private val statsSource: StatsSource = mock(MockMode.autoUnit)
    private val statsRankingsCacheDao: StatsRankingsCacheDao = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val country = "US"
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        everySuspend { getSelectedCountryCode() } returns country
    }

    private val repo = StatsRepositoryImpl(statsSource, clock, regionRepository, statsRankingsCacheDao, json)

    @Test
    fun cold_cache_fetches_each_ranking_forwarding_the_limit_and_caches() = runTest {
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_WAITLISTED, country) } returns null
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_COLLECTED, country) } returns null
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_POPULAR, country) } returns null
        everySuspend { statsSource.fetchMostWaitlisted(10) } returns listOf(RankedGame("a", "A"))
        everySuspend { statsSource.fetchMostCollected(20) } returns listOf(RankedGame("b", "B"))
        everySuspend { statsSource.fetchMostPopular(null) } returns listOf(RankedGame("c", "C"))

        assertEquals(listOf(RankedGame("a", "A")), repo.getMostWaitlisted(limit = 10))
        assertEquals(listOf(RankedGame("b", "B")), repo.getMostCollected(limit = 20))
        assertEquals(listOf(RankedGame("c", "C")), repo.getMostPopular())

        // Each ranking forwarded its limit to the source and cached the result.
        verifySuspend(exactly(1)) { statsSource.fetchMostWaitlisted(10) }
        verifySuspend(exactly(1)) { statsSource.fetchMostCollected(20) }
        verifySuspend(exactly(1)) { statsSource.fetchMostPopular(null) }
        verifySuspend(exactly(3)) { statsRankingsCacheDao.upsert(any()) }
    }

    @Test
    fun fresh_cache_returns_decoded_ranking_without_fetch() = runTest {
        val ranking = listOf(RankedGame("a", "A", boxart = "art", priceDenominated = "$9"))
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_WAITLISTED, country) } returns
            entryFor(RANKING_MOST_WAITLISTED, ranking, expires = now + 10_000)

        assertEquals(ranking, repo.getMostWaitlisted(limit = 10))

        verifySuspend(exactly(0)) { statsSource.fetchMostWaitlisted(any()) }
        verifySuspend(exactly(0)) { statsRankingsCacheDao.upsert(any()) }
    }

    @Test
    fun stale_cache_refresh_failure_serves_stale() = runTest {
        val ranking = listOf(RankedGame("a", "A"))
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_WAITLISTED, country) } returns
            entryFor(RANKING_MOST_WAITLISTED, ranking, expires = now - 1)
        everySuspend { statsSource.fetchMostWaitlisted(10) } throws Exception("stats down")

        // Warm cache + failed refresh: serve the stale ranking (D7), don't throw, don't upsert.
        assertEquals(ranking, repo.getMostWaitlisted(limit = 10))
        verifySuspend(exactly(0)) { statsRankingsCacheDao.upsert(any()) }
    }

    @Test
    fun applies_the_limit_to_the_cached_ranking() = runTest {
        val ranking = listOf(RankedGame("a", "A"), RankedGame("b", "B"), RankedGame("c", "C"))
        everySuspend { statsRankingsCacheDao.get(RANKING_MOST_WAITLISTED, country) } returns
            entryFor(RANKING_MOST_WAITLISTED, ranking, expires = now + 10_000)

        // Fresh cache of 3, asked for 2 → take(2).
        assertEquals(listOf(RankedGame("a", "A"), RankedGame("b", "B")), repo.getMostWaitlisted(limit = 2))
    }

    private fun entryFor(rankingType: String, ranking: List<RankedGame>, expires: Long) = StatsRankingsCacheEntry(
        rankingType = rankingType,
        country = country,
        json = json.encodeToString(ListSerializer(RankedGame.serializer()), ranking),
        fetchedAt = expires - 1,
        expires = expires,
    )
}
