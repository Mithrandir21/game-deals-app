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
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Lifted to commonTest in phase-A4a as the Mokkery proof-of-concept.
 *
 * The previous Android-only version used MockK + `InstantTaskExecutorRule`; the rule
 * was a holdover (this test uses `Flow` + `runTest`, never `LiveData`) and is dropped.
 * `mockk<Release>()` for the value object becomes a real constructed [Release] — Mokkery
 * cannot mock final classes anyway, and the test never invoked methods on it.
 *
 * Mokkery API mapping vs the prior MockK shape:
 * - `mockk<T>()` → `mock<T>(MockMode.autoUnit)` (autoUnit replaces `coEvery { ... } just Runs`)
 * - `coEvery { suspendFn() } returns x` → `everySuspend { suspendFn() } returns x`
 * - `coEvery { plainFn() }` → `every { plainFn() }` (Mokkery is strict about suspend vs not)
 * - `coVerify(exactly = N) { ... }` → `verifySuspend(exactly(N)) { ... }`
 */
class ReleasesRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val releasesDao: ReleasesDao = mock(MockMode.autoUnit)
    private val cheapsharkSource: CheapsharkSource = mock(MockMode.autoUnit)
    private val impl = ReleasesRepositoryImpl(logger, releasesDao, cheapsharkSource)

    @Test
    fun observe_releases_with_refresh_called() = runTest {
        val release = Release(title = "Test Release", date = 0, image = "thumb.png")

        every { releasesDao.observeAllReleases() } returns flowOf(listOf(release))
        everySuspend { cheapsharkSource.fetchReleases() } returns listOf(release)

        val result = impl.observeReleases().first()
        assertTrue(result.isNotEmpty())

        verify(exactly(1)) { releasesDao.observeAllReleases() }
        verifySuspend(exactly(1)) { cheapsharkSource.fetchReleases() }
        verifySuspend(exactly(1)) { releasesDao.addReleases(release) }
    }

    @Test
    fun refresh_releases_skips_dao_observe_path() = runTest {
        val release = Release(title = "Test Release", date = 0, image = "thumb.png")

        everySuspend { cheapsharkSource.fetchReleases() } returns listOf(release)

        impl.refreshReleases()

        verify(exactly(0)) { releasesDao.observeAllReleases() }
        verifySuspend(exactly(1)) { cheapsharkSource.fetchReleases() }
        verifySuspend(exactly(1)) { releasesDao.addReleases(release) }
    }
}
