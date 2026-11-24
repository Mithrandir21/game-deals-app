package pm.bam.gamedeals.widget

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pm.bam.gamedeals.R
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleCalendarRepository
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleCountdown
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.shortcuts.AppShortcut
import java.util.Date
import java.util.Locale

class SaleCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = SaleCountdownWidget()
}

/**
 * The sale to count down to on Steam, Epic or GOG, from the Worker's hand-typed dates ([SaleCalendarRepository], which
 * picks between big sales and smaller fests): "Steam Winter Sale · Ends in 5 days". Redrawn every hour by the system
 * (`sale_countdown_widget_info.xml`), so the count is right to the hour. Tapping opens the Deals tab. With the
 * [FeatureFlag.SaleCountdown] switch off it is a plain "Tap for today's deals" tile, as a placed widget can't be removed.
 */
class SaleCountdownWidget : GlanceAppWidget(), KoinComponent {

    private val saleCalendarRepository: SaleCalendarRepository by inject()
    private val featureFlags: FeatureFlags by inject()

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context, System.currentTimeMillis())
        provideContent {
            GlanceTheme {
                SaleCountdownContent(state)
            }
        }
    }

    @VisibleForTesting
    internal suspend fun load(context: Context, nowMillis: Long): SaleCountdownState {
        // The hourly redraw runs without the app being opened, so it is also how a switch flip reaches the widget.
        featureFlags.awaitRefresh()
        if (!featureFlags.isEnabled(FeatureFlag.SaleCountdown)) return SaleCountdownState.SwitchedOff
        val countdown = saleCalendarRepository.currentCountdown() ?: return SaleCountdownState.NoSale
        return SaleCountdownState.Counting(countdown.toUi(context, nowMillis))
    }
}

/** What the countdown widget shows. */
internal sealed interface SaleCountdownState {
    /** The sale countdown is switched off: a plain tile that opens Deals. */
    data object SwitchedOff : SaleCountdownState

    /** No sale is listed ahead. */
    data object NoSale : SaleCountdownState

    data class Counting(val ui: SaleCountdownUi) : SaleCountdownState
}

@Immutable
internal data class SaleCountdownUi(
    /** e.g. "Steam Winter Sale". */
    val name: String,
    /** e.g. "Ends in 5 days". */
    val headline: String,
    /** e.g. "Steam · ends Jan 4". */
    val detail: String,
)

private fun SaleCountdown.toUi(context: Context, nowMillis: Long): SaleCountdownUi {
    val running = this is SaleCountdown.Running
    val target = if (running) sale.endEpochMs else sale.startEpochMs
    val amount = when (val left = timeLeft(nowMillis, target)) {
        is TimeLeft.Days -> context.resources.getQuantityString(R.plurals.widget_sale_days, left.days, left.days)
        is TimeLeft.Hours -> context.resources.getQuantityString(R.plurals.widget_sale_hours, left.hours, left.hours)
        TimeLeft.UnderAnHour -> context.getString(R.string.widget_sale_under_an_hour)
    }
    val date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"), Date(target)).toString()
    return SaleCountdownUi(
        name = sale.name,
        headline = context.getString(if (running) R.string.widget_sale_ends_in else R.string.widget_sale_starts_in, amount),
        detail = context.getString(if (running) R.string.widget_sale_ends_on else R.string.widget_sale_starts_on, sale.store, date),
    )
}

/** How long until a sale starts or ends, rounded down: whole days from two days out, whole hours below that. */
internal sealed interface TimeLeft {
    data class Days(val days: Int) : TimeLeft
    data class Hours(val hours: Int) : TimeLeft
    data object UnderAnHour : TimeLeft
}

private const val HOUR_MS = 60L * 60 * 1000
private const val DAY_MS = 24 * HOUR_MS

internal fun timeLeft(nowMillis: Long, targetMillis: Long): TimeLeft {
    val left = (targetMillis - nowMillis).coerceAtLeast(0)
    return when {
        left >= 2 * DAY_MS -> TimeLeft.Days((left / DAY_MS).toInt())
        left >= HOUR_MS -> TimeLeft.Hours((left / HOUR_MS).toInt())
        else -> TimeLeft.UnderAnHour
    }
}

@Composable
private fun SaleCountdownContent(state: SaleCountdownState) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(AppShortcut.Deals.intent(context))),
    ) {
        when (state) {
            SaleCountdownState.SwitchedOff -> {
                WidgetHeader(R.string.app_name, R.drawable.ic_widget_gift)
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    text = context.getString(R.string.widget_sale_switched_off),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
            SaleCountdownState.NoSale -> {
                WidgetHeader(R.string.widget_sale_countdown_header, R.drawable.ic_widget_timer)
                Spacer(GlanceModifier.height(6.dp))
                WidgetMessage(R.string.widget_sale_none)
            }
            is SaleCountdownState.Counting -> {
                WidgetHeader(R.string.widget_sale_countdown_header, R.drawable.ic_widget_timer)
                Spacer(GlanceModifier.height(6.dp))
                SaleCountdownLines(state.ui)
            }
        }
    }
}

@Composable
private fun SaleCountdownLines(state: SaleCountdownUi) {
    Column {
        Text(
            text = state.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = state.headline,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = state.detail,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}
