package pm.bam.gamedeals.domain.scheduling

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The shared poll body driven by the Android worker / iOS BGTask handler. */
class NotificationPollTest {

    private val sync: NotificationSync = mock(MockMode.autoUnit)
    private val presenter: NotificationPresenter = mock(MockMode.autoUnit)

    private fun alert(id: String) = PendingNotificationAlert(id, "title-$id", emptyList())

    @Test
    fun new_alerts_are_handed_to_the_presenter() = runTest {
        val alerts = listOf(alert("n1"), alert("n2"))
        everySuspend { sync.syncAndCollectNew() } returns alerts

        runNotificationPoll(sync, presenter)

        verifySuspend(exactly(1)) { presenter.present(alerts) }
    }

    @Test
    fun no_new_alerts_does_not_touch_the_presenter() = runTest {
        everySuspend { sync.syncAndCollectNew() } returns emptyList()

        runNotificationPoll(sync, presenter)

        verifySuspend(exactly(0)) { presenter.present(any()) }
    }

    @Test
    fun a_sync_failure_propagates_so_the_worker_can_retry() = runTest {
        everySuspend { sync.syncAndCollectNew() } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> { runNotificationPoll(sync, presenter) }

        verifySuspend(exactly(0)) { presenter.present(any()) }
    }
}
