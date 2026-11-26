package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/user/info/v2` response (epic #219, Phase 2) — currently just the username.
 *
 * [username] is nullable because ITAD does return `"username": null` for some accounts; decoding it
 * as non-null threw mid-login and left the session in a half-signed-in state (Sentry KOTLIN-J).
 */
@Serializable
data class RemoteItadUser(
    @SerialName("username") val username: String? = null,
)
