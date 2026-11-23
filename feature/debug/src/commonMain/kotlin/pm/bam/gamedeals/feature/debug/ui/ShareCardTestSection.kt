package pm.bam.gamedeals.feature.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.share.captureContent
import pm.bam.gamedeals.common.ui.share.rememberShareCardCapture
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_share_card_button
import pm.bam.gamedeals.feature.debug.generated.resources.debug_share_card_desc
import pm.bam.gamedeals.feature.debug.generated.resources.debug_share_card_failed

/** Hades on ITAD: a real game, so the shared link's preview page and the app link can be checked end to end. */
private const val SAMPLE_GAME_ID = "018d937f-33f0-7200-80fc-87f769196c84"

/**
 * Share-image test — a [DebugScreen] section. Draws a sample card, captures it as a PNG and opens the share sheet
 * with the image and a real game link, so image sharing and the link can be tried on a phone before the real deal
 * card exists.
 */
@Composable
internal fun ShareCardTestSection() {
    val actions = LocalPlatformActions.current
    val capture = rememberShareCardCapture()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val link = ShareLinks.game(SAMPLE_GAME_ID).orEmpty()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
    ) {
        Text(
            text = stringResource(Res.string.debug_share_card_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().captureContent(capture, MaterialTheme.colorScheme.background),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Column(Modifier.padding(GameDealsCustomTheme.spacing.large)) {
                Text("Hades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Sample card · Game Deals", style = MaterialTheme.typography.bodyMedium)
                Text(link, style = MaterialTheme.typography.labelSmall)
            }
        }
        Button(onClick = {
            scope.launch {
                val png = capture.capturePng()
                failed = png == null
                if (png != null) actions.shareImage(png, link)
            }
        }) {
            Text(stringResource(Res.string.debug_share_card_button))
        }
        if (failed) {
            Text(
                text = stringResource(Res.string.debug_share_card_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
