@file:OptIn(kotlin.time.ExperimentalTime::class)

package pm.bam.gamedeals.feature.giveaways.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.Giveaway

/** The two status tabs on the giveaways list, mirroring ITAD's Live / Expired filter. */
internal enum class GiveawayStatusTab { LIVE, EXPIRED }

/**
 * Parses GamerPower's `end_date` (UTC, "yyyy-MM-dd HH:mm:ss") into epoch millis for the countdown,
 * or `null` when there's no usable expiry.
 *
 * GamerPower uses "N/A"/blank to mean "no expiry", and [DatetimeParsing.parseDatetime] throws on
 * anything its strict format can't parse — both are guarded here so callers (and Compose previews,
 * whose fixtures use "N/A") never crash and simply get `null` ("No expiry").
 */
internal fun parseGiveawayEndDateMillis(endDate: String?, datetimeParsing: DatetimeParsing): Long? =
    endDate?.takeIf { it.isNotBlank() && it != "N/A" }
        ?.let { runCatching { datetimeParsing.parseDatetime(it).toInstant(TimeZone.UTC).toEpochMilliseconds() }.getOrNull() }

/**
 * A giveaway is "Live" when its source still marks it active AND it hasn't passed its end date — a
 * `null` [endDateMillis] means no expiry, so it stays live. Everything else is "Expired".
 */
internal fun isLive(giveaway: Giveaway, endDateMillis: Long?, nowMillis: Long): Boolean =
    giveaway.status.equals("active", ignoreCase = true) && (endDateMillis == null || endDateMillis > nowMillis)
