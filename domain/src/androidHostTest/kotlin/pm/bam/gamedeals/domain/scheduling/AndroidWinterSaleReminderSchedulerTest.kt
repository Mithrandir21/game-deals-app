package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
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
    }

    @Test
    fun cancel_cancels_the_unique_job() {
        AndroidWinterSaleReminderScheduler(context).cancel()

        verify(exactly = 1) { workManager.cancelUniqueWork(WINTER_SALE_REMINDER_WORK) }
    }
}
