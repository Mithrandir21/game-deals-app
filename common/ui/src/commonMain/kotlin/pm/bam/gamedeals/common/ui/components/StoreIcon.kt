package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.generated.resources.Res as CommonRes
import pm.bam.gamedeals.common.ui.generated.resources.videogame_thumb

/**
 * A single store identity icon, resolved with a three-step precedence so every deal surface renders
 * consistently:
 *
 *  1. a **bundled brand logo** ([storeLogoFor]) for the common stores — tinted to [color] so the
 *     monochrome mark stays legible in both light and dark themes;
 *  2. otherwise a remote **[iconUrl]** image (the source-neutral [pm.bam.gamedeals.domain.models.Store.iconUrl],
 *     blank under ITAD today but kept as a forward-compatible fallback) via Coil;
 *  3. otherwise a **neutral dot** tinted with [color].
 *
 * This mirrors the fallback [StoreLabel] established for ITAD deal rows; [StoreLabel] now delegates here.
 */
@Composable
fun StoreIcon(
    storeName: String,
    modifier: Modifier = Modifier,
    iconUrl: String? = null,
    iconSize: Dp = GameDealsCustomTheme.spacing.storeIcon,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentDescription: String? = null,
) {
    val logo = storeLogoFor(storeName)
    when {
        logo != null -> Image(
            painter = painterResource(logo),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(color),
            modifier = modifier.size(iconSize),
        )

        !iconUrl.isNullOrBlank() -> AsyncImage(
            model = iconUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            error = painterResource(CommonRes.drawable.videogame_thumb),
            modifier = modifier
                .size(iconSize)
                .clip(CircleShape),
        )

        else -> Box(
            modifier = modifier
                .size(GameDealsCustomTheme.spacing.storeDot)
                .clip(CircleShape)
                .background(color),
        )
    }
}
