package pm.bam.gamedeals.domain.repositories.releases

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertTrue

class ReleasesRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val releasesDao: ReleasesDao = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)
    private val impl = ReleasesRepositoryImpl(logger, releasesDao, dealsSource)

    @Test
    fun observeReleases_triggers_refresh_that_replaces_stale_rows() = runTest {
        val release = Release(title = "Test Release", date = 0, image = "thumb.png")

        every { releasesDao.observeAllReleases() } returns flowOf(listOf(release))
        everySuspend { dealsSource.fetchReleases() } returns listOf(release)

        val result = impl.observeReleases().first()
        assertTrue(result.isNotEmpty())

        verify(exactly(1)) { releasesDao.observeAllReleases() }
        verifySuspend(exactly(1)) { dealsSource.fetchReleases() }
        verifySuspend(exactly(1)) { releasesDao.replaceAll(listOf(release)) }
    }

    @Test
    fun refreshReleases_swaps_table_contents_via_dao_replaceAll() = runTest {
        val release = Release(title = "Test Release", date = 0, image = "thumb.png")

        everySuspend { dealsSource.fetchReleases() } returns listOf(release)

        impl.refreshReleases()

        verify(exactly(0)) { releasesDao.observeAllReleases() }
        verifySuspend(exactly(1)) { dealsSource.fetchReleases() }
        verifySuspend(exactly(1)) { releasesDao.replaceAll(listOf(release)) }
    }
}
