package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.ReleaseWindow

/**
 * Request body for `POST /deals/v2`. ITAD's rich deal filtering is only available via the POST body's
 * `filter` object (the GET `filter` query param wants a base64-lz-compressed string; bare query params
 * like `cut`/`price`/`type` are silently ignored). The response envelope is identical to the GET
 * variant ([RemoteItadDealsResponse]).
 *
 * The shared [kotlinx.serialization.json.Json] has `encodeDefaults = true`, so optional fields are
 * marked [EncodeDefault] `NEVER` to keep them out of the body when unset — matching the old GET's
 * "omit null params" behaviour and avoiding sending `null` where ITAD expects an object/absent key.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteItadDealsRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val country: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val offset: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val limit: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sort: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val mature: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val shops: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val filter: RemoteItadDealsFilter? = null,
)

/**
 * The `filter` object of [RemoteItadDealsRequest]. Each field maps to one ITAD deal-filter dimension and
 * is omitted when unset. Range fields ([RemoteFilterRange]/[RemoteFilterIntRange]) always serialize both
 * `min` and `max` (ITAD requires both keys; `null` is a valid open bound — except `releaseYear` whose
 * `max` must be an explicit year, handled by the mapper).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RemoteItadDealsFilter(
    @EncodeDefault(EncodeDefault.Mode.NEVER) val cut: RemoteFilterIntRange? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val price: RemoteFilterRange? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val type: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val drm: List<Int>? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val flag: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val steamPerc: RemoteFilterIntRange? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val releaseDays: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val releaseYear: RemoteFilterIntRange? = null,
)

/** Inclusive numeric range; both keys are always emitted (`null` = open bound). */
@Serializable
data class RemoteFilterRange(val min: Double?, val max: Double?)

/** Inclusive integer range (cut/Steam %/year); both keys are always emitted (`null` = open bound). */
@Serializable
data class RemoteFilterIntRange(val min: Int?, val max: Int?)

/**
 * Maps the domain [DealsFilter] to ITAD's wire `filter` object, or `null` when nothing is constrained.
 * [currentYear] resolves the release-recency windows; ITAD rejects an open (`null`) `releaseYear.max`,
 * so the "this year" / "2+ years" windows always supply a concrete `max`.
 */
internal fun DealsFilter.toRemoteFilter(currentYear: Int): RemoteItadDealsFilter? {
    if (isEmpty()) return null
    return RemoteItadDealsFilter(
        cut = minCutPercent?.let { RemoteFilterIntRange(min = it, max = 100) },
        price = maxPrice?.let { RemoteFilterRange(min = null, max = it) },
        type = types.takeIf { it.isNotEmpty() }?.map { it.apiValue }?.sorted(),
        drm = if (drmFree) listOf(DRM_FREE_ID) else null,
        flag = flag?.apiValue,
        steamPerc = minSteamPercent?.let { RemoteFilterIntRange(min = it, max = 100) },
        releaseDays = if (release == ReleaseWindow.NewLast90) RELEASE_NEW_DAYS else null,
        releaseYear = when (release) {
            ReleaseWindow.ThisYear -> RemoteFilterIntRange(min = currentYear, max = currentYear)
            ReleaseWindow.TwoPlusYears -> RemoteFilterIntRange(min = null, max = currentYear - 2)
            else -> null
        },
    )
}

/** ITAD's sentinel id for DRM-free titles in the `drm` filter. */
private const val DRM_FREE_ID = 1000

/** "New release" window for [ReleaseWindow.NewLast90]. */
private const val RELEASE_NEW_DAYS = 90
