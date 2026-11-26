package pm.bam.gamedeals.remote.itad

import pm.bam.gamedeals.logging.ExpectedFailure
import pm.bam.gamedeals.remote.exceptions.RemoteHttpException

/**
 * ITAD answered an account call with 401 after the token refresh had already failed: the session is gone
 * (revoked, or cleared by [pm.bam.gamedeals.remote.itad.auth.oauth.ItadTokenProvider]) or will be retried on
 * the next refresh. Marked [ExpectedFailure] so a sync that logs it at ERROR leaves a breadcrumb, not a
 * second Sentry issue for the same sign-out (GAME-DEALS-G alongside GAME-DEALS-H).
 */
internal class ItadSessionRejectedException(cause: RemoteHttpException.Unauthorized) :
    RuntimeException("ITAD rejected the session (HTTP 401)", cause), ExpectedFailure
