package pm.bam.gamedeals.domain.repositories.releases

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener

class ReleasesRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val releasesDao: ReleasesDao = mockk()

    private val cheapsharkSource: CheapsharkSource = mockk()

    private val impl = ReleasesRepository(logger, releasesDao, cheapsharkSource)

    @Test
    fun `observe stores with refresh called`() = runTest {
        val release: Release = mockk()

        coEvery { releasesDao.observeAllReleases() } returns flowOf(listOf(release))
        coEvery { cheapsharkSource.fetchReleases() } returns listOf(release)
        coEvery { releasesDao.addReleases(any()) } just Runs


        val result = impl.observeReleases().first()
        Assert.assertTrue(result.isNotEmpty())

        coVerify(exactly = 1) { releasesDao.observeAllReleases() }
        coVerify(exactly = 1) { cheapsharkSource.fetchReleases() }
        coVerify(exactly = 1) { releasesDao.addReleases(release) }
    }


    @Test
    fun `refresh deals`() = runTest {
        val release: Release = mockk()

        coEvery { cheapsharkSource.fetchReleases() } returns listOf(release)
        coEvery { releasesDao.addReleases(any()) } just Runs


        impl.refreshReleases()

        coVerify(exactly = 0) { releasesDao.observeAllReleases() }
        coVerify(exactly = 1) { cheapsharkSource.fetchReleases() }
        coVerify(exactly = 1) { releasesDao.addReleases(release) }
    }
}
