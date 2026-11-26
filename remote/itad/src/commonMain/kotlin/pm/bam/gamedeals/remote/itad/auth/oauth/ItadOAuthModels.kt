package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The OAuth token endpoint response (`/oauth/token`) for both the code exchange and refresh. */
@Serializable
data class RemoteItadTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("scope") val scope: String? = null,
)
