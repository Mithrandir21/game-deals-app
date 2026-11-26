package pm.bam.gamedeals.common.ui.adaptive

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadableWidthTest {

    @Test
    fun a_phone_keeps_the_full_width() {
        assertEquals(0.dp, readableSideMargin(411.dp))
        assertEquals(0.dp, readableSideMargin(READABLE_CONTENT_MAX_WIDTH))
    }

    @Test
    fun a_wider_screen_centres_the_column_at_the_readable_width() {
        // A landscape Pixel Tablet is 1280 dp wide: 280 dp each side leaves 720 dp of content.
        assertEquals(280.dp, readableSideMargin(1280.dp))
        assertEquals(60.dp, readableSideMargin(840.dp))
    }
}
