package pm.bam.gamedeals.logging

import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExpectedFailureTest {

    private class SessionRejected(cause: Throwable? = null) : RuntimeException("signed out", cause), ExpectedFailure

    @Test
    fun a_null_throwable_is_not_expected() {
        assertFalse(isExpectedFailure(null))
    }

    @Test
    fun cancellation_is_expected_directly_and_when_wrapped() {
        assertTrue(isExpectedFailure(CancellationException("Job was cancelled")))
        assertTrue(isExpectedFailure(IllegalStateException("load failed", CancellationException("Job was cancelled"))))
    }

    @Test
    fun a_marked_failure_is_expected_directly_and_when_wrapped() {
        assertTrue(isExpectedFailure(SessionRejected()))
        assertTrue(isExpectedFailure(IllegalStateException("sync failed", SessionRejected())))
    }

    @Test
    fun an_ordinary_defect_is_not_expected_even_with_a_defect_cause() {
        assertFalse(isExpectedFailure(IllegalStateException("query returned no rows", IllegalArgumentException("bad id"))))
    }
}
