@file:JvmName("AndroidTheme")

package pm.bam.gamedeals.common.ui.theme

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun GameDealsTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean, // Dynamic color is available on Android 12+
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme
        else -> lightScheme
    }
    val view = LocalView.current
    val activity = LocalActivity.current
    // `activity` is null in non-Activity hosts such as @Preview renders on device,
    // ComposeViews used in service notifications, or dialogs hosted on Application
    // context. Skip status-bar styling rather than crashing with ClassCastException.
    if (!view.isInEditMode && activity != null) {
        LaunchedEffect(colorScheme.surface, darkTheme) {
            val window = activity.window
            // Match the status bar to the surface for the dense ITAD look (UI Improvements #253),
            // with icon contrast following the theme (dark icons on the light surface, light on dark).
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val extendedSpacing = CustomSpaces()

    CompositionLocalProvider(LocalExtendedSpacing provides extendedSpacing) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
