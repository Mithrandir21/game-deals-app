package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.UIKit.UIApplication
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS [AuthBrowserLauncher] (epic #219, Phase 2.2) via `ASWebAuthenticationSession`. The system handles
 * the redirect back to [redirectScheme] internally (no Activity/manifest needed); the callback URL's
 * query yields the auth code.
 *
 * NOTE: this file cannot be compiled on the Linux dev box (Kotlin/Native iOS target) — it must be
 * verified with `:iosApp:compileKotlinIosSimulatorArm64` on macOS, and the login flow smoke-tested on a
 * device/simulator. The cancellation error code (1) corresponds to
 * `ASWebAuthenticationSessionErrorCodeCanceledLogin`. `keyWindow` is used for the presentation anchor;
 * may need scene-aware handling on iPad (cf. issue #144).
 */
class IosAuthBrowserLauncher : AuthBrowserLauncher {

    override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult =
        suspendCancellableCoroutine { cont ->
            val url = NSURL.URLWithString(authorizeUrl)
            if (url == null) {
                cont.resume(AuthRedirectResult.Failed("Invalid authorize URL"))
                return@suspendCancellableCoroutine
            }

            val session = ASWebAuthenticationSession(
                uRL = url,
                callbackURLScheme = redirectScheme,
            ) { callbackURL: NSURL?, error: NSError? ->
                cont.resume(toResult(callbackURL, error))
            }
            session.presentationContextProvider = presentationContextProvider
            session.prefersEphemeralWebBrowserSession = false

            cont.invokeOnCancellation { session.cancel() }

            if (!session.start()) {
                cont.resume(AuthRedirectResult.Failed("Could not start ASWebAuthenticationSession"))
            }
        }

    private val presentationContextProvider =
        object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
            override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
                foregroundWindow() ?: UIWindow()
        }

    // `UIApplication.keyWindow` is nil under multi-scene (deprecated since iOS 13); resolve the foreground-active
    // window scene's window so the auth sheet has a valid anchor on iPad too (cf. issue #144).
    private fun foregroundWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
        val active = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
        return (active?.windows?.firstOrNull() as? UIWindow) ?: UIApplication.sharedApplication.keyWindow
    }

    private fun toResult(callbackURL: NSURL?, error: NSError?): AuthRedirectResult = when {
        // ASWebAuthenticationSessionErrorCodeCanceledLogin == 1
        error != null -> if (error.code == 1L) AuthRedirectResult.Cancelled else AuthRedirectResult.Failed(error.localizedDescription)
        callbackURL != null -> {
            val items = NSURLComponents(uRL = callbackURL, resolvingAgainstBaseURL = false).queryItems.orEmpty()
            val code = items.value("code")
            val errorParam = items.value("error")
            when {
                errorParam != null -> AuthRedirectResult.Failed(errorParam)
                code != null -> AuthRedirectResult.Success(code, items.value("state"))
                else -> AuthRedirectResult.Failed("Redirect missing 'code'")
            }
        }
        else -> AuthRedirectResult.Failed("No callback URL")
    }

    @Suppress("UNCHECKED_CAST")
    private fun List<*>.value(name: String): String? =
        (this as List<NSURLQueryItem>).firstOrNull { it.name == name }?.value
}
