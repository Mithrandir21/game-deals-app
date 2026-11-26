package pm.bam.gamedeals.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap

// Shared pieces of the home-screen widgets (FreeGamesWidget, WaitlistDealsWidget).

/** Most game rows any widget shows; also how many thumbnails a widget downloads. */
internal const val WIDGET_MAX_ROWS = 7

/** Narrower than this (about 2 cells) a widget shows one game large instead of rows. */
internal val WIDGET_NARROW_WIDTH = 180.dp

/** Padding plus the header. */
private val CHROME_HEIGHT = 48.dp

/** One game row: a 32 dp thumbnail beside two lines of text, plus the gap below it. */
private val ROW_HEIGHT = 40.dp

/** How many game rows fit a widget of [size]; 1 for a narrow widget. */
internal fun widgetRows(size: DpSize): Int =
    if (size.width < WIDGET_NARROW_WIDTH) 1 else ((size.height - CHROME_HEIGHT) / ROW_HEIGHT).toInt().coerceIn(1, WIDGET_MAX_ROWS)

private const val THUMBNAIL_WIDTH_PX = 240
private const val THUMBNAIL_HEIGHT_PX = 112

/** Downloads a small thumbnail. Widgets live in another process with a tight bitmap budget, so keep it small. */
internal suspend fun loadWidgetThumbnail(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(THUMBNAIL_WIDTH_PX, THUMBNAIL_HEIGHT_PX)
        .allowHardware(false) // RemoteViews can't carry hardware bitmaps.
        .build()
    return (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}

@Composable
internal fun WidgetHeader(@StringRes text: Int, icon: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(16.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = LocalContext.current.getString(text),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
internal fun WidgetMessage(@StringRes text: Int) {
    Text(
        text = LocalContext.current.getString(text),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
    )
}

@Composable
internal fun WidgetThumbnail(bitmap: Bitmap?, modifier: GlanceModifier) {
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
