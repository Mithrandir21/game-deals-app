package pm.bam.gamedeals.feature.home.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.SingleEventEffect
import pm.bam.gamedeals.common.ui.components.DealBadgeCluster
import pm.bam.gamedeals.common.ui.components.PriceBlock
import pm.bam.gamedeals.common.ui.components.StoreLabel
import pm.bam.gamedeals.common.ui.components.dealBadgeContentSuffix
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb
import pm.bam.gamedeals.common.ui.signals.isRare
import pm.bam.gamedeals.common.ui.signals.rememberGameSignals
import pm.bam.gamedeals.common.ui.signals.reviewText
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.hero
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_add
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_add_failed
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_added_count
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_back
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_failed
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_itad_busy
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_finished_body
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_finished_title
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_hint
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_home_body
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_home_title
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_loading
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_open
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_retry
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_sign_in
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_signed_out
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_skip
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_stamp_add
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_stamp_skip
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_title
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_undo
import pm.bam.gamedeals.feature.home.generated.resources.deal_swipe_undo_failed
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_hero_deal_description
import pm.bam.gamedeals.feature.home.ui.DealSwipeViewModel.Status
import kotlin.math.abs
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes

internal const val DEAL_SWIPE_CARD_TAG = "deal_swipe_card"

/** Home's entry to deal swipe, under the waitlist and collection counts (signed-in users only). */
@Composable
internal fun DealSwipeHomeCard(modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium)
            .clip(CardDefaults.shape)
            .clickable(role = Role.Button, onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Icon(Icons.Filled.Swipe, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                Text(
                    text = stringResource(Res.string.deal_swipe_home_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = stringResource(Res.string.deal_swipe_home_body), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
internal fun DealSwipeScreen(
    onBack: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    viewModel: DealSwipeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val descriptions by viewModel.descriptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SingleEventEffect(viewModel.events) { event ->
        when (event) {
            is DealSwipeViewModel.Event.AddFailed -> {
                val result = snackbarHostState.showSnackbar(
                    message = getString(Res.string.deal_swipe_add_failed, event.deal.title),
                    actionLabel = getString(Res.string.deal_swipe_retry),
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.onRetryAdd(event.deal)
            }
            is DealSwipeViewModel.Event.UndoFailed ->
                snackbarHostState.showSnackbar(getString(Res.string.deal_swipe_undo_failed, event.deal.title))
        }
    }

    DealSwipeContent(
        state = state,
        stores = stores,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = { deal -> goToGame(deal.gameID) },
        onSwipeRight = viewModel::onSwipeRight,
        onSwipeLeft = viewModel::onSwipeLeft,
        onUndo = viewModel::onUndo,
        onRetry = viewModel::onRetry,
        onSignIn = { SignInPromptController.request() },
        descriptions = descriptions,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DealSwipeContent(
    state: DealSwipeViewModel.State,
    stores: ImmutableMap<Int, Store>,
    onBack: () -> Unit,
    onOpen: (Deal) -> Unit,
    onSwipeRight: (Deal) -> Unit,
    onSwipeLeft: (Deal) -> Unit,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    /** Game id → a short description, for the cards that have one. */
    descriptions: ImmutableMap<String, String> = persistentMapOf(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.deal_swipe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.deal_swipe_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state.status) {
                Status.LOADING -> {
                    val loading = stringResource(Res.string.deal_swipe_loading)
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
                Status.FAILED -> SwipeMessage(
                    title = null,
                    body = stringResource(if (state.itadBusy) Res.string.deal_swipe_itad_busy else Res.string.deal_swipe_failed),
                    action = stringResource(Res.string.deal_swipe_retry),
                    onAction = onRetry,
                )
                Status.SIGNED_OUT -> SwipeMessage(
                    title = null,
                    body = stringResource(Res.string.deal_swipe_signed_out),
                    action = stringResource(Res.string.deal_swipe_sign_in),
                    onAction = onSignIn,
                )
                Status.FINISHED -> SwipeMessage(
                    title = stringResource(Res.string.deal_swipe_finished_title),
                    body = stringResource(Res.string.deal_swipe_finished_body),
                    note = state.addedCount.takeIf { it > 0 }?.let { pluralStringResource(Res.plurals.deal_swipe_added_count, it, it) },
                    action = stringResource(Res.string.deal_swipe_undo).takeIf { state.canUndo },
                    onAction = onUndo,
                    outlinedAction = true,
                )
                Status.READY -> SwipeDeck(
                    state = state,
                    stores = stores,
                    descriptions = descriptions,
                    onOpen = onOpen,
                    onSwipeRight = onSwipeRight,
                    onSwipeLeft = onSwipeLeft,
                    onUndo = onUndo,
                )
            }
        }
    }
}

@Composable
private fun SwipeDeck(
    state: DealSwipeViewModel.State,
    stores: ImmutableMap<Int, Store>,
    descriptions: ImmutableMap<String, String>,
    onOpen: (Deal) -> Unit,
    onSwipeRight: (Deal) -> Unit,
    onSwipeLeft: (Deal) -> Unit,
    onUndo: () -> Unit,
) {
    val cards = state.cards
    val top = cards.firstOrNull() ?: return
    // One signals call per page; the repository serves games it already has from memory.
    val signals = rememberGameSignals(cards.map { it.gameID })
    val topState = remember(top.dealID) { SwipeCardState() }
    val scope = rememberCoroutineScope()
    val swipe: (SwipeDirection) -> Unit = { direction ->
        scope.launch {
            topState.flyOut(direction)
            if (direction == SwipeDirection.Right) onSwipeRight(top) else onSwipeLeft(top)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(GameDealsCustomTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        Text(
            text = if (state.addedCount > 0) pluralStringResource(Res.plurals.deal_swipe_added_count, state.addedCount, state.addedCount)
            else stringResource(Res.string.deal_swipe_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The next card waits underneath, growing to full size as the top one is dragged away.
            cards.getOrNull(1)?.let { next ->
                SwipeDealCard(
                    deal = next,
                    store = stores[next.storeID],
                    signals = signals,
                    description = descriptions[next.gameID],
                    modifier = Modifier
                        .graphicsLayer {
                            val grow = abs(topState.progress)
                            scaleX = NEXT_CARD_SCALE + (1f - NEXT_CARD_SCALE) * grow
                            scaleY = scaleX
                        }
                        .clearAndSetSemantics { },
                )
            }
            SwipeableCard(
                deal = top,
                state = topState,
                onSwiped = { direction -> if (direction == SwipeDirection.Right) onSwipeRight(top) else onSwipeLeft(top) },
                onOpen = { onOpen(top) },
                onAdd = { swipe(SwipeDirection.Right) },
                onSkip = { swipe(SwipeDirection.Left) },
            ) {
                SwipeDealCard(deal = top, store = stores[top.storeID], signals = signals, description = descriptions[top.gameID])
            }
        }
        SwipeButtons(
            canUndo = state.canUndo,
            onSkip = { swipe(SwipeDirection.Left) },
            onUndo = onUndo,
            onAdd = { swipe(SwipeDirection.Right) },
        )
    }
}

internal enum class SwipeDirection(val sign: Float) { Left(-1f), Right(1f) }

private const val NEXT_CARD_SCALE = 0.94f

/** How far across the card's width a drag must go to count as a swipe. */
private const val SWIPE_THRESHOLD = 0.3f

/** The card leans this many degrees at a full width's drag. */
private const val SWIPE_MAX_TILT = 12f

/** Where the top card is while it's dragged or thrown off. */
@Stable
internal class SwipeCardState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var widthPx by mutableFloatStateOf(0f)

    val thresholdPx: Float get() = widthPx * SWIPE_THRESHOLD

    /** -1 (dragged far enough to skip) through 0 (resting) to 1 (far enough to add). */
    val progress: Float get() = if (thresholdPx <= 0f) 0f else (offset.value.x / thresholdPx).coerceIn(-1f, 1f)

    suspend fun flyOut(direction: SwipeDirection) {
        offset.animateTo(
            Offset(direction.sign * widthPx * 1.5f, offset.value.y),
            tween(durationMillis = 200, easing = FastOutLinearInEasing),
        )
    }

    suspend fun settle() {
        offset.animateTo(Offset.Zero, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
}

@Composable
private fun SwipeableCard(
    deal: Deal,
    state: SwipeCardState,
    onSwiped: (SwipeDirection) -> Unit,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onSkip: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val flingPx = with(LocalDensity.current) { 1000.dp.toPx() }
    val addLabel = stringResource(Res.string.deal_swipe_add)
    val skipLabel = stringResource(Res.string.deal_swipe_skip)
    val openLabel = stringResource(Res.string.deal_swipe_open)
    val description = stringResource(
        Res.string.home_screen_hero_deal_description, deal.title, deal.normalPriceDenominated, deal.salePriceDenominated,
    ) + dealBadgeContentSuffix(
        isNewHistoricalLow = deal.isNewHistoricalLow,
        isLowestEver = deal.isLowestEver,
        isStoreLow = deal.isStoreLow,
        hasVoucher = deal.hasVoucher,
    )

    Box(
        modifier = Modifier
            .widthIn(max = CARD_MAX_WIDTH)
            .fillMaxWidth()
            .onSizeChanged { state.widthPx = it.width.toFloat() }
            // Outside the moving layer, so the drag is measured in the screen's frame, not the card's.
            .pointerInput(state) {
                val velocity = VelocityTracker()
                var armed = false
                detectDragGestures(
                    onDragStart = {
                        velocity.resetTracking()
                        armed = false
                    },
                    onDragEnd = {
                        val vx = velocity.calculateVelocity().x
                        val x = state.offset.value.x
                        val direction = when {
                            abs(vx) > flingPx -> if (vx > 0) SwipeDirection.Right else SwipeDirection.Left
                            x > state.thresholdPx -> SwipeDirection.Right
                            x < -state.thresholdPx -> SwipeDirection.Left
                            else -> null
                        }
                        scope.launch {
                            if (direction == null) {
                                state.settle()
                            } else {
                                state.flyOut(direction)
                                onSwiped(direction)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { state.settle() } },
                ) { change, drag ->
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val next = state.offset.value + drag
                    scope.launch { state.offset.snapTo(next) }
                    // A tick as the drag crosses the line where letting go would swipe.
                    val past = abs(next.x) > state.thresholdPx
                    if (past != armed) {
                        armed = past
                        if (past) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                }
            }
            .graphicsLayer {
                translationX = state.offset.value.x
                translationY = state.offset.value.y
                rotationZ = if (state.widthPx > 0f) state.offset.value.x / state.widthPx * SWIPE_MAX_TILT else 0f
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(
                    CustomAccessibilityAction(addLabel) { onAdd(); true },
                    CustomAccessibilityAction(skipLabel) { onSkip(); true },
                )
            }
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onOpen)
            .testTag(DEAL_SWIPE_CARD_TAG),
    ) {
        content()
        val progress = state.progress
        if (progress > STAMP_MIN) {
            SwipeStamp(stringResource(Res.string.deal_swipe_stamp_add), MaterialTheme.colorScheme.primary, progress, Alignment.TopStart, -STAMP_TILT)
        } else if (progress < -STAMP_MIN) {
            SwipeStamp(stringResource(Res.string.deal_swipe_stamp_skip), MaterialTheme.colorScheme.error, -progress, Alignment.TopEnd, STAMP_TILT)
        }
    }
}

private val CARD_MAX_WIDTH = 420.dp
private const val STAMP_MIN = 0.05f
private const val STAMP_TILT = 12f

/** "Waitlist" or "Skip", fading in over the art as the card is dragged toward it. */
@Composable
private fun BoxScope.SwipeStamp(text: String, color: Color, strength: Float, alignment: Alignment, tilt: Float) {
    Surface(
        modifier = Modifier
            .align(alignment)
            .padding(GameDealsCustomTheme.spacing.large)
            .graphicsLayer {
                alpha = strength
                rotationZ = tilt
            }
            .clearAndSetSemantics { },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        border = BorderStroke(3.dp, color),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.extraSmall),
        )
    }
}

/** A deal as a swipe card: wide art with the deal's badges, then the title, price, store and review score. */
@Composable
private fun SwipeDealCard(
    deal: Deal,
    store: Store?,
    signals: Map<String, GameSignals>,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            AsyncImage(
                model = deal.artwork.hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                error = painterResource(CommonRes.drawable.videogame_thumb),
                modifier = Modifier.fillMaxSize(),
            )
            DealBadgeCluster(
                discountPercent = deal.savings.roundToInt(),
                hasVoucher = deal.hasVoucher,
                isNewHistoricalLow = deal.isNewHistoricalLow,
                isLowestEver = deal.isLowestEver,
                isStoreLow = deal.isStoreLow,
                onArt = true,
                isRare = signals.isRare(deal.gameID, deal.savings.roundToInt()),
                modifier = Modifier.align(Alignment.BottomStart).padding(GameDealsCustomTheme.spacing.small),
            )
        }
        // Takes what height is left under the art, so on a short screen the description is cut, not the price.
        Column(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(GameDealsCustomTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            Text(
                text = deal.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            PriceBlock(
                salePrice = deal.salePriceDenominated,
                regularPrice = deal.normalPriceDenominated,
                salePriceStyle = MaterialTheme.typography.headlineMedium,
                horizontalAlignment = Alignment.Start,
            )
            store?.let { StoreLabel(storeName = it.storeName, iconUrl = it.iconUrl) }
            signals.reviewText(deal.gameID)?.let { review ->
                Text(review, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwipeGameDetails(signals[deal.gameID], description)
        }
    }
}

/** What the game is: "Developer · Year", up to three specific tags, and a short description. Each part only when known. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SwipeGameDetails(signals: GameSignals?, description: String?) {
    val maker = listOfNotNull(signals?.developer, signals?.releaseYear?.toString()).joinToString(" · ")
    if (maker.isNotEmpty()) {
        Text(maker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val tags = signals?.specificTags().orEmpty()
    if (tags.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        ) {
            tags.forEach { tag ->
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = GameDealsCustomTheme.spacing.small, vertical = 4.dp),
                    )
                }
            }
        }
    }
    if (description != null) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = DESCRIPTION_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private const val DESCRIPTION_LINES = 3

@Composable
private fun SwipeButtons(canUndo: Boolean, onSkip: () -> Unit, onUndo: () -> Unit, onAdd: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalIconButton(onClick = onSkip, modifier = Modifier.size(BIG_BUTTON)) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.deal_swipe_skip))
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(Res.string.deal_swipe_undo))
        }
        FilledIconButton(onClick = onAdd, modifier = Modifier.size(BIG_BUTTON)) {
            Icon(Icons.Filled.BookmarkAdd, contentDescription = stringResource(Res.string.deal_swipe_add))
        }
    }
}

private val BIG_BUTTON = 64.dp

@Composable
private fun SwipeMessage(
    title: String?,
    body: String,
    action: String?,
    onAction: () -> Unit,
    note: String? = null,
    outlinedAction: Boolean = false,
) {
    Column(
        modifier = Modifier.widthIn(max = CARD_MAX_WIDTH).padding(GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        title?.let {
            Text(it, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        }
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center) }
        action?.let {
            if (outlinedAction) OutlinedButton(onClick = onAction) { Text(it) } else Button(onClick = onAction) { Text(it) }
        }
    }
}
