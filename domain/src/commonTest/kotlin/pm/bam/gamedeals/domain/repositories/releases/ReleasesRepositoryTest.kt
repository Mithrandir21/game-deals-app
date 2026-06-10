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
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertTrue

class ReleasesRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val releasesDao: ReleasesDao = mock(MockMode.autoUnit)
    private val igdbSource: IgdbSource = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = ReleasesRepositoryImpl(logger, releasesDao, igdbSource, clock)

    @Test
    fun observeReleases_empty_cache_triggers_refresh_and_stamps_expires() = runTest {
        val release = Release(title = "Test Release", date = 0, image = "thumb.png")

        every { releasesDao.observeAllReleases() } returns flowOf(listOf(release))
        everySuspend { releasesDao.getAllReleases() } returns emptyList()
        everySuspend { igdbSource.fetchNewReleases() } returns listOf(release)

        val result = impl.observeReleases().first()
        assertTrue(result.isNotEmpty())

        verify(exactly(1)) { releasesDao.observeAllReleases() }
        verifySuspend(exactly(1)) { igdbSource.fetchNewReleases() }
        verifySuspend(exactly(1)) { releasesDao.replaceAll(listOf(release.copy(expires = now + RELEASES_TTL_MILLIS))) }
    }

    @Test
    fun refreshReleases_unforced_expired_swaps_table_contents_and_stamps_expires() = runTest {
        val expired = Release(title = "Old Release", date = 0, image = "old.png", expires = now - 1)
        val fetched = Release(title = "Test Release", date = 0, image = "thumb.png")

        everySuspend { releasesDao.getAllReleases() } returns listOf(expired)
        everySuspend { igdbSource.fetchNewReleases() } returns listOf(fetched)

        impl.refreshReleases()

        verifySuspend(exactly(1)) { igdbSource.fetchNewReleases() }
        verifySuspend(exactly(1)) { releasesDao.replaceAll(listOf(fetched.copy(expires = now + RELEASES_TTL_MILLIS))) }
    }

    @Test
    fun refreshReleases_unforced_all_fresh_skips_fetch() = runTest {
        val fresh = Release(title = "Fresh Release", date = 0, image = "fresh.png", expires = now + 10_000)

        everySuspend { releasesDao.getAllReleases() } returns listOf(fresh)

        impl.refreshReleases()

        verifySuspend(exactly(0)) { igdbSource.fetchNewReleases() }
        verifySuspend(exactly(1)) { releasesDao.getAllReleases() }
    }
}
