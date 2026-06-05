package pm.bam.gamedeals.domain.repositories.stats

import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.source.StatsSource
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsRepositoryTest {

    @Test
    fun delegates_each_ranking_to_the_source_and_forwards_the_limit() = runTest {
        val source = FakeStatsSource(
            waitlisted = listOf(RankedGame("a", "A")),
            collected = listOf(RankedGame("b", "B")),
            popular = listOf(RankedGame("c", "C")),
        )
        val repo = StatsRepositoryImpl(source)

        assertEquals(listOf(RankedGame("a", "A")), repo.getMostWaitlisted(limit = 10))
        assertEquals(listOf(RankedGame("b", "B")), repo.getMostCollected(limit = 20))
        assertEquals(listOf(RankedGame("c", "C")), repo.getMostPopular())

        assertEquals(listOf(10, 20, null), source.limits)
    }
}

internal class FakeStatsSource(
    private val waitlisted: List<RankedGame> = emptyList(),
    private val collected: List<RankedGame> = emptyList(),
    private val popular: List<RankedGame> = emptyList(),
) : StatsSource {
    val limits = mutableListOf<Int?>()
    override suspend fun fetchMostWaitlisted(limit: Int?): List<RankedGame> { limits += limit; return waitlisted }
    override suspend fun fetchMostCollected(limit: Int?): List<RankedGame> { limits += limit; return collected }
    override suspend fun fetchMostPopular(limit: Int?): List<RankedGame> { limits += limit; return popular }
}
