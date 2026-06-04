package pm.bam.gamedeals.remote.itad.auth.oauth

import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Process-level hand-off between [AndroidAuthBrowserLauncher] (which suspends awaiting the redirect)
 * and the app's redirect `Activity` (which receives the `pm.bam.gamedeals://oauth/...` deep link and
 * calls [deliver]). Single in-flight authorization at a time (epic #219, Phase 2.2).
 */
object AuthRedirectBus {

    private var pending: CompletableDeferred<AuthRedirectResult>? = null

    fun register(deferred: CompletableDeferred<AuthRedirectResult>) {
        pending = deferred
    }

    fun clear() {
        pending = null
    }

    /** Called by the redirect Activity with the deep-link [uri]; resolves the awaiting launcher. */
    fun deliver(uri: Uri?) {
        val deferred = pending ?: return
        deferred.complete(uri.toAuthResult())
        pending = null
    }

    private fun Uri?.toAuthResult(): AuthRedirectResult {
        if (this == null) return AuthRedirectResult.Failed("Null redirect URI")
        val error = getQueryParameter("error")
        val code = getQueryParameter("code")
        return when {
            error != null -> AuthRedirectResult.Failed(error)
            code != null -> AuthRedirectResult.Success(code, getQueryParameter("state"))
            else -> AuthRedirectResult.Failed("Redirect missing 'code'")
        }
    }
}
