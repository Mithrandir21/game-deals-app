package pm.bam.gamedeals.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Column
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pm.bam.gamedeals.R
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.shortcuts.AppShortcut

/** Registered in the manifest; the system talks to the widget through this receiver. */
class WaitlistDealsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaitlistDealsWidget()
}

/**
 * Home-screen widget: the games on the user's IsThereAnyDeal waitlist that are on sale right now, biggest discount
 * first, with an "all-time low" marker. It reads the waitlist snapshot the app already keeps (prices from one batched
 * ITAD call) and refreshes it only when it is older than [STALE_AFTER_MILLIS] or priced for another region, so the
 * widget adds little ITAD traffic. Tapping a game opens its page; tapping elsewhere opens the Waitlist. The app also
 * redraws it whenever the waitlist snapshot or sign-in changes (GameDealsApplication).
 */
class WaitlistDealsWidget : GlanceAppWidget(), KoinComponent {

    private val authTokenStore: AuthTokenStore by inject()
    private val waitlistRepository: WaitlistRepository by inject()
    private val regionRepository: RegionRepository by inject()
    private val clock: Clock by inject()
    private val logger: Logger by inject()

    // Exact: recompose for each size the launcher asks for, so LocalSize picks how many games fit.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent {
            GlanceTheme {
                WaitlistDealsContent(state)
            }
        }
    }

    private suspend fun load(context: Context): WaitlistDealsState {
        if (authTokenStore.observeAuthState().first() !is AuthState.LoggedIn) return WaitlistDealsState.SignedOut
        val region = regionRepository.getSelectedCountryCode()
        var snapshot = waitlistRepository.observeWaitlistDisplay().first()
        if (snapshot == null || snapshot.isStale(region)) {
            try {
                waitlistRepository.refreshWaitlistDisplay()
                snapshot = waitlistRepository.observeWaitlistDisplay().first()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                error(logger, t) { "Waitlist deals widget couldn't refresh the waitlist." }
            }
        }
        // A failed refresh with a usable snapshot still shows it; prices for another region are never shown.
        if (snapshot == null) return WaitlistDealsState.Error
        // The snapshot isn't refetched when a game is removed; drop rows that left the waitlist since (as the list screen does).
        val waitlisted = waitlistRepository.observeWaitlistIds().first()
        val items = snapshot.items.filter { it.gameId in waitlisted }
        if (items.isEmpty()) return WaitlistDealsState.EmptyWaitlist
        val onSale = if (snapshot.regionCode == region) items.onSale() else emptyList()
        return WaitlistDealsState.Loaded(onSale.take(WIDGET_MAX_ROWS).map { it.toWidgetDeal(context) })
    }

    private fun WaitlistDisplaySnapshot.isStale(region: String) =
        regionCode != region || clock.nowMillis() - refreshedAtEpochMs > STALE_AFTER_MILLIS

    private suspend fun WaitlistDisplayItem.toWidgetDeal(context: Context) = WidgetDeal(
        gameId = gameId,
        title = title,
        price = bestPriceDenominated.orEmpty(),
        regular = bestRegularDenominated?.takeIf { it != bestPriceDenominated },
        discountPercent = discountPercent ?: 0,
        shop = bestShopName,
        atHistoricalLow = isAtHistoricalLow,
        thumbnail = loadWidgetThumbnail(context, artwork.thumbnail),
    )

    private companion object {
        /** Matches the widget's system refresh period (res/xml/waitlist_deals_widget_info.xml). */
        const val STALE_AFTER_MILLIS = 3 * 60 * 60 * 1000L
    }
}

/** On-sale rows, biggest discount first; ties go to the all-time lows. */
internal fun List<WaitlistDisplayItem>.onSale(): List<WaitlistDisplayItem> =
    filter { it.hasDeal && (it.discountPercent ?: 0) > 0 }
        .sortedWith(compareByDescending<WaitlistDisplayItem> { it.discountPercent ?: 0 }.thenByDescending { it.isAtHistoricalLow })

