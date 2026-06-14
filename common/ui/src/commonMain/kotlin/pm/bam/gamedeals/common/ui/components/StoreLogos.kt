package pm.bam.gamedeals.common.ui.components

import org.jetbrains.compose.resources.DrawableResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_battlenet
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_ea
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_epic
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamejolt
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gog
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_humble
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_itchio
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_microsoft
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_origin
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_steam
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_ubisoft
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_xbox

/**
 * Bundled brand logos for the common stores, keyed by the ITAD store name.
 *
 * ITAD's API serves no shop logos (`/service/shops/v1` returns only `id`+`title`), so we ship our own
 * monochrome marks (from Simple Icons) for the stores users see most; [StoreIcon] tints them to the
 * surrounding content colour (so they stay visible in both themes) and falls back to a neutral dot for
 * any store without a bundled logo. Matching is on a normalised name (lower-cased, non-alphanumerics
 * stripped) so ITAD's spelling/regional variants — "GOG.com", "Epic Game Store", "Humble Store",
 * "Battle.net" — still resolve.
 */
fun storeLogoFor(storeName: String): DrawableResource? = when (storeName.normalizeStoreName()) {
    "steam" -> Res.drawable.store_logo_steam
    "gog", "gogcom" -> Res.drawable.store_logo_gog
    "epic", "epicgames", "epicgamestore", "epicgamesstore" -> Res.drawable.store_logo_epic
    "humble", "humblestore", "humblebundle" -> Res.drawable.store_logo_humble
    "ubisoft", "ubisoftstore", "ubisoftconnect", "uplay" -> Res.drawable.store_logo_ubisoft
    "ea", "eaapp", "eastore", "electronicarts" -> Res.drawable.store_logo_ea
    "origin" -> Res.drawable.store_logo_origin
    "battlenet", "blizzard", "blizzardentertainment" -> Res.drawable.store_logo_battlenet
    "itch", "itchio" -> Res.drawable.store_logo_itchio
    "gamejolt" -> Res.drawable.store_logo_gamejolt
    "microsoft", "microsoftstore", "windowsstore", "msstore" -> Res.drawable.store_logo_microsoft
    "xbox", "xboxstore" -> Res.drawable.store_logo_xbox
    else -> null
}

private fun String.normalizeStoreName(): String =
    lowercase().filter { it.isLetterOrDigit() }
