package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_countdown_days
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_countdown_hours
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_countdown_minutes
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_countdown_seconds
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_countdown_ended
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_countdown_label

/**
 * A giveaway's live countdown to [expiryEpochMs] (e.g. "11d 16h 32m 05s"), mirroring the bundle
 * detail timer. The per-second [produceState] tick is the only state read in this leaf composable,
 * so only this `Text` recomposes each second. [nowMillis] is injectable so previews/tests pass a
 * fixed clock (no ticking in the preview tool).
 */
@Composable
internal fun GiveawayCountdown(
    expiryEpochMs: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    val remaining by produceState(
        initialValue = (expiryEpochMs - nowMillis()).coerceAtLeast(0L),
        key1 = expiryEpochMs,
    ) {
        while (true) {
            value = (expiryEpochMs - nowMillis()).coerceAtLeast(0L)
            if (value <= 0L) break
            delay(1000)
        }
    }

    val label = stringResource(Res.string.giveaway_screen_countdown_label)
    val text = if (remaining <= 0L) {
        stringResource(Res.string.giveaway_screen_countdown_ended)
    } else {
        formatCountdown(remaining)
    }
    // Spoken form spells the units out ("16 hours") so TalkBack doesn't read the abbreviated
    // "16 h" as the letter "h". Resolved unconditionally to keep the composable call count stable.
    val spoken = spokenCountdown(remaining)
    val spokenDescription = if (remaining <= 0L) text else "$label: $spoken"
    Text(
        text = text,
        style = style,
        modifier = modifier.semantics { contentDescription = spokenDescription },
    )
}

/**
 * A TalkBack-friendly rendering of a remaining duration, e.g. "16 hours, 32 minutes, 5 seconds"
 * (days only when non-zero, mirroring [formatCountdown]). Uses full words rather than the visual
 * d/h/m/s abbreviations, which screen readers pronounce as bare letters.
 *
 * All four units are resolved every call — never conditionally — so the number of composable
 * invocations stays constant as the countdown ticks past a unit boundary.
 */
@Composable
internal fun spokenCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val days = (totalSeconds / 86_400).toInt()
    val hours = ((totalSeconds % 86_400) / 3_600).toInt()
    val minutes = ((totalSeconds % 3_600) / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val daysStr = pluralStringResource(Res.plurals.giveaway_countdown_days, days, days)
    val hoursStr = pluralStringResource(Res.plurals.giveaway_countdown_hours, hours, hours)
    val minutesStr = pluralStringResource(Res.plurals.giveaway_countdown_minutes, minutes, minutes)
    val secondsStr = pluralStringResource(Res.plurals.giveaway_countdown_seconds, seconds, seconds)
    return listOfNotNull(
        daysStr.takeIf { days > 0 },
        hoursStr,
        minutesStr,
        secondsStr,
    ).joinToString(", ")
}

/**
 * Formats a remaining duration (ms) as the countdown, e.g. "11d 16h 32m 05s". The day segment is
 * dropped once under a day; seconds are zero-padded so the trailing digits don't jump.
 */
internal fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (days > 0) append(days).append("d ")
        append(hours).append("h ")
        append(minutes).append("m ")
        append(seconds.toString().padStart(2, '0')).append('s')
    }
}

@Preview
@Composable
private fun GiveawayCountdownPreview() {
    val remainingMs = ((11L * 86_400 + 16 * 3_600 + 32 * 60 + 5) * 1000)
    GameDealsTheme {
        GiveawayCountdown(expiryEpochMs = remainingMs, nowMillis = { 0L })
    }
}
