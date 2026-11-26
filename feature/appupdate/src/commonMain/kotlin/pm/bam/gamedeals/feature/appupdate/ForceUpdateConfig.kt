package pm.bam.gamedeals.feature.appupdate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The remote payload attached to the `force_update` feature flag:
 *
 * ```json
 * { "minimum_version": "1.2.0", "blocking": true }
 * ```
 *
 * @property minimumVersion the oldest `versionName` still considered supported. Builds *below* it are
 *   prompted; builds at or above it are not.
 * @property blocking whether the prompt is a hard gate (no dismiss, no back press) or a dismissible nudge.
 *   Defaults to `false`, so forgetting the field yields the gentler behaviour rather than locking users out.
 */
@Serializable
internal data class ForceUpdateConfig(
    @SerialName("minimum_version") val minimumVersion: String,
    @SerialName("blocking") val blocking: Boolean = false,
)

// Lenient about extra keys so the payload can grow server-side without older builds falling over — an older
// client seeing a field it doesn't understand should ignore it, not stop gating.
private val json = Json { ignoreUnknownKeys = true }

/**
 * Parses a raw flag payload, returning `null` for anything unusable — absent, malformed, or missing the
 * required `minimum_version`. Every such case means "no prompt": a typo in the remote config must never be
 * able to lock users out of the app.
 */
internal fun parseForceUpdateConfig(raw: String?): ForceUpdateConfig? {
    if (raw.isNullOrBlank()) return null
    return runCatching { json.decodeFromString<ForceUpdateConfig>(raw) }
        .getOrNull()
        ?.takeIf { it.minimumVersion.isNotBlank() }
}
