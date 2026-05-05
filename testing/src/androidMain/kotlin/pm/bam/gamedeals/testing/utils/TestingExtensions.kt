package pm.bam.gamedeals.testing.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import pm.bam.gamedeals.testing.MainCoroutineRule


private fun <T> List<T>.getOrException(index: Int) = this.getOrElse(index) { idx ->
    println("-----Existing Data-----")
    this.forEachIndexed { existingIndex, t -> println("Index: $existingIndex -- Data: $t") }
    println("-----------------------")
    throw NoSuchElementException("List size is $size. Index $idx out of bounds.")
}

fun <T> List<T>.second() = this.getOrException(1)
fun <T> List<T>.third() = this.getOrException(2)
fun <T> List<T>.fourth() = this.getOrException(3)
fun <T> List<T>.fifth() = this.getOrException(4)
fun <T> List<T>.sixth() = this.getOrException(5)
fun <T> List<T>.seventh() = this.getOrException(6)
fun <T> List<T>.eighth() = this.getOrException(7)
fun <T> List<T>.ninth() = this.getOrException(8)
fun <T> List<T>.tenth() = this.getOrException(9)

/**
 * Returns a [List] of [T] that continuously gets the emissions of the any updates to the [Flow].
 *
 * This will allow a different thread than the one running the main unit test to emit the returned [List] with updates from the [Flow], without
 * blocking, and also automatically cancelling the updating job if [TestScope.backgroundScope] for [coroutineScope].
 *
 *
 * @param coroutineScope For suspend functions to not be blocked during testing, use [TestScope.backgroundScope] for [coroutineScope].
 * @param testDispatcher Use [MainCoroutineRule.testDispatcher] or [UnconfinedTestDispatcher] to allow of greedy collections.
 */
@Suppress("kotlin:S6311")
fun <T> Flow<T>.observeEmissions(coroutineScope: CoroutineScope, testDispatcher: TestDispatcher): List<T> {
    val destination = mutableListOf<T>()

    coroutineScope.launch(testDispatcher) {
        this@observeEmissions.toList(destination)
    }

    return destination
}