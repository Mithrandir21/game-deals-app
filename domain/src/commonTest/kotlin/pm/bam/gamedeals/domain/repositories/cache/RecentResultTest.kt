package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecentResultTest {

    private var now = 0L
    private val recent = RecentResult<Int>(Clock { now }, maxAgeMillis = 1_000)
    private var fetches = 0
    private suspend fun fetch(): Int = ++fetches

    @Test
    fun a_result_is_reused_until_it_is_too_old() = runTest {
        assertEquals(1, recent.get(::fetch))
        now = 999
        assertEquals(1, recent.get(::fetch))
        now = 1_000
        assertEquals(2, recent.get(::fetch))
    }

    @Test
    fun overlapping_callers_share_one_fetch() = runTest {
        val results = List(3) { async { recent.get { delay(100); fetch() } } }.awaitAll()

        assertEquals(listOf(1, 1, 1), results)
        assertEquals(1, fetches)
    }

    @Test
    fun invalidate_forces_the_next_fetch() = runTest {
        recent.get(::fetch)
        recent.invalidate()

        assertEquals(2, recent.get(::fetch))
    }

    @Test
    fun a_failed_fetch_is_not_kept() = runTest {
        assertFailsWith<IllegalStateException> { recent.get { error("offline") } }

        assertEquals(1, recent.get(::fetch))
    }
}
