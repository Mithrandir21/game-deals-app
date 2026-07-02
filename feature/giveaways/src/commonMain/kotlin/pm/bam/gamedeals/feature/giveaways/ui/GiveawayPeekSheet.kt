package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import pm.bam.gamedeals.common.ui.PreviewGiveaway
import pm.bam.gamedeals.common.ui.components.StoreLabel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_description_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_go_to_giveaway
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_image
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_detail_instructions_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_free_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_list_item_worth_label
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_no_expiry
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * A quick-peek bottom sheet for a single [Giveaway], opened from a [GiveawayCard] tap. Shows the same
 * detail the old full-screen `GiveawayDetailScreen` did — hero art, claim button, countdown, platforms,
 * worth, description and instructions — without a screen transition. The whole [Giveaway] is passed in
 * from the list (it's already in memory), so there is no loading/error state to model. Gated on
 * [giveaway] being non-null, so it can sit unconditionally in the composition (like `GamePeekSheet`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiveawayPeekSheet(
    giveaway: Giveaway?,
    endDateMillis: Long?,
    onDismiss: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (giveaway != null) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            GiveawayPeekContent(
                giveaway = giveaway,
                endDateMillis = endDateMillis,
                goToWeb = goToWeb,
            )
        }
    }
}

/**
 * The peek's scrollable body, independent of the [ModalBottomSheet] chrome. Instructions can be long,
 * so this stays a [LazyColumn]. Records the [AnalyticsEvents.GIVEAWAY_OPENED] event on claim — the only
 * web link on this surface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GiveawayPeekContent(
    giveaway: Giveaway,
    endDateMillis: Long?,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    val analytics: Analytics = koinInject()
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        // The sheet has no TopAppBar, so the title leads the content.
        item {
            Text(
                modifier = Modifier.semantics { heading() },
                text = giveaway.title,
                style = MaterialTheme.typography.titleLarge,
            )
        }

        item {
            // Matches GamerPower's source image ratio (460×215 ≈ 2.14:1) so the full Steam-header art
            // shows uncropped; 460px wide is also its max resolution (can't be made sharper).
            AsyncImage(
                model = giveaway.image,
                contentDescription = stringResource(Res.string.giveaway_detail_image, giveaway.title),
                error = painterResource(CommonRes.drawable.videogame_thumb),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(460f / 215f)
                    .clip(RoundedCornerShape(GameDealsCustomTheme.spacing.small)),
            )
        }

        item {
            Button(
                onClick = {
                    analytics.capture(
                        AnalyticsEvents.GIVEAWAY_OPENED,
                        mapOf(
                            "giveaway_id" to giveaway.id,
                            "type" to giveaway.type.name,
                            "platforms" to giveaway.platforms.map { it.name },
                        ),
                    )
                    goToWeb(giveaway.openGiveawayUrl, giveaway.title)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.giveaway_detail_go_to_giveaway))
            }
        }

        item {
            endDateMillis?.let {
                GiveawayCountdown(expiryEpochMs = it, modifier = Modifier.fillMaxWidth())
            } ?: Text(
                text = stringResource(Res.string.giveaway_screen_no_expiry),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (giveaway.platforms.isNotEmpty()) {
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
                ) {
                    giveaway.platforms.forEach { platform ->
                        StoreLabel(storeName = platform.platformValue)
                    }
                }
            }
        }

        giveaway.worthDenominated?.let { worth ->
            item {
                Text(text = buildAnnotatedString {
                    withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle()) {
                        append(stringResource(Res.string.giveaway_screen_list_item_free_label))
                    }
                    append(" ")
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(stringResource(Res.string.giveaway_screen_list_item_worth_label, worth))
                    }
                })
            }
        }

        if (giveaway.description.isNotBlank()) {
            item {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.giveaway_detail_description_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = giveaway.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (giveaway.instructions.isNotBlank()) {
            item {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(Res.string.giveaway_detail_instructions_heading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                Text(
                    text = giveaway.instructions,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GiveawayPeekContentPreview() {
    GameDealsTheme {
        GiveawayPeekContent(
            giveaway = PreviewGiveaway.copy(
                title = "Tell Me Why - Chapters 1,2,3",
                worthDenominated = "$29.99",
                description = "Get Tell Me Why's three chapters free on Steam for a limited time.",
                instructions = "1. Sign in to Steam.\n2. Open the store page.\n3. Click \"Add to Account\".",
                platforms = persistentListOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM),
            ),
            endDateMillis = 4_102_444_800_000L,
            goToWeb = { _, _ -> },
        )
    }
}
