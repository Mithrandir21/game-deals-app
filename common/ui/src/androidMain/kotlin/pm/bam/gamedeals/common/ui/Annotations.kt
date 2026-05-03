package pm.bam.gamedeals.common.ui

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview


@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
annotation class DarkMode

@Preview(name = "Light Mode", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
annotation class LightMode

@LightMode
@DarkMode
annotation class DarkAndLightPreview


@Preview(name = "Phone Portrait", device = Devices.PHONE, showBackground = true, widthDp = 450, heightDp = 800)
annotation class PhonePortrait

@Preview(name = "Foldable Portrait", device = Devices.FOLDABLE, showBackground = true, widthDp = 750, heightDp = 900)
annotation class FoldablePortrait


@Preview(name = "Phone Landscape", device = Devices.PHONE, showBackground = true, widthDp = 800, heightDp = 480)
annotation class PhoneLandscape

@Preview(name = "Tablet Portrait", device = Devices.TABLET, showBackground = true, widthDp = 800, heightDp = 1200)
annotation class TabletPortrait


@Preview(name = "Foldable Landscape", device = Devices.FOLDABLE, showBackground = true, widthDp = 950, heightDp = 700)
annotation class FoldableLandscape

@Preview(name = "Tablet Landscape", device = Devices.TABLET, showBackground = true, widthDp = 1200, heightDp = 800)
annotation class TabletLandscape
