package pm.bam.gamedeals.domain.repositories.releases

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.db.entities.ReleaseEntity
import pm.bam.gamedeals.domain.db.entities.toEntity
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

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `observe stores with refresh called`() = runTest {
        val release: Release = mockk()
        val entity: ReleaseEntity = mockk()
        mockkStatic("pm.bam.gamedeals.domain.db.entities.MappersKt")
        every { release.toEntity() } returns entity

        coEvery { releasesDao.observeAllReleases() } returns flowOf(listOf(release))
        coEvery { cheapsharkSource.fetchReleases() } returns listOf(release)
        coEvery { releasesDao.addReleaseEntities(any()) } just Runs


        val result = impl.observeReleases().first()
        Assert.assertTrue(result.isNotEmpty())

        coVerify(exactly = 1) { releasesDao.observeAllReleases() }
        coVerify(exactly = 1) { cheapsharkSource.fetchReleases() }
        coVerify(exactly = 1) { releasesDao.addReleaseEntities(entity) }
    }


    @Test
    fun `refresh deals`() = runTest {
        val release: Release = mockk()
        val entity: ReleaseEntity = mockk()
        mockkStatic("pm.bam.gamedeals.domain.db.entities.MappersKt")
        every { release.toEntity() } returns entity

        coEvery { cheapsharkSource.fetchReleases() } returns listOf(release)
        coEvery { releasesDao.addReleaseEntities(any()) } just Runs


        impl.refreshReleases()

        coVerify(exactly = 0) { releasesDao.observeAllReleases() }
        coVerify(exactly = 1) { cheapsharkSource.fetchReleases() }
        coVerify(exactly = 1) { releasesDao.addReleaseEntities(entity) }
    }
}
