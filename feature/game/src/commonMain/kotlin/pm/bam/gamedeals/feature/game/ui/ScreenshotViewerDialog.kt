@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import net.engawapg.lib.zoomable.ScrollGesturePropagation
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_image_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_viewer_close
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_viewer_page_indicator
import pm.bam.gamedeals.feature.game.generated.resources.game_details_screenshot_viewer_page_indicator_cd

@Composable
internal fun ScreenshotViewerDialog(
    screenshotImageIds: ImmutableList<String>,
    gameName: String,
    initialPage: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { screenshotImageIds.size }
    val zoomState = rememberZoomState()

    LaunchedEffect(pagerState.currentPage) { zoomState.reset() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val imageId = screenshotImageIds[page]
                AsyncImage(
                    model = igdbImageUrl(imageId, IgdbImageSize.ScreenshotHuge),
                    contentDescription = stringResource(Res.string.game_details_screenshot_image_cd, gameName, page + 1),
                    contentScale = ContentScale.Fit,
                    onSuccess = { state -> zoomState.setContentSize(state.painter.intrinsicSize) },
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = zoomState,
                            scrollGesturePropagation = ScrollGesturePropagation.NotZoomed,
                        ),
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(GameDealsCustomTheme.spacing.medium)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.game_details_screenshot_viewer_close),
                    tint = Color.White,
                )
            }

            if (screenshotImageIds.size > 1) {
                val pageIndicatorCd = stringResource(
                    Res.string.game_details_screenshot_viewer_page_indicator_cd,
                    pagerState.currentPage + 1,
                    screenshotImageIds.size,
                )
                Text(
                    text = stringResource(
                        Res.string.game_details_screenshot_viewer_page_indicator,
                        pagerState.currentPage + 1,
                        screenshotImageIds.size,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(GameDealsCustomTheme.spacing.large)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(horizontal = GameDealsCustomTheme.spacing.medium, vertical = GameDealsCustomTheme.spacing.extraSmall)
                        .semantics {
                            contentDescription = pageIndicatorCd
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
        }
    }
}
