@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

/**
 * Catalogue + live-signal metadata for a game, sourced from ITAD `/games/info/v2` (epic #291, Phase 1).
 *
 * Distinct from [GameDetails] (deals/prices) and [IgdbGame] (description/screenshots/similar games): this
 * carries the fields the app previously discarded out of `info/v2` — developers/publishers, tags, release
 * date — plus the redesigned Game Page's live signals: storefront/critic [reviews], waitlist/collection
 * [stats], and current [players] counts.
 *
 * Source-neutral and `@Serializable` so it can be cached as a region-agnostic JSON blob like [GameDetails]
 * (caching wired in Phase 3). [players] is volatile and is cached separately / at a shorter TTL.
 */
@Immutable
@Serializable
data class GameMeta(
    val gameId: String,
    val steamAppId: Int? = null,
    val releaseDate: String? = null,
    val developers: ImmutableList<String> = persistentListOf(),
    val publishers: ImmutableList<String> = persistentListOf(),
    val tags: ImmutableList<String> = persistentListOf(),
    val reviews: ImmutableList<Review> = persistentListOf(),
    val players: Players? = null,
    val stats: Stats? = null,
    val earlyAccess: Boolean = false,
    val achievements: Boolean = false,
    val tradingCards: Boolean = false,
) {
    /** A storefront/critic review score, e.g. Steam (% positive) or Metacritic (/100). [score] is the source's own scale. */
    @Immutable
    @Serializable
    data class Review(
        val source: String,
        val score: Int? = null,
        val count: Int? = null,
        val url: String? = null,
    )

    /** Current player counts from ITAD `players` (recent / day / week / peak). Snapshot only — no history. */
    @Immutable
    @Serializable
    data class Players(
        val recent: Int? = null,
        val day: Int? = null,
        val week: Int? = null,
        val peak: Int? = null,
    )

    /** ITAD global standing: catalogue [rank], and how many users have [waitlisted] / [collected] the game. */
    @Immutable
    @Serializable
    data class Stats(
        val rank: Int? = null,
        val waitlisted: Int? = null,
        val collected: Int? = null,
    )
}
