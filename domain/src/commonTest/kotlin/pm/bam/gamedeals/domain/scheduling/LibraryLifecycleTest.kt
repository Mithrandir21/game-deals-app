package pm.bam.gamedeals.domain.scheduling

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import kotlin.test.Test

/** The shared, app-scoped library reconcile driven by each platform's auth-state observer. */
class LibraryLifecycleTest {

    private val waitlist: WaitlistRepository = mock(MockMode.autoUnit)
    private val collection: CollectionRepository = mock(MockMode.autoUnit)
    private val ignored: IgnoredRepository = mock(MockMode.autoUnit)
    private val logger: Logger = mock(MockMode.autoUnit)

    @Test
    fun logged_in_reconciles_all_three_lists_and_clears_nothing() = runTest {
        everySuspend { waitlist.getWaitlist() } returns emptyList()
        everySuspend { collection.getCollection() } returns emptyList()
        everySuspend { ignored.getIgnored() } returns emptyList()

        applyLibraryLifecycle(AuthState.LoggedIn("bob"), waitlist, collection, ignored, logger)

        verifySuspend(exactly(1)) { waitlist.getWaitlist() }
        verifySuspend(exactly(1)) { collection.getCollection() }
        verifySuspend(exactly(1)) { ignored.getIgnored() }
        verifySuspend(exactly(0)) { waitlist.clearLocal() }
        verifySuspend(exactly(0)) { collection.clearLocal() }
        verifySuspend(exactly(0)) { ignored.clearLocal() }
    }

    @Test
    fun logged_out_clears_all_three_lists_and_fetches_nothing() = runTest {
        applyLibraryLifecycle(AuthState.LoggedOut, waitlist, collection, ignored, logger)

        verifySuspend(exactly(1)) { waitlist.clearLocal() }
        verifySuspend(exactly(1)) { collection.clearLocal() }
        verifySuspend(exactly(1)) { ignored.clearLocal() }
        verifySuspend(exactly(0)) { waitlist.getWaitlist() }
        verifySuspend(exactly(0)) { collection.getCollection() }
        verifySuspend(exactly(0)) { ignored.getIgnored() }
    }

    @Test
    fun one_failing_reconcile_is_best_effort_and_the_others_still_run() = runTest {
        everySuspend { waitlist.getWaitlist() } throws RuntimeException("boom")
        everySuspend { collection.getCollection() } returns emptyList()
        everySuspend { ignored.getIgnored() } returns emptyList()

        applyLibraryLifecycle(AuthState.LoggedIn("bob"), waitlist, collection, ignored, logger)

        verifySuspend(exactly(1)) { collection.getCollection() }
        verifySuspend(exactly(1)) { ignored.getIgnored() }
    }
}
