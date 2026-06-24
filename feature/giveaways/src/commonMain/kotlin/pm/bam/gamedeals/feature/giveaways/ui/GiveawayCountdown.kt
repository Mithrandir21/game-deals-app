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
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
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
    Text(
        text = text,
        style = style,
        modifier = modifier.semantics { contentDescription = "$label: $text" },
    )
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
