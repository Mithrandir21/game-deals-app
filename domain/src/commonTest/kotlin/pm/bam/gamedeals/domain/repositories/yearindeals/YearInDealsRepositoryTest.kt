package pm.bam.gamedeals.domain.repositories.yearindeals

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.not
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.GameHistoryLow
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.featureflags.NoOpFeatureFlags
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearInDealsRepositoryTest {

    private val zone = TimeZone.UTC
    private fun at(year: Int, month: Int, day: Int) = LocalDateTime(year, month, day, 12, 0).toInstant(zone).toEpochMilliseconds()

    private class FakeSampleMode(var on: Boolean = false) : YearInDealsSampleMode {
        override fun observe(): Flow<Boolean> = MutableStateFlow(on)
        override suspend fun isOn() = on
        override suspend fun set(on: Boolean) { this.on = on }
    }

    private val collection = (1..150).map { CollectionEntry(gameId = "c$it", title = "C$it", addedEpochMs = at(2026, 1 + it % 11, 1 + it % 27)) }
    private val waitlist = listOf(WaitlistEntry(gameId = "w1", title = "W1", addedEpochMs = at(2024, 5, 5)))

    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) { everySuspend { getCollection() } returns collection }
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) { everySuspend { getWaitlist() } returns waitlist }
    private val priceBatches = mutableListOf<Int>()
    private val dealsSource: DealsSource = mock(MockMode.autoUnit) {
        everySuspend { fetchBundleGamePrices(any()) } calls { (ids: List<String>) ->
            priceBatches += ids.size
            ids.map { BundleGamePrice(it, "Steam", 10.0, null, 0, historicalLowValue = null, historicalLowDenominated = null, currency = "USD", bestRegularValue = 10.0) }
        }
        everySuspend { fetchHistoryLows(any()) } returns listOf(GameHistoryLow("w1", 5.0, 20.0, 75, "USD", at(2026, 8, 1)))
    }

    private fun repository(
        now: Long = at(2026, 12, 15),
        sample: FakeSampleMode = FakeSampleMode(),
        flags: FeatureFlags = NoOpFeatureFlags,
        source: DealsSource = dealsSource,
    ) = YearInDealsRepositoryImpl(
        TestingLoggingListener(), collectionRepository, waitlistRepository, source, flags, sample, { now }, { zone },
    )

    @Test
    fun builds_the_year_and_prices_the_collection_in_batches_of_100() = runTest {
        val year = repository().getYearInDeals()
        assertEquals(2026, year.year)
        assertEquals(150, year.collected!!.count)
        assertEquals("$1,500.00", year.fullPriceValue)
        assertEquals(1, year.waitlistWins!!.count)
        assertNotNull(year.longestWait)
        assertEquals(listOf(100, 50), priceBatches)
    }

    @Test
    fun when_prices_and_lows_fail_their_sections_are_left_out_but_the_rest_shows() = runTest {
        val failing: DealsSource = mock(MockMode.autoUnit) {
            everySuspend { fetchBundleGamePrices(any()) } throws IllegalStateException("429")
            everySuspend { fetchHistoryLows(any()) } throws IllegalStateException("429")
        }
        val year = repository(source = failing).getYearInDeals()
        assertNull(year.fullPriceValue)
        assertNull(year.waitlistWins)
        assertEquals(150, year.collected!!.count)
        assertNotNull(year.longestWait)
    }

    @Test
    fun when_the_collection_cannot_load_the_whole_card_fails() = runTest {
        val offline: CollectionRepository = mock(MockMode.autoUnit) { everySuspend { getCollection() } throws IllegalStateException("offline") }
        val repo = YearInDealsRepositoryImpl(
            TestingLoggingListener(), offline, waitlistRepository, dealsSource, NoOpFeatureFlags, FakeSampleMode(), { at(2026, 12, 15) }, { zone },
        )
        assertFailsWith<IllegalStateException> { repo.getYearInDeals() }
    }

    @Test
    fun sample_mode_returns_sample_data_without_touching_itad() = runTest {
        val year = repository(sample = FakeSampleMode(on = true)).getYearInDeals()
        assertEquals("Hades", year.collected!!.highlights.first().title)
        verifySuspend(not) { collectionRepository.getCollection() }
        verifySuspend(not) { dealsSource.fetchHistoryLows(any()) }
    }

    @Test
    fun offered_in_season_with_the_flag_on_and_never_with_it_off() {
        val on = object : FeatureFlags by NoOpFeatureFlags {
            override fun isEnabled(flag: FeatureFlag) = flag == FeatureFlag.YearInDeals || NoOpFeatureFlags.isEnabled(flag)
        }
        assertTrue(repository(now = at(2026, 12, 1), flags = on).isOffered())
        assertTrue(repository(now = at(2027, 1, 31), flags = on).isOffered())
        assertFalse(repository(now = at(2026, 11, 30), flags = on).isOffered())
        // The flag ships off, so with no remote value the card is never offered.
        assertFalse(repository(now = at(2026, 12, 1)).isOffered())
    }
}
