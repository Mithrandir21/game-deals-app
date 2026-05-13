package pm.bam.gamedeals.common.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
@Suppress("UNUSED_PARAMETER")
actual fun GameDealsTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean, // No Material You / dynamic colours on iOS; parameter accepted for parity with the Android signature.
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtendedSpacing provides CustomSpaces()) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
            typography = Typography,
            content = content,
        )
    }
}
