package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderDecision
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderPresenter

class WinterSaleReminderWorkerTest {

    private val reminder: WinterSaleReminder = mockk()
    private val presenter: WinterSaleReminderPresenter = mockk(relaxed = true)

    @Before
    fun setUp() {
        startKoin { modules(module { single { reminder }; single { presenter } }) }
    }

    @After
    fun tearDown() = stopKoin()

    @Test
    fun show_posts_the_reminder_and_succeeds() = runTest {
        coEvery { reminder.claimDelivery() } returns WinterSaleReminderDecision.Show

        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        coVerify(exactly = 1) { presenter.present() }
    }

    @Test
    fun skip_succeeds_without_posting() = runTest {
        coEvery { reminder.claimDelivery() } returns WinterSaleReminderDecision.Skip

        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        coVerify(exactly = 0) { presenter.present() }
    }

    @Test
    fun retry_asks_again_up_to_six_times_then_gives_up_quietly() = runTest {
        coEvery { reminder.claimDelivery() } returns WinterSaleReminderDecision.Retry

        for (attempt in 0..5) assertEquals(ListenableWorker.Result.retry(), worker(attempt).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(attempt = 6).doWork())
        coVerify(exactly = 0) { presenter.present() }
    }

    private fun worker(attempt: Int): WinterSaleReminderWorker {
        val params: WorkerParameters = mockk(relaxed = true)
        every { params.runAttemptCount } returns attempt
        return WinterSaleReminderWorker(mockk<Context>(relaxed = true), params)
    }
}
