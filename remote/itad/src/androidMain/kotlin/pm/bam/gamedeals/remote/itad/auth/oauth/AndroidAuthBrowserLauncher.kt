package pm.bam.gamedeals.remote.itad.auth.oauth

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android [AuthBrowserLauncher] (epic #219, Phase 2.2): opens the authorize URL in the system browser
 * and awaits the redirect, which the app's redirect `Activity` delivers via [AuthRedirectBus].
 *
 * Uses a plain `ACTION_VIEW` browser intent (no Custom Tabs dependency), which reports no cancel of its own:
 * coming back to the app without a redirect (Back, a closed tab, an app switch) resolves the attempt as
 * [AuthRedirectResult.Cancelled]. Cancellation of the calling coroutine cancels the await and clears the bus.
 */
class AndroidAuthBrowserLauncher(
    private val context: Context,
) : AuthBrowserLauncher {

    override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult {
        val redirect = CompletableDeferred<AuthRedirectResult>()
        AuthRedirectBus.register(redirect)
        val application = context.applicationContext as Application
        val returnWatcher = ReturnToAppWatcher()
        application.registerActivityLifecycleCallbacks(returnWatcher)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authorizeUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            awaitRedirectOrAbandon(redirect, returnWatcher.returned)
        } finally {
            application.unregisterActivityLifecycleCallbacks(returnWatcher)
            AuthRedirectBus.clear()
        }
    }

    /** Completes [returned] the first time any of the app's activities resumes after the browser opened. */
    private class ReturnToAppWatcher : Application.ActivityLifecycleCallbacks {
        val returned = CompletableDeferred<Unit>()

        override fun onActivityResumed(activity: Activity) {
            returned.complete(Unit)
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}

/**
 * Waits for the browser [redirect], resolving as [AuthRedirectResult.Cancelled] once the user is back in the app
 * ([returnedToApp]) and no redirect has arrived within [graceMs].
 */
internal suspend fun awaitRedirectOrAbandon(
    redirect: CompletableDeferred<AuthRedirectResult>,
    returnedToApp: Deferred<Unit>,
    graceMs: Long = REDIRECT_GRACE_MS,
): AuthRedirectResult = coroutineScope {
    val abandonWatch = launch {
        returnedToApp.await()
        delay(graceMs)
        redirect.complete(AuthRedirectResult.Cancelled)
    }
    redirect.await().also { abandonWatch.cancel() }
}

// The redirect Activity delivers before the app's own Activity resumes; the grace only covers unusual orderings.
private const val REDIRECT_GRACE_MS = 1_000L
