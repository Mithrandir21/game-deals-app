package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidWinterSaleReminderSchedulerTest {

    private val context: Context = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager
    }

    @After
    fun tearDown() = unmockkObject(WorkManager.Companion)

    @Test
    fun schedule_replaces_one_unique_delayed_job_that_needs_a_connection() {
        val request = slot<OneTimeWorkRequest>()

        AndroidWinterSaleReminderScheduler(context).schedule(90 * 60_000L)

        verify(exactly = 1) { workManager.enqueueUniqueWork(WINTER_SALE_REMINDER_WORK, ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals(90 * 60_000L, request.captured.workSpec.initialDelay)
        assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
        assertEquals(WinterSaleReminderWorker::class.java.name, request.captured.workSpec.workerClassName)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.captured.workSpec.backoffPolicy)
        assertEquals(2 * 60_000L, request.captured.workSpec.backoffDelayDuration)
    }

    @Test
    fun cancel_cancels_the_unique_job() {
        AndroidWinterSaleReminderScheduler(context).cancel()

        verify(exactly = 1) { workManager.cancelUniqueWork(WINTER_SALE_REMINDER_WORK) }
    }

    @Test
    fun pending_while_the_job_waits_or_runs_but_not_when_missing_or_finished() = runTest {
        val scheduler = AndroidWinterSaleReminderScheduler(context)

        every { workManager.getWorkInfosForUniqueWorkFlow(WINTER_SALE_REMINDER_WORK) } returns flowOf(listOf(info(WorkInfo.State.ENQUEUED)))
        assertTrue(scheduler.isPending())
        every { workManager.getWorkInfosForUniqueWorkFlow(WINTER_SALE_REMINDER_WORK) } returns flowOf(listOf(info(WorkInfo.State.RUNNING)))
        assertTrue(scheduler.isPending())
        every { workManager.getWorkInfosForUniqueWorkFlow(WINTER_SALE_REMINDER_WORK) } returns flowOf(listOf(info(WorkInfo.State.SUCCEEDED)))
        assertFalse(scheduler.isPending())
        every { workManager.getWorkInfosForUniqueWorkFlow(WINTER_SALE_REMINDER_WORK) } returns flowOf(emptyList())
        assertFalse(scheduler.isPending())
    }

    private fun info(state: WorkInfo.State) = WorkInfo(UUID.randomUUID(), state, emptySet(), Data.EMPTY, Data.EMPTY, 0, 0)
}
