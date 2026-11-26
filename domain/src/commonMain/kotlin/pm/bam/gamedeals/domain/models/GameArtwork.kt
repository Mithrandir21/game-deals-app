package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The full set of cover/banner art ITAD serves for a game, carried whole so each UI surface can pick
 * the variant that fits its slot instead of being handed one pre-chosen URL (this replaced the old
 * `bestArt()` selector).
 *
 * The measured ITAD variants (all from one game's `assets`):
 *  - [banner145] 145×68, [banner300] 300×140, [banner400] 400×187 — the Steam **header** ratio (~2.14:1)
 *  - [banner600] 600×344 — a wider capsule (~1.74:1, ≈16:9)
 *  - [boxart] 300×450 — the portrait Steam library capsule (2:3)
 *
 * Every field is nullable: ITAD may omit any variant, and persisted/transient entities can carry none.
 * Surfaces select through the [thumbnail]/[hero]/[portrait] accessors rather than reading raw fields, so
 * the fallback order lives in one place.
 *
 * Persisted on [Deal]/[Game] via Room `@Embedded(prefix = "art_")` (five `art_*` columns); on the
 * JSON-blob-cached models (`Bundle.BundleGame`, [RankedGame], `GameDetails`/`DealDetails`) it serializes
 * as a nested object.
 */
@Immutable
@Serializable
data class GameArtwork(
    @SerialName("banner145") val banner145: String? = null,
    @SerialName("banner300") val banner300: String? = null,
    @SerialName("banner400") val banner400: String? = null,
    @SerialName("banner600") val banner600: String? = null,
    @SerialName("boxart") val boxart: String? = null,
)

/** Wide thumbnail for dense list rows / peek headers (≈2.14:1). Prefers the light header banners. */
val GameArtwork.thumbnail: String?
    get() = banner300 ?: banner400 ?: banner145 ?: boxart

/** Large hero art for full-width cards (≈16:9). Prefers the high-res wide banner. */
val GameArtwork.hero: String?
    get() = banner600 ?: banner400 ?: banner300 ?: boxart

/** Portrait cover (2:3) for cover slots, e.g. the Game page when no IGDB cover is available. */
val GameArtwork.portrait: String?
    get() = boxart ?: banner600 ?: banner400
