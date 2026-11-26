package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_accuracy_body
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_accuracy_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_contact_body
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_contact_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_entry_body
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_entry_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_intro
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_provider_body
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_provider_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_sponsor_body
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_sponsor_heading
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_rules_title

/** Heading and body of each section of the rules, in reading order. */
private val RuleSections: List<Pair<StringResource, StringResource>> = listOf(
    Res.string.giveaway_rules_provider_heading to Res.string.giveaway_rules_provider_body,
    Res.string.giveaway_rules_entry_heading to Res.string.giveaway_rules_entry_body,
    Res.string.giveaway_rules_accuracy_heading to Res.string.giveaway_rules_accuracy_body,
    Res.string.giveaway_rules_sponsor_heading to Res.string.giveaway_rules_sponsor_body,
    Res.string.giveaway_rules_contact_heading to Res.string.giveaway_rules_contact_body,
)

/**
 * The official rules for the giveaways the app lists. App Store guideline 5.3.2 asks for rules that are
 * always available in the app and that say Apple is not a sponsor, so this is reachable from the
 * Giveaways tab's top row and from every giveaway's peek sheet. The app only lists third-party
 * giveaways, so the rules mostly point at each provider's own terms. Gated on [show], so it can sit
 * unconditionally in the composition (like [GiveawayPeekSheet]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GiveawayRulesSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (show) {
        ModalBottomSheet(
            onDismissRequest = { onDismiss() },
            sheetState = modalBottomSheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            GiveawayRulesContent()
        }
    }
}

/** The rules' scrollable body, independent of the [ModalBottomSheet] chrome. */
@Composable
internal fun GiveawayRulesContent() {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(Res.string.giveaway_rules_title),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Text(
                text = stringResource(Res.string.giveaway_rules_intro),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        items(RuleSections) { (sectionTitle, sectionBody) ->
            Column(verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall)) {
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(sectionTitle),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(sectionBody),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GiveawayRulesContentPreview() {
    GameDealsTheme {
        GiveawayRulesContent()
    }
}
