package pm.bam.gamedeals.common.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Turns a composable that is on screen (a deal card, a year card) into a PNG for [pm.bam.gamedeals.common.ui.platform.PlatformActions.shareImage].
 *
 * ```
 * val capture = rememberShareCardCapture()
 * DealCard(modifier = Modifier.captureContent(capture, MaterialTheme.colorScheme.background))
 * Button(onClick = { scope.launch { capture.capturePng()?.let { actions.shareImage(it, link) } } }) { … }
 * ```
 *
 * The card is captured exactly as drawn, at the screen's density, so it must be fully laid out and showing.
 */
@Stable
class ShareCardCapture internal constructor(internal val layer: GraphicsLayer) {

    /** The card as PNG bytes, or `null` if it hasn't been drawn yet or couldn't be encoded. */
    suspend fun capturePng(): ByteArray? = try {
        val bitmap = layer.toImageBitmap()
        if (bitmap.width == 0 || bitmap.height == 0) null else bitmap.encodePng().takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

@Composable
fun rememberShareCardCapture(): ShareCardCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ShareCardCapture(layer) }
}

/**
 * Records everything this element draws into [capture], and still draws it on screen as normal.
 *
 * [background] fills the image behind the content. Pass the colour the card sits on: the shared image then has no
 * see-through pixels, which matters because chat apps that convert images to JPEG (WhatsApp, for one) turn them black,
 * so a rounded card would arrive with black corners.
 */
fun Modifier.captureContent(capture: ShareCardCapture, background: Color): Modifier = drawWithContent {
    capture.layer.record {
        drawRect(background)
        this@drawWithContent.drawContent()
    }
    drawLayer(capture.layer)
}

/** PNG bytes for this bitmap; empty if the platform encoder fails. */
internal expect fun ImageBitmap.encodePng(): ByteArray
