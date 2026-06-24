package pm.bam.gamedeals.common.time

/**
 * A small time-source abstraction so production code never reads the wall clock directly.
 *
 * Production code that needs "now" injects a [Clock] and calls [nowMillis]; tests inject a
 * fake whose value can be advanced deterministically. The single concrete production
 * implementation lives at the composition root (`:app`) and delegates to
 * [System.currentTimeMillis].
 */
fun interface Clock {

    /** Returns the current epoch time in milliseconds, equivalent to [System.currentTimeMillis]. */
    fun nowMillis(): Long
}
