package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
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
import java.util.concurrent.TimeUnit

/**
 * JVM-host coverage for the WorkManager wiring, intercepting the static `WorkManager.getInstance` with
 * mockk (mirrors the Room-static approach in `DealsRepositoryTest`). Guards the contract that matters for
 * background delivery: a single uniquely-named periodic poll, `KEEP` so re-arming on every start is
 * idempotent, a CONNECTED constraint, and a matching cancel.
 */
class AndroidNotificationSchedulerTest {

    private val context: Context = mockk(relaxed = true)
    private val workManager: WorkManager = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(context) } returns workManager
    }

    @After
    fun tearDown() = unmockkObject(WorkManager.Companion)

    private fun scheduler() = AndroidNotificationScheduler(context)

    @Test
    fun schedule_enqueues_a_unique_keep_periodic_poll_with_a_network_constraint() {
        val request = slot<PeriodicWorkRequest>()

        scheduler().schedule()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                NOTIFICATION_POLL_WORK,
                ExistingPeriodicWorkPolicy.UPDATE, // update existing users' interval/constraints
                capture(request),
            )
        }
        assertEquals(TimeUnit.HOURS.toMillis(6), request.captured.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, request.captured.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun cancel_cancels_the_unique_poll_work() {
        scheduler().cancel()

        verify(exactly = 1) { workManager.cancelUniqueWork(NOTIFICATION_POLL_WORK) }
    }
}
