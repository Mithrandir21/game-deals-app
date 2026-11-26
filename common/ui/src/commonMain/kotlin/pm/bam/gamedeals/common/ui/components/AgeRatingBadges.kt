package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.esrb_ao
import pm.bam.gamedeals.common.ui.generated.resources.esrb_e
import pm.bam.gamedeals.common.ui.generated.resources.esrb_e10
import pm.bam.gamedeals.common.ui.generated.resources.esrb_ec
import pm.bam.gamedeals.common.ui.generated.resources.esrb_m
import pm.bam.gamedeals.common.ui.generated.resources.esrb_t
import pm.bam.gamedeals.common.ui.generated.resources.pegi_12
import pm.bam.gamedeals.common.ui.generated.resources.pegi_16
import pm.bam.gamedeals.common.ui.generated.resources.pegi_18
import pm.bam.gamedeals.common.ui.generated.resources.pegi_3
import pm.bam.gamedeals.common.ui.generated.resources.pegi_7
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.IgdbGame

/** Default height of an age-rating badge — sized to read clearly without dominating a header (#199). */
val DefaultAgeRatingBadgeHeight: Dp = 40.dp

/**
 * ESRB/PEGI age ratings (IGDB `age_ratings`) shown as the official rating badges — bundled vector drawables
 * traced from Wikimedia Commons. Ratings we don't ship an asset for (e.g. ESRB "Rating Pending") are simply
 * omitted. Wraps if the row is narrow. Extracted from the game page so any surface can reuse it (#199).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AgeRatingsRow(
    ratings: List<IgdbGame.IgdbAgeRating>,
    modifier: Modifier = Modifier,
    badgeHeight: Dp = DefaultAgeRatingBadgeHeight,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.extraSmall),
    ) {
        ratings.forEach { rating ->
            val art = ageRatingArt(rating) ?: return@forEach
            val cd = when (rating.board) {
                IgdbGame.IgdbAgeRating.Board.ESRB -> "ESRB ${rating.code}"
                IgdbGame.IgdbAgeRating.Board.PEGI -> "PEGI ${rating.code}"
            }
            Image(
                painter = painterResource(art),
                contentDescription = cd,
                modifier = Modifier.height(badgeHeight),
            )
        }
    }
}

/** The bundled official badge for a rating, or null when we don't ship that one (e.g. ESRB Rating Pending). */
private fun ageRatingArt(rating: IgdbGame.IgdbAgeRating): DrawableResource? = when (rating.board) {
    IgdbGame.IgdbAgeRating.Board.ESRB -> when (rating.code) {
        "EC" -> Res.drawable.esrb_ec
        "E" -> Res.drawable.esrb_e
        "E10+" -> Res.drawable.esrb_e10
        "T" -> Res.drawable.esrb_t
        "M" -> Res.drawable.esrb_m
        "AO" -> Res.drawable.esrb_ao
        else -> null
    }
    IgdbGame.IgdbAgeRating.Board.PEGI -> when (rating.code) {
        "3" -> Res.drawable.pegi_3
        "7" -> Res.drawable.pegi_7
        "12" -> Res.drawable.pegi_12
        "16" -> Res.drawable.pegi_16
        "18" -> Res.drawable.pegi_18
        else -> null
    }
}
