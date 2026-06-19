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
import pm.bam.gamedeals.domain.repositories.pricewatch.PriceWatchChecker
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The shared poll body driven by the Android worker / iOS BGTask handler. */
class NotificationPollTest {

    private val sync: NotificationSync = mock(MockMode.autoUnit)
    private val priceWatchChecker: PriceWatchChecker = mock(MockMode.autoUnit)
    private val presenter: NotificationPresenter = mock(MockMode.autoUnit)

    private fun alert(id: String) = PendingNotificationAlert(id, "title-$id", emptyList())

    @Test
    fun new_alerts_from_both_sources_are_handed_to_the_presenter() = runTest {
        val itad = listOf(alert("n1"), alert("n2"))
        val priceWatch = listOf(alert("pw1"))
        everySuspend { sync.syncAndCollectNew() } returns itad
        everySuspend { priceWatchChecker.collectCrossedAlerts() } returns priceWatch

        runNotificationPoll(sync, priceWatchChecker, presenter)

        verifySuspend(exactly(1)) { presenter.present(itad + priceWatch) }
    }

    @Test
    fun no_new_alerts_does_not_touch_the_presenter() = runTest {
        everySuspend { sync.syncAndCollectNew() } returns emptyList()
        everySuspend { priceWatchChecker.collectCrossedAlerts() } returns emptyList()

        runNotificationPoll(sync, priceWatchChecker, presenter)

        verifySuspend(exactly(0)) { presenter.present(any()) }
    }

    @Test
    fun a_sync_failure_propagates_so_the_worker_can_retry() = runTest {
        everySuspend { sync.syncAndCollectNew() } throws RuntimeException("boom")

        assertFailsWith<RuntimeException> { runNotificationPoll(sync, priceWatchChecker, presenter) }

        verifySuspend(exactly(0)) { presenter.present(any()) }
    }
}
