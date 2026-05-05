package pm.bam.gamedeals.domain.repositories.giveaway

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val MIN_DATETIME = LocalDateTime(1970, 1, 1, 0, 0)
private val MAX_DATETIME = LocalDateTime(9999, 12, 31, 23, 59, 59)

/**
 * Lifted to commonTest in phase-A4b. Replaces `mockk<Giveaway> { every { type } returns ... }`
 * (mock-each-property pattern) with constructed [Giveaway] values via a `giveaway(...)` helper
 * with sensible defaults — Mokkery cannot mock final classes, and constructing the data class
 * reads better than stubbing 5 properties separately anyway.
 */
class GiveawaysRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val giveawaysDao: GiveawaysDao = mock(MockMode.autoUnit)
    private val gamerPowerSource: GamerPowerSource = mock(MockMode.autoUnit)
    private val impl = GiveawaysRepository(logger, giveawaysDao, gamerPowerSource)

    @Test
    fun observe_giveaways_with_descending_publishedDate_order() = runTest {
        val resultOne = giveaway(id = 1, publishedDate = MIN_DATETIME)
        val resultTwo = giveaway(id = 2, publishedDate = MAX_DATETIME)

        every { giveawaysDao.observeAllGiveaways() } returns flowOf(listOf(resultOne, resultTwo))

        val result = impl.observeGiveaways().first()
        assertTrue(result.isNotEmpty())
        assertEquals(result[0], resultTwo)
        assertEquals(result[1], resultOne)

        verify(exactly(1)) { giveawaysDao.observeAllGiveaways() }
    }

    @Test
    fun refresh_giveaways() = runTest {
        val giveaway = giveaway()

        everySuspend { gamerPowerSource.fetchGiveaways() } returns listOf(giveaway)

        impl.refreshGiveaways()

        verifySuspend(exactly(1)) { gamerPowerSource.fetchGiveaways() }
        verifySuspend(exactly(1)) { giveawaysDao.addGiveaways(giveaway) }
    }

    @Test
    fun observe_giveaways_with_search_parameters() = runTest {
        val resultOne = giveaway(
            id = 1,
            type = GiveawayType.GAME,
            platforms = listOf(GiveawayPlatform.PC),
            publishedDate = MIN_DATETIME,
            users = 1,
            worth = 1.0,
        )
        val resultTwo = giveaway(
            id = 2,
            type = GiveawayType.DLC,
            platforms = listOf(GiveawayPlatform.PS5),
            publishedDate = MAX_DATETIME,
            users = 2,
            worth = 2.0,
        )
        val resultThree = giveaway(
            id = 3,
            type = GiveawayType.BETA,
            platforms = listOf(GiveawayPlatform.NINTENDO_SWITCH),
            publishedDate = MAX_DATETIME,
            users = 3,
            worth = 3.0,
        )

        every { giveawaysDao.observeAllGiveaways() } returns flowOf(listOf(resultOne, resultTwo, resultThree))

        val para = GiveawaySearchParameters(
            types = persistentListOf(GiveawayType.GAME to true, GiveawayType.BETA to true),
            platforms = persistentListOf(GiveawayPlatform.PC to true, GiveawayPlatform.NINTENDO_SWITCH to true),
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

        verify(exactly(3)) { giveawaysDao.observeAllGiveaways() }
    }
}

private fun giveaway(
    id: Int = 1,
    title: String = "Test Giveaway",
    worthDenominated: String? = "$0",
    worth: Double? = 0.0,
    thumbnail: String = "thumb.png",
    image: String = "image.png",
    description: String = "desc",
    instructions: String = "instructions",
    openGiveawayUrl: String = "https://example.com/open",
    publishedDate: LocalDateTime = MIN_DATETIME,
    type: GiveawayType = GiveawayType.GAME,
    platforms: List<GiveawayPlatform> = listOf(GiveawayPlatform.PC),
    endDate: String? = null,
    users: Int = 0,
    status: String = "Active",
    gamerpowerUrl: String = "https://example.com",
    openGiveaway: String = "https://example.com/giveaway",
) = Giveaway(
    id, title, worthDenominated, worth, thumbnail, image, description, instructions,
    openGiveawayUrl, publishedDate, type, platforms, endDate, users, status, gamerpowerUrl, openGiveaway,
)
