package pm.bam.gamedeals.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleCountdown
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleKind
import pm.bam.gamedeals.domain.repositories.salecalendar.StoreSale
import java.util.Locale

/** The sale countdown widget's words for each state (matrix 7.4), in English. */
@RunWith(AndroidJUnit4::class)
class SaleCountdownWidgetTextTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().let { app ->
        app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.US) })
    }

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val start = java.time.Instant.parse("2026-12-17T18:00:00Z").toEpochMilli()
    private val winter = StoreSale("steam-winter-2026", "Steam", "Steam Winter Sale", start, start + 18 * day, SaleKind.BIG)

    @Test
    fun an_upcoming_sale_counts_whole_days_to_its_start() {
        val ui = SaleCountdown.Upcoming(winter).toUi(context, nowMillis = start - 5 * day - 3 * hour)

        assertEquals("Steam Winter Sale", ui.name)
        assertEquals("Starts in 5 days", ui.headline)
        assertEquals("Steam · starts Dec 17", ui.detail)
    }

    @Test
    fun a_running_sale_counts_hours_on_its_last_two_days_then_says_under_an_hour() {
        val end = winter.endEpochMs
        assertEquals("Ends in 30 hours", SaleCountdown.Running(winter).toUi(context, end - 30 * hour).headline)
        assertEquals("Ends in 1 hour", SaleCountdown.Running(winter).toUi(context, end - hour).headline)
        assertEquals("Ends in under an hour", SaleCountdown.Running(winter).toUi(context, end - 59 * 60 * 1000).headline)
        assertEquals("Steam · ends Jan 4", SaleCountdown.Running(winter).toUi(context, end - day * 3).detail)
    }

    private val counting = SaleCountdownState.Counting(SaleCountdownUi("Steam Winter Sale", "Starts in 5 days", "Steam · starts Dec 17"))

    @Test
    fun two_rows_high_the_widget_shows_the_header_sale_count_and_date() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(160.dp, 180.dp))
        provideComposable { GlanceTheme { SaleCountdownContent(counting) } }

        onNode(hasText("Sales")).assertExists()
        onNode(hasText("Steam Winter Sale")).assertExists()
        onNode(hasText("Starts in 5 days")).assertExists()
        onNode(hasText("Steam · starts Dec 17")).assertExists()
    }

    @Test
    fun one_row_high_the_widget_keeps_the_sale_and_its_count_and_drops_the_rest() = runGlanceAppWidgetUnitTest {
        // A 2x1 widget on Lite_Phone_36 is about 156 x 82 dp; all four lines cut off the count there.
        setContext(context)
        setAppWidgetSize(DpSize(156.dp, 82.dp))
        provideComposable { GlanceTheme { SaleCountdownContent(counting) } }

        onNode(hasText("Steam Winter Sale")).assertExists()
        onNode(hasText("Starts in 5 days")).assertExists()
        onNode(hasText("Sales")).assertDoesNotExist()
        onNode(hasText("Steam · starts Dec 17")).assertDoesNotExist()
    }

    @Test
    fun with_no_sale_ahead_the_widget_says_so() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { GlanceTheme { SaleCountdownContent(SaleCountdownState.NoSale) } }

        onNode(hasText("No sale announced yet.")).assertExists()
    }

    @Test
    fun switched_off_the_widget_is_a_plain_tile_into_the_app() = runGlanceAppWidgetUnitTest {
        setContext(context)
        provideComposable { GlanceTheme { SaleCountdownContent(SaleCountdownState.SwitchedOff) } }

        onNode(hasText("Game Deals")).assertExists()
        onNode(hasText("Tap for today's deals")).assertExists()
        onNode(hasText("Sales")).assertDoesNotExist()
    }
}
