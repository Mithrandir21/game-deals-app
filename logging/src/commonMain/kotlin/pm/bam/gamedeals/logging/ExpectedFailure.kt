package pm.bam.gamedeals.logging

import kotlin.coroutines.cancellation.CancellationException

/**
 * Marks a failure the app handles by design, so an ERROR log of it is recorded as a Sentry breadcrumb
 * rather than raised as an issue (see [isExpectedFailure]).
 *
 * For failure types that only ever mean "working as intended" — e.g. ITAD rejecting a session whose refresh
 * was already logged — not for anything that could also signal a defect. Matched by type, so it survives R8.
 */
interface ExpectedFailure

/**
 * Whether [throwable] (or anything in its cause chain) is a failure the app already handles as designed, so
 * it shouldn't become a Sentry issue:
 * - a connectivity failure ([isExpectedNetworkFailure]);
 * - coroutine cancellation — routine teardown (a screen left mid-load, a ViewModel cleared) that reached an
 *   ERROR log through a broad `catch` instead of [runCatchingLogged];
 * - anything marked [ExpectedFailure].
 */
fun isExpectedFailure(throwable: Throwable?): Boolean =
    isExpectedNetworkFailure(throwable) ||
        throwable.anyInCauseChain { it is CancellationException || it is ExpectedFailure }
