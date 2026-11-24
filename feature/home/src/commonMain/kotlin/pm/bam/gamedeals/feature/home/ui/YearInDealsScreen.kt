package pm.bam.gamedeals.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.share.captureContent
import pm.bam.gamedeals.common.ui.share.rememberShareCardCapture
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.models.portrait
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_back
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_biggest_drop
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_biggest_drop_label
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_card_heading
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_collected
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_empty
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_failed
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_footer
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_full_price
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_full_price_note
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_home_body
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_home_title
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_longest_wait
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_longest_wait_label
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_newest_label
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_retry
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_share
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_share_failed
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_title
import pm.bam.gamedeals.feature.home.generated.resources.year_in_deals_wins
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/** Home's entry to the year card: a tappable card in the app's green, shown in December and January. */
@Composable
internal fun YearInDealsHomeCard(year: Int, onOpen: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.small)
            .clip(CardDefaults.shape)
            .clickable(role = Role.Button, onClick = onOpen),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(GameDealsCustomTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                Text(
                    text = stringResource(Res.string.year_in_deals_home_title, year),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = stringResource(Res.string.year_in_deals_home_body), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YearInDealsScreen(
    onBack: () -> Unit,
    viewModel: YearInDealsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.year_in_deals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.year_in_deals_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val current = state) {
                YearInDealsViewModel.State.Loading -> CircularProgressIndicator(Modifier.padding(top = 96.dp))
                YearInDealsViewModel.State.Failed -> Message(
                    text = stringResource(Res.string.year_in_deals_failed),
                    action = stringResource(Res.string.year_in_deals_retry),
                    onAction = viewModel::retry,
                )
                is YearInDealsViewModel.State.Empty -> Message(text = stringResource(Res.string.year_in_deals_empty, current.year))
                is YearInDealsViewModel.State.Ready -> YearInDealsReady(current.year, shareText = viewModel.shareText(current.year))
            }
        }
    }
}

@Composable
private fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.widthIn(max = 480.dp).padding(GameDealsCustomTheme.spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun YearInDealsReady(year: YearInDeals, shareText: String) {
    val actions = LocalPlatformActions.current
    val capture = rememberShareCardCapture()
    val scope = rememberCoroutineScope()
    var shareFailed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .verticalScroll(rememberScrollState())
            .padding(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        YearInDealsCard(
            year = year,
            modifier = Modifier.fillMaxWidth().captureContent(capture, MaterialTheme.colorScheme.background),
        )
        if (year.fullPriceValue != null) {
            Text(
                text = stringResource(Res.string.year_in_deals_full_price_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val png = capture.capturePng()
                    shareFailed = png == null
                    if (png != null) actions.shareImage(png, shareText)
                }
            },
        ) { Text(stringResource(Res.string.year_in_deals_share)) }
        if (shareFailed) {
            Text(
                text = stringResource(Res.string.year_in_deals_share_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The card itself: what gets turned into the shared image. Everything on it must read on its own, without the
 * app around it, so it carries the year, the app's name and where to get it.
 */
@Composable
internal fun YearInDealsCard(year: YearInDeals, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
        ) {
            Text(
                text = stringResource(Res.string.year_in_deals_card_heading, year.year),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )

            val number = yearCardNumberStyle()
            val title = SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)

            year.collected?.let { collected ->
                if (collected.highlights.isNotEmpty()) {
                    // The most recently added first, so say so: the row isn't a "best of".
                    Labelled(stringResource(Res.string.year_in_deals_newest_label)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small)) {
                            collected.highlights.forEach { game ->
                                AsyncImage(
                                    model = game.artwork.portrait,
                                    contentDescription = game.title,
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(CommonRes.drawable.videogame_thumb),
                                    modifier = Modifier.weight(1f).aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            // Keep covers the same size when there are fewer than four.
                            repeat(4 - collected.highlights.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
                Stat(
                    highlighted(collected.count.toString() to number) {
                        pluralStringResource(Res.plurals.year_in_deals_collected, collected.count, *it)
                    },
                )
            }
            year.fullPriceValue?.let { value ->
                Stat(highlighted(value to number) { stringResource(Res.string.year_in_deals_full_price, *it) })
            }
            year.waitlistWins?.let { wins ->
                Stat(
                    highlighted(wins.count.toString() to number) {
                        pluralStringResource(Res.plurals.year_in_deals_wins, wins.count, *it)
                    },
                )
                wins.biggestDrop?.let { drop ->
                    Labelled(stringResource(Res.string.year_in_deals_biggest_drop_label)) {
                        Detail(
                            highlighted(drop.game.title to title, "${drop.cutPercent}%" to number, drop.lowDenominated to number) {
                                stringResource(Res.string.year_in_deals_biggest_drop, *it)
                            },
                        )
                    }
                }
            }
            year.longestWait?.let { wait ->
                Labelled(stringResource(Res.string.year_in_deals_longest_wait_label)) {
                    Detail(
                        highlighted(wait.game.title to title, wait.days.toString() to number) {
                            pluralStringResource(Res.plurals.year_in_deals_longest_wait, wait.days, *it)
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.year_in_deals_footer), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(ShareLinks.APP_URL.removePrefix("https://"), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun Stat(text: AnnotatedString) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Detail(text: AnnotatedString) {
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium)
        content()
    }
}

/**
 * The card's numbers in a warm orange, which stands apart from the green card and its text in both themes (at least
 * 4.5:1 against the card). The card is primaryContainer, so its brightness tells the theme apart, also when the card
 * is drawn off screen for sharing.
 */
@Composable
private fun yearCardNumberStyle(): SpanStyle {
    val darkCard = MaterialTheme.colorScheme.primaryContainer.luminance() < 0.5f
    return SpanStyle(color = if (darkCard) Color(0xFFFFB68B) else Color(0xFFB33A00), fontWeight = FontWeight.Bold)
}

/**
 * The string [resolve] makes, with each of [values] drawn in its style. [resolve] gets a marker per value to pass as
 * the string's arguments, in order; the markers are then swapped for the values. The text itself is unchanged, so
 * screen readers read the same sentence.
 */
@Composable
private fun highlighted(vararg values: Pair<String, SpanStyle>, resolve: @Composable (Array<String>) -> String): AnnotatedString {
    val template = resolve(Array(values.size) { "$MARKER_START$it$MARKER_END" })
    return buildAnnotatedString {
        var from = 0
        MARKER.findAll(template).forEach { match ->
            append(template.substring(from, match.range.first))
            val (value, style) = values[match.groupValues[1].toInt()]
            withStyle(style) { append(value) }
            from = match.range.last + 1
        }
        append(template.substring(from))
    }
}

// Private-use characters, which never appear in a translation.
private const val MARKER_START = '\uE000'
private const val MARKER_END = '\uE001'
private val MARKER = Regex("$MARKER_START(\\d+)$MARKER_END")
