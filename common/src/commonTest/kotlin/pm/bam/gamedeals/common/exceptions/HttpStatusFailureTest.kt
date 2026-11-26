package pm.bam.gamedeals.common.exceptions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpStatusFailureTest {

    private class Http(override val httpStatus: Int, cause: Throwable? = null) : RuntimeException("HTTP $httpStatus", cause), HttpStatusFailure

    @Test
    fun the_status_is_found_on_the_throwable_or_any_of_its_causes() {
        assertEquals(404, Http(404).httpStatusOrNull())
        assertEquals(429, IllegalStateException("wrapped", RuntimeException("again", Http(429))).httpStatusOrNull())
    }

    @Test
    fun a_failure_without_an_http_answer_has_no_status() {
        assertNull(RuntimeException("offline").httpStatusOrNull())
        assertFalse(RuntimeException("offline").isHttpNotFound())
        assertFalse(RuntimeException("offline").isRateLimited())
    }

    @Test
    fun not_found_and_rate_limited_are_told_apart() {
        assertTrue(Http(404).isHttpNotFound())
        assertFalse(Http(404).isRateLimited())
        assertTrue(Http(429).isRateLimited())
        assertFalse(Http(500).isHttpNotFound())
    }

    @Test
    fun a_cause_chain_that_points_at_itself_ends() {
        val selfish = object : RuntimeException("loop") {
            override val cause: Throwable get() = this
        }
        assertNull(selfish.httpStatusOrNull())
    }
}
