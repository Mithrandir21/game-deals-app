package pm.bam.gamedeals.oauth

import android.os.Bundle
import androidx.activity.ComponentActivity
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthRedirectBus

/**
 * Receives the ITAD OAuth redirect (`pm.bam.gamedeals://oauth/itad?code=...`) and hands the callback
 * URI to [AuthRedirectBus], resuming the suspended browser launcher (epic #219, Phase 2.2). Declared
 * with the matching `<intent-filter>` in the app manifest; finishes immediately (no UI).
 */
class OAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthRedirectBus.deliver(intent?.data)
        finish()
    }
}
