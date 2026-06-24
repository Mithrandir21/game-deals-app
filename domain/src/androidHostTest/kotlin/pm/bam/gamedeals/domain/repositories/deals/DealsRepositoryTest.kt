package pm.bam.gamedeals.domain.repositories.deals

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.TransactionScope
import androidx.room.Transactor
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.db.cache.DealDetailsCacheEntry
import pm.bam.gamedeals.domain.db.dao.DealDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.DealsDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.dealDetails


class DealsRepositoryTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val logger: Logger = TestingLoggingListener()

    private val dealsDao: DealsDao = mockk()

    private val domainDatabase: DomainDatabase = mockk()

    private val dealsSource: DealsSource = mockk()

    private val dealDetailsCacheDao: DealDetailsCacheDao = mockk()

    private val now = 1_000_000L
    private val clock = Clock { now }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val country = "US"
    private val regionRepository: RegionRepository = mockk {
        coEvery { getSelectedCountryCode() } returns country
    }

    private val transactor: Transactor = mockk()
    private val txScope: TransactionScope<Unit> = mockk()

    private val impl = DealsRepositoryImpl(logger, dealsDao, domainDatabase, dealsSource, clock, regionRepository, dealDetailsCacheDao, json)

    /**
     * Wires Room KMP's two-layer transaction API so the body inside
     * `useWriterConnection { it.immediateTransaction { ... } }` runs in-test
     * without a real database. Mirrors the pre-Room-KMP `withTransaction` capture
     * pattern but for the new commonMain shape (Room 2.7+). The inner
     * `immediateTransaction` block has a `TransactionScope<R>` receiver.
     */
    private fun stubTransaction() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        mockkStatic("androidx.room.TransactorKt")
        val outer = slot<suspend (Transactor) -> Unit>()
        val inner = slot<suspend TransactionScope<Unit>.() -> Unit>()
        coEvery { domainDatabase.useWriterConnection<Unit>(capture(outer)) } coAnswers {
            outer.captured.invoke(transactor)
        }
        coEvery { transactor.immediateTransaction<Unit>(capture(inner)) } coAnswers {
            inner.captured.invoke(txScope)
        }
    }


    @Test
    fun `observe all deals`() = runTest {
        coEvery { dealsDao.observeAllDeals() } returns flowOf(emptyList())

        val result = impl.observeAllDeals().first()
        assertTrue(result.isEmpty())

        coVerify(exactly = 1) { dealsDao.observeAllDeals() }
    }


    @Test
    fun `getDeal - cold cache - fetches, caches the region blob, and returns the fresh value`() = runTest {
        val dealId = "abc123"
        val details = dealDetails()

        coEvery { dealDetailsCacheDao.get(dealId, country) } returns null
        coEvery { dealsSource.fetchDealDetails(dealId) } returns details
        coEvery { dealDetailsCacheDao.upsert(any()) } just runs


        val result = impl.getDeal(dealId)
        assertEquals(details, result)

        coVerify(exactly = 1) { dealsSource.fetchDealDetails(dealId) }
        coVerify(exactly = 1) { dealDetailsCacheDao.upsert(any()) }
    }


    @Test
    fun `getDeal - fresh cache - decodes the cached blob without a fetch`() = runTest {
        val dealId = "abc123"
        val details = dealDetails()
        val entry = DealDetailsCacheEntry(
            dealId = dealId,
            country = country,
            json = json.encodeToString(DealDetails.serializer(), details),
            expires = now + 10_000,
        )
        coEvery { dealDetailsCacheDao.get(dealId, country) } returns entry


        val result = impl.getDeal(dealId)
        assertEquals(details, result)

        coVerify(exactly = 0) { dealsSource.fetchDealDetails(any()) }
        coVerify(exactly = 0) { dealDetailsCacheDao.upsert(any()) }
    }


    @Test
    fun `getDeal - cold cache - refresh failure surfaces (no stale fallback)`() = runTest {
        val dealId = "abc123"

        coEvery { dealDetailsCacheDao.get(dealId, country) } returns null
        coEvery { dealsSource.fetchDealDetails(dealId) } throws Exception("network down")


        assertFailsWith<Exception> { impl.getDeal(dealId) }
    }


    @Test
    fun `getDeals - delegates to source and is not Room-cached`() = runTest {
        val query = DealsQuery(offset = 30)
        val deals = listOf<Deal>(mockk())

        coEvery { dealsSource.fetchDeals(query) } returns deals


        val result = impl.getDeals(query)
        assertEquals(deals, result)

        coVerify(exactly = 1) { dealsSource.fetchDeals(query) }
        coVerify(exactly = 0) { dealsDao.addDeals(*anyVararg()) }
    }


    @Test
    fun `refreshDeals - forced - fetches via source, stamps expires, writes to DAO`() = runTest {
        val storeId = 1
        val fetched: Deal = mockk()
        val stamped: Deal = mockk()
        every { fetched.copy(expires = now + millisInHour * 8, country = country) } returns stamped

        stubTransaction()

        coEvery { dealsDao.clearDealsForStore(storeId, country) } just runs
        coEvery { dealsSource.fetchDealsForStore(query = any()) } returns listOf(fetched)
        coEvery { dealsDao.addDeals(*anyVararg()) } just runs


        impl.refreshDeals(storeId, force = true)


        coVerify(exactly = 1) { dealsDao.clearDealsForStore(storeId, country) }
        coVerify(exactly = 1) { dealsSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { dealsDao.addDeals(stamped) }
        verify(exactly = 1) { fetched.copy(expires = now + millisInHour * 8, country = country) }
    }


    @Test
    fun `refreshDeals - unforced - expired deals - fetches via source`() = runTest {
        val storeId = 1
        val expired: Deal = mockk { every { expires } returns now - 10_000 }
        val fetched: Deal = mockk()
        val stamped: Deal = mockk()
        every { fetched.copy(expires = now + millisInHour * 8, country = country) } returns stamped

        coEvery { dealsDao.getStoreDeals(storeId, country) } returns listOf(expired)

        stubTransaction()

        coEvery { dealsDao.clearDealsForStore(storeId, country) } just runs
        coEvery { dealsSource.fetchDealsForStore(query = any()) } returns listOf(fetched)
        coEvery { dealsDao.addDeals(*anyVararg()) } just runs


        impl.refreshDeals(storeId)


        coVerify(exactly = 1) { dealsDao.clearDealsForStore(storeId, country) }
        coVerify(exactly = 1) { dealsSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 1) { dealsDao.addDeals(stamped) }
    }


    @Test
    fun `refreshDeals - unforced - skips source when DAO has fresh deals`() = runTest {
        val storeId = 1
        val fresh: Deal = mockk { every { expires } returns now + 10_000 }
        coEvery { dealsDao.getStoreDeals(storeId, country) } returns listOf(fresh)


        impl.refreshDeals(storeId)


        coVerify(exactly = 0) { dealsSource.fetchDealsForStore(query = any()) }
        coVerify(exactly = 0) { dealsDao.clearDealsForStore(any(), any()) }
    }


    @Test
    fun `getStoreDeals - reads the active region's cached deals`() = runTest {
        val storeId = 1
        val fresh: Deal = mockk { every { expires } returns now + 10_000 }
        coEvery { dealsDao.getStoreDeals(storeId, country) } returns listOf(fresh)


        val result = impl.getStoreDeals(storeId)


        assertEquals(listOf(fresh), result)
        // Fresh cache for the active region: no fetch, and the read is country-scoped.
        coVerify(exactly = 0) { dealsSource.fetchDealsForStore(query = any()) }
        coVerify { dealsDao.getStoreDeals(storeId, country) }
    }
}
