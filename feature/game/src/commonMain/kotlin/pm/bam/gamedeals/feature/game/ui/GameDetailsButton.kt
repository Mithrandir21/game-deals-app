@file:Suppress("DEPRECATION")

package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_view_details

@Composable
internal fun GameDetailsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(Res.string.game_screen_view_details))
    }
}

@Preview
@Composable
private fun GameDetailsButton_Light_Preview() {
    GameDealsTheme {
        GameDetailsButton(onClick = {})
    }
}

@Preview
@Composable
private fun GameDetailsButton_Dark_Preview() {
    GameDealsTheme(darkTheme = true) {
        GameDetailsButton(onClick = {})
    }
}
