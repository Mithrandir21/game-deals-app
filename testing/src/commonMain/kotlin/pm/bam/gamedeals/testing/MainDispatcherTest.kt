@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

abstract class MainDispatcherTest {
    protected val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    protected fun installMainDispatcher() {
        Dispatchers.setMain(testDispatcher)
    }

    protected fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }
}
