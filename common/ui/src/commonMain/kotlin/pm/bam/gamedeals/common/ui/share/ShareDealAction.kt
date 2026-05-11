package pm.bam.gamedeals.common.ui.share

import androidx.compose.runtime.Composable

@Composable
expect fun rememberShareDealAction(): (text: String) -> Unit
