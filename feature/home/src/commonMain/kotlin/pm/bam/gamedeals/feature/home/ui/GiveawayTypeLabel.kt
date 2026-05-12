package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.feature.home.generated.resources.Res
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_type_dlc
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_type_early_access
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_type_game
import pm.bam.gamedeals.feature.home.generated.resources.home_screen_giveaway_type_other

@Composable
internal fun GiveawayType.displayLabel(): String = stringResource(
    when (this) {
        GiveawayType.GAME -> Res.string.home_screen_giveaway_type_game
        GiveawayType.DLC -> Res.string.home_screen_giveaway_type_dlc
        GiveawayType.BETA -> Res.string.home_screen_giveaway_type_early_access
        GiveawayType.OTHER -> Res.string.home_screen_giveaway_type_other
    }
)
