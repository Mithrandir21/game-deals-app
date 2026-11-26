package pm.bam.gamedeals.common.ui.share

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What [ShareCardCapture] hands to the share sheet: a real PNG of the card, with no see-through corners. */
class ShareCardCaptureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun a_drawn_card_becomes_a_png_with_a_solid_background_behind_its_rounded_corners() {
        lateinit var capture: ShareCardCapture
        composeTestRule.setContent {
            capture = rememberShareCardCapture()
            Box(
                modifier = Modifier
                    .size(160.dp, 90.dp)
                    .captureContent(capture, Color.White)
                    .background(Color(0xFF3355AA), RoundedCornerShape(24.dp)),
            ) { Text("Hades") }
        }
        composeTestRule.waitForIdle()

        val png = runBlocking { capture.capturePng() }

        assertNotNull(png)
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png!!.size)
        assertNotNull("decodes as an image", bitmap)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
        // The corner sits outside the rounded card, so it shows the background colour, fully opaque.
        assertEquals(Color.White.toArgb(), bitmap.getPixel(0, 0))
        assertEquals(Color(0xFF3355AA).toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height - 4))
    }

    @Test
    fun a_card_that_was_never_drawn_gives_no_image() {
        lateinit var capture: ShareCardCapture
        composeTestRule.setContent { capture = rememberShareCardCapture() }
        composeTestRule.waitForIdle()

        assertNull(runBlocking { capture.capturePng() })
    }
}
