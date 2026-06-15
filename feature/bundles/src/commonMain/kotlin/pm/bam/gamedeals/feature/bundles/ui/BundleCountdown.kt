package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Clock
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.bundles.generated.resources.Res
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_countdown_ended
import pm.bam.gamedeals.feature.bundles.generated.resources.bundle_detail_countdown_label

/**
 * The bundle's live countdown to [expiryEpochMs] (e.g. "11d 16h 32m 05s"). The per-second [produceState]
 * tick is the ONLY state read in this leaf composable, so only this `Text` recomposes each second — never
 * the surrounding detail body. [nowMillis] is injectable so previews/tests pass a fixed clock (no ticking
 * in the preview tool).
 */
@Composable
internal fun BundleCountdown(
    expiryEpochMs: Long,
    modifier: Modifier = Modifier,
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

    val label = stringResource(Res.string.bundle_detail_countdown_label)
    val text = if (remaining <= 0L) {
        stringResource(Res.string.bundle_detail_countdown_ended)
    } else {
        formatCountdown(remaining)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.semantics { contentDescription = "$label: $text" },
    )
}

@Preview
@Composable
private fun BundleCountdownPreview() {
    // Fixed clock so the preview renders a stable "11d 16h 32m 05s" without ticking in the tool.
    val remainingMs = ((11L * 86_400 + 16 * 3_600 + 32 * 60 + 5) * 1000)
    GameDealsTheme {
        BundleCountdown(expiryEpochMs = remainingMs, nowMillis = { 0L })
    }
}
