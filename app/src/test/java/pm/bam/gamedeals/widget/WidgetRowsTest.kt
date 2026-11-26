package pm.bam.gamedeals.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** How many game rows each widget size gets ([widgetRows]); shared by both widgets. */
class WidgetRowsTest {

    @Test
    fun a_narrow_widget_shows_one_game_however_tall_it_is() {
        assertEquals(1, widgetRows(DpSize(width = 150.dp, height = 400.dp)))
    }

    @Test
    fun rows_follow_the_height_left_under_the_header() {
        assertEquals(3, widgetRows(DpSize(width = 300.dp, height = 180.dp)))
    }

    @Test
    fun a_wide_but_very_short_widget_still_shows_one_row() {
        assertEquals(1, widgetRows(DpSize(width = 300.dp, height = 60.dp)))
    }

    @Test
    fun a_very_tall_widget_is_capped_at_the_most_rows() {
        assertEquals(WIDGET_MAX_ROWS, widgetRows(DpSize(width = 300.dp, height = 1000.dp)))
    }
}
