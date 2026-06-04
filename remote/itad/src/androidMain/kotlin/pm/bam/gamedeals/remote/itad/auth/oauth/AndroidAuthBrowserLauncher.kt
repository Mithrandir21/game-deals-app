package pm.bam.gamedeals.remote.itad.auth.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred

/**
 * Android [AuthBrowserLauncher] (epic #219, Phase 2.2): opens the authorize URL in the system browser
 * and awaits the redirect, which the app's redirect `Activity` delivers via [AuthRedirectBus].
 *
 * Uses a plain `ACTION_VIEW` browser intent (no Custom Tabs dependency). Cancellation of the calling
 * coroutine cancels the await and clears the bus; an abandoned browser tab simply leaves the await
 * pending until cancelled (best-effort — verify the cancel/dismiss UX on a device).
 */
class AndroidAuthBrowserLauncher(
    private val context: Context,
) : AuthBrowserLauncher {

    override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult {
        val deferred = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(deferred)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            deferred.await()
        } finally {
            AuthRedirectBus.clear()
        }
    }
}
