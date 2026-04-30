package pm.bam.gamedeals.domain.repositories.giveaway

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import java.time.LocalDateTime

class GiveawaysRepositoryImplTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val giveawaysDao: GiveawaysDao = mockk()

    private val gamerPowerSource: GamerPowerSource = mockk()

    private val impl = GiveawaysRepositoryImpl(logger, giveawaysDao, gamerPowerSource)

    @Test
    fun `observe giveaways with descending publishedDate order`() = runTest {
        val resultOne = mockk<Giveaway> {
            every { publishedDate } returns LocalDateTime.MIN
        }
        val resultTwo = mockk<Giveaway> {
            every { publishedDate } returns LocalDateTime.MAX
        }

        every { giveawaysDao.observeAllGiveaways() } returns flowOf(listOf(resultOne, resultTwo))

        val result = impl.observeGiveaways().first()
        assertTrue(result.isNotEmpty())

        assertEquals(result[0], resultTwo)
        assertEquals(result[1], resultOne)

        verify(exactly = 1) { giveawaysDao.observeAllGiveaways() }
    }

    @Test
    fun `refresh giveaways`() = runTest {
        val giveaway = mockk<Giveaway>()

        coEvery { gamerPowerSource.fetchGiveaways() } returns listOf(giveaway)
        coEvery { giveawaysDao.addGiveaways(any()) } just Runs

        impl.refreshGiveaways()

        coVerify(exactly = 1) { gamerPowerSource.fetchGiveaways() }
        coVerify(exactly = 1) { giveawaysDao.addGiveaways(any()) }
    }

    @Test
    fun `observe giveaways with search parameters`() = runTest {
        val resultOne = mockk<Giveaway> {
            every { type } returns GiveawayType.GAME
            every { platforms } returns listOf(GiveawayPlatform.PC)
            every { publishedDate } returns LocalDateTime.MIN
            every { users } returns 1
            every { worth } returns 1.0
        }
        val resultTwo = mockk<Giveaway> {
            every { type } returns GiveawayType.DLC
            every { platforms } returns listOf(GiveawayPlatform.PS5)
            every { publishedDate } returns LocalDateTime.MAX
            every { users } returns 2
            every { worth } returns 2.0
        }
        val resultThree = mockk<Giveaway> {
            every { type } returns GiveawayType.BETA
            every { platforms } returns listOf(GiveawayPlatform.NINTENDO_SWITCH)
            every { publishedDate } returns LocalDateTime.MAX
            every { users } returns 3
            every { worth } returns 3.0
        }

        every { giveawaysDao.observeAllGiveaways() } returns flowOf(listOf(resultOne, resultTwo, resultThree))

        val para = GiveawaySearchParameters(
            types = listOf(GiveawayType.GAME to true, GiveawayType.BETA to true),
            platforms = listOf(GiveawayPlatform.PC to true, GiveawayPlatform.NINTENDO_SWITCH to true),
            sortBy = GiveawaySortBy.DATE
        )

        val resultDescendingDate = impl.observeGiveaways(para).first()
        assertTrue(resultDescendingDate.isNotEmpty())
        assertEquals(2, resultDescendingDate.size)

        assertEquals(resultDescendingDate[0], resultThree)
        assertEquals(resultDescendingDate[1], resultOne)


        val resultDescendingPopularity = impl.observeGiveaways(para.copy(sortBy = GiveawaySortBy.POPULARITY)).first()
        assertTrue(resultDescendingPopularity.isNotEmpty())
        assertEquals(2, resultDescendingPopularity.size)

        assertEquals(resultDescendingPopularity[0], resultThree)
        assertEquals(resultDescendingPopularity[1], resultOne)


        val resultDescendingWorth = impl.observeGiveaways(para.copy(sortBy = GiveawaySortBy.VALUE)).first()
        assertTrue(resultDescendingWorth.isNotEmpty())
        assertEquals(2, resultDescendingWorth.size)

        assertEquals(resultDescendingWorth[0], resultThree)
        assertEquals(resultDescendingWorth[1], resultOne)

        verify(exactly = 3) { giveawaysDao.observeAllGiveaways() }
    }
}
