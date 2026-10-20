package pm.bam.gamedeals.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.R
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayPlatformSelection
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.GiveawayTypeSelection
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.shortcuts.AppShortcut
import pm.bam.gamedeals.shortcuts.EXTRA_APP_SHORTCUT
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Registered in the manifest; the system talks to the widget through this receiver. */
class FreeGamesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FreeGamesWidget()
}

/**
 * Home-screen widget: PC games that are free to keep right now, most popular first — the Android twin of the iOS
 * Free Games widget (`iosApp/GameDealsWidget`). Unlike iOS it reads the app's own giveaways cache through
 * [GiveawaysRepository], refreshing it when stale, so it shows the same list as the Giveaways tab. Tapping it opens
 * that tab. The system refreshes it every 3 hours (`res/xml/free_games_widget_info.xml`).
 */
class FreeGamesWidget : GlanceAppWidget(), KoinComponent {

    private val giveawaysRepository: GiveawaysRepository by inject()
    private val logger: Logger by inject()

    // Exact: recompose for each size the launcher asks for, so LocalSize picks how many games fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent {
            GlanceTheme {
                FreeGamesContent(state)
            }
        }
    }

    private suspend fun load(context: Context): FreeGamesState {
        val refreshFailed = try {
            giveawaysRepository.refreshGiveaways()
            false
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            error(logger, t) { "Free Games widget couldn't refresh giveaways." }
            true
        }
        val giveaways = giveawaysRepository.observeGiveaways(PC_GAMES_BY_POPULARITY).first().take(MAX_ROWS)
        // A failed refresh with a usable cache still shows the cache.
        if (giveaways.isEmpty() && refreshFailed) return FreeGamesState.Error
        return FreeGamesState.Loaded(giveaways.map { it.toWidgetGame(context) })
    }

    private suspend fun Giveaway.toWidgetGame(context: Context) = WidgetGame(
        id = id,
        title = title.removeSuffix(" Giveaway"),
        worth = worthDenominated?.takeIf { it.isNotBlank() && it != "N/A" },
        endsLabel = endDate?.let(::relativeEnd)?.let { context.getString(R.string.widget_free_games_ends, it) },
        thumbnail = thumbnail(context, thumbnail),
    )

    /** Downloads a small thumbnail. Widgets live in another process with a tight bitmap budget, so keep it small. */
    private suspend fun thumbnail(context: Context, url: String): Bitmap? {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(THUMBNAIL_WIDTH_PX, THUMBNAIL_HEIGHT_PX)
            .allowHardware(false) // RemoteViews can't carry hardware bitmaps.
            .build()
        return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
    }

    private companion object {
        const val THUMBNAIL_WIDTH_PX = 240
        const val THUMBNAIL_HEIGHT_PX = 112

        val PC_GAMES_BY_POPULARITY = GiveawaySearchParameters(
            platforms = GiveawayPlatform.entries.map { GiveawayPlatformSelection(it, it == GiveawayPlatform.PC) }.toImmutableList(),
            types = GiveawayType.entries.map { GiveawayTypeSelection(it, it == GiveawayType.GAME) }.toImmutableList(),
            sortBy = GiveawaySortBy.POPULARITY,
        )
    }
}

internal sealed interface FreeGamesState {
    /** The refresh failed and nothing is cached. */
    data object Error : FreeGamesState

    /** Empty when it worked but nothing is free. */
    data class Loaded(val games: List<WidgetGame>) : FreeGamesState
}

internal data class WidgetGame(
    val id: Int,
    val title: String,
    /** The usual price, e.g. "$19.99", or null when GamerPower reports none. */
    val worth: String?,
    /** e.g. "ends in 4 days", or null when the giveaway has no end date. */
    val endsLabel: String?,
    val thumbnail: Bitmap?,
)

private val END_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/**
 * GamerPower end dates are UTC "yyyy-MM-dd HH:mm:ss", or "N/A" (no end date) → e.g. "in 4 days"; null when there is
 * no usable date. Fixed at load time, which is fine at a 3-hour refresh.
 */
internal fun relativeEnd(endDate: String, nowMillis: Long = System.currentTimeMillis()): String? {
    val endMillis = runCatching {
        LocalDateTime.parse(endDate, END_DATE_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull() ?: return null
    if (endMillis <= nowMillis) return null
    return DateUtils.getRelativeTimeSpanString(endMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString()
}

// region UI

private const val MAX_ROWS = 7
private val NARROW_WIDTH = 180.dp
/** Padding plus the "Free to keep" header. */
private val CHROME_HEIGHT = 48.dp
/** One game row: a 32 dp thumbnail beside two lines of text, plus the gap below it. */
private val ROW_HEIGHT = 40.dp

@Composable
private fun FreeGamesContent(state: FreeGamesState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val openGiveaways = Intent(context, MainActivity::class.java).putExtra(EXTRA_APP_SHORTCUT, AppShortcut.GIVEAWAYS.key)
    // A narrow widget (about 2 cells wide) shows one game large; otherwise as many rows as the height fits.
    val rows = if (size.width < NARROW_WIDTH) 1 else ((size.height - CHROME_HEIGHT) / ROW_HEIGHT).toInt().coerceIn(1, MAX_ROWS)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity(openGiveaways)),
    ) {
        Header()
        Spacer(GlanceModifier.height(6.dp))
        when (state) {
            FreeGamesState.Error -> Message(LocalContext.current.getString(R.string.widget_free_games_error))
            is FreeGamesState.Loaded -> when {
                state.games.isEmpty() -> Message(LocalContext.current.getString(R.string.widget_free_games_empty))
                rows == 1 && size.width < NARROW_WIDTH -> SmallGame(state.games.first())
                // Own Column, each row padded rather than followed by a Spacer: Glance renders at most 10 children
                // per container, and header + spacer + seven rows with spacers would silently drop the last ones.
                else -> Column {
                    state.games.take(rows).forEach { game -> GameRow(game) }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_gift),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = LocalContext.current.getString(R.string.widget_free_games_header),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Message(text: String) {
    Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
}

@Composable
private fun SmallGame(game: WidgetGame) {
    Column {
        Thumbnail(game.thumbnail, GlanceModifier.fillMaxWidth().height(56.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = game.title,
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Details(game)
    }
}

@Composable
private fun GameRow(game: WidgetGame) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Thumbnail(game.thumbnail, GlanceModifier.size(width = 64.dp, height = 32.dp))
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(
                text = game.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Details(game)
        }
    }
}

@Composable
private fun Details(game: WidgetGame) {
    val style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
    Row {
        game.worth?.let { Text(text = it, maxLines = 1, style = style.copy(textDecoration = TextDecoration.LineThrough)) }
        if (game.worth != null && game.endsLabel != null) Spacer(GlanceModifier.width(4.dp))
        game.endsLabel?.let { Text(text = it, maxLines = 1, style = style) }
    }
}

@Composable
private fun Thumbnail(bitmap: Bitmap?, modifier: GlanceModifier) {
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.cornerRadius(4.dp),
        )
    } else {
        Box(modifier = modifier.cornerRadius(4.dp).background(GlanceTheme.colors.surfaceVariant)) {}
    }
}

// endregion
