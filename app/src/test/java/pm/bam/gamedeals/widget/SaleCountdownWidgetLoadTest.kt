package pm.bam.gamedeals.widget

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleCalendarRepository
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.testing.FakeFeatureFlags

/** What the sale countdown widget decides to show ([SaleCountdownWidget.load]), before any text is formatted. */
class SaleCountdownWidgetLoadTest {

    // Only used to format a running countdown, which these cases never reach.
    private val context: Context = mockk()
    private val saleCalendar: SaleCalendarRepository = mockk()
    private val flags = FakeFeatureFlags()

    @Before
    fun setUp() {
        startKoin {
            modules(module {
                single { saleCalendar }
                single<FeatureFlags> { flags }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private suspend fun load() = SaleCountdownWidget().load(context, nowMillis = 0)

    @Test
    fun switched_off_it_is_a_plain_tile_and_the_sale_dates_are_not_read() = runTest {
        flags.set(FeatureFlag.SaleCountdown, false)

        assertEquals(SaleCountdownState.SwitchedOff, load())
        coVerify(exactly = 0) { saleCalendar.currentCountdown() }
    }

    @Test
    fun the_switches_are_refreshed_before_it_decides() = runTest {
        coEvery { saleCalendar.currentCountdown() } returns null

        load()

        assertEquals(1, flags.refreshCount)
    }

    @Test
    fun switched_on_with_no_sale_ahead_it_says_so() = runTest {
        coEvery { saleCalendar.currentCountdown() } returns null

        assertEquals(SaleCountdownState.NoSale, load())
    }
}
