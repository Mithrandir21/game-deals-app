@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignInPromptViewModelTest : MainDispatcherTest() {

    private val accountRepository: AccountRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    @Test
    fun login_runs_oauth_and_invokes_completion() = runTest {
        everySuspend { accountRepository.login() } returns ItadUser("bob")
        val viewModel = SignInPromptViewModel(accountRepository, logger)

        var completed = false
        viewModel.login { completed = true }
        advanceUntilIdle()

        verifySuspend(exactly(1)) { accountRepository.login() }
        assertTrue(completed)
        assertFalse(viewModel.signingIn.value)
    }

    @Test
    fun signing_in_is_true_while_in_flight_then_cleared() = runTest {
        val gate = CompletableDeferred<Unit>()
        everySuspend { accountRepository.login() } calls {
            gate.await()
            ItadUser("bob")
        }
        val viewModel = SignInPromptViewModel(accountRepository, logger)

        viewModel.login { }
        runCurrent()
        assertTrue(viewModel.signingIn.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.signingIn.value)
    }
}