internal sealed interface WaitlistDealsState {
    data object SignedOut : WaitlistDealsState

    /** The refresh failed and nothing is cached. */
    data object Error : WaitlistDealsState

    /** Signed in, but nothing is waitlisted. */
    data object EmptyWaitlist : WaitlistDealsState

    /** Empty when nothing on the waitlist is discounted right now. */
    data class Loaded(val deals: List<WidgetDeal>) : WaitlistDealsState
}

// Immutable: built once per widget update and never changed; the Bitmap is never mutated after decoding.
@Immutable
internal data class WidgetDeal(
    val gameId: String,
    val title: String,
    /** e.g. "$4.99". */
    val price: String,
    /** The usual price, e.g. "$19.99", or null when unknown or the same as [price]. */
    val regular: String?,
    val discountPercent: Int,
    val shop: String?,
    val atHistoricalLow: Boolean,
    val thumbnail: Bitmap?,
)

// region UI

@Composable
private fun WaitlistDealsContent(state: WaitlistDealsState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val rows = widgetRows(size)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(12.dp)
            // Signed out, the waitlist shortcut opens the sign-in sheet over the empty list.
            .clickable(actionStartActivity(AppShortcut.Waitlist.intent(context))),
    ) {
        WidgetHeader(R.string.widget_waitlist_deals_header, R.drawable.ic_widget_waitlist)
        Spacer(GlanceModifier.height(6.dp))
        when (state) {
            WaitlistDealsState.SignedOut -> WidgetMessage(R.string.widget_waitlist_deals_signed_out)
            WaitlistDealsState.Error -> WidgetMessage(R.string.widget_waitlist_deals_error)
            WaitlistDealsState.EmptyWaitlist -> WidgetMessage(R.string.widget_waitlist_deals_empty_waitlist)
            is WaitlistDealsState.Loaded -> when {
                state.deals.isEmpty() -> WidgetMessage(R.string.widget_waitlist_deals_none_on_sale)
                rows == 1 && size.width < WIDGET_NARROW_WIDTH -> SmallDeal(state.deals.first())
                // Own Column: Glance renders at most 10 children per container (see FreeGamesWidget).
                else -> Column {
                    state.deals.take(rows).forEach { deal -> DealRow(deal) }
                }
            }
        }
    }
}

@Composable
private fun SmallDeal(deal: WidgetDeal) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.clickable(actionStartActivity(AppShortcut.Game(deal.gameId).intent(context)))) {
        WidgetThumbnail(deal.thumbnail, GlanceModifier.fillMaxWidth().height(56.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = deal.title,
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        PriceLine(deal)
        if (deal.atHistoricalLow) LowestEver()
    }
}

@Composable
private fun DealRow(deal: WidgetDeal) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable(actionStartActivity(AppShortcut.Game(deal.gameId).intent(context))),
    ) {
        WidgetThumbnail(deal.thumbnail, GlanceModifier.size(width = 64.dp, height = 32.dp))
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(
                text = deal.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceLine(deal)
                if (deal.atHistoricalLow) {
                    Spacer(GlanceModifier.width(6.dp))
                    LowestEver()
                }
            }
        }
    }
}

/** "-75% $4.99 ~~$19.99~~ Steam" */
@Composable
private fun PriceLine(deal: WidgetDeal) {
    val context = LocalContext.current
    val style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = context.getString(R.string.widget_waitlist_deals_cut, deal.discountPercent),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(text = deal.price, maxLines = 1, style = style.copy(color = GlanceTheme.colors.onSurface))
        deal.regular?.let {
            Spacer(GlanceModifier.width(4.dp))
            Text(text = it, maxLines = 1, style = style.copy(textDecoration = TextDecoration.LineThrough))
        }
        deal.shop?.let {
            Spacer(GlanceModifier.width(4.dp))
            Text(text = it, maxLines = 1, style = style)
        }
    }
}

@Composable
private fun LowestEver() {
    Text(
        text = LocalContext.current.getString(R.string.widget_waitlist_deals_lowest_ever),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.tertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

// endregion
