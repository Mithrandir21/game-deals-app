package pm.bam.gamedeals.feature.giveaways.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.feature.giveaways.generated.resources.Res
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_date
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_popularity
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_sort_by_value
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_dlc
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_early_access
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_game
import pm.bam.gamedeals.feature.giveaways.generated.resources.giveaway_screen_filters_type_other

@Composable
internal fun GiveawayType.displayLabel(): String = stringResource(
    when (this) {
        GiveawayType.GAME -> Res.string.giveaway_screen_filters_type_game
        GiveawayType.DLC -> Res.string.giveaway_screen_filters_type_dlc
        GiveawayType.BETA -> Res.string.giveaway_screen_filters_type_early_access
        GiveawayType.OTHER -> Res.string.giveaway_screen_filters_type_other
    }
)

@Composable
internal fun GiveawaySortBy.displayLabel(): String = stringResource(
    when (this) {
        GiveawaySortBy.DATE -> Res.string.giveaway_screen_filters_sort_by_date
        GiveawaySortBy.VALUE -> Res.string.giveaway_screen_filters_sort_by_value
        GiveawaySortBy.POPULARITY -> Res.string.giveaway_screen_filters_sort_by_popularity
    }
)
