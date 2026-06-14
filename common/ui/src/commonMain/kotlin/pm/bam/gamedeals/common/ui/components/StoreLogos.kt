package pm.bam.gamedeals.common.ui.components

import org.jetbrains.compose.resources.DrawableResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_allyouplay
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_blizzard
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_dlgamer
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_dreamgame
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_ea
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_epic
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_etailmarket
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_fanatical
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_fireflower
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamebillet
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamejolt
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamersgate
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamesload
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamesplanet
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gamesporium
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_gog
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_greenmangaming
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_humble
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_indiegala
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_itchio
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_joybuggy
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_macgamestore
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_microsoft
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_newegg
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_nuuvem
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_planetplay
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_playsum
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_steam
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_twogame
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_ubisoft
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_wingamestore
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_xbox
import pm.bam.gamedeals.common.ui.generated.resources.store_logo_zoomplatform

/**
 * Bundled brand logos for the stores ITAD surfaces, keyed by the ITAD store name.
 *
 * ITAD's API serves no shop logos (`/service/shops/v1` returns only `id`+`title`), so we ship our own:
 * the stores' full-colour brand favicons. [StoreIcon] renders them on a white circular chip (so even
 * black-on-transparent marks stay legible on a dark surface) and falls back to a neutral dot for any
 * store without a bundled logo. Matching is on a normalised name (lower-cased, non-alphanumerics
 * stripped) so ITAD's spelling/regional variants — "GOG.com", "Epic Game Store", "GamesPlanet US",
 * "Battle.net" — resolve. Covers the full ITAD shop list as of 2026-06; the only entries left to the
 * dot fallback are ones whose domain exposes no real favicon.
 */
fun storeLogoFor(storeName: String): DrawableResource? = when (storeName.normalizeStoreName()) {
    "steam" -> Res.drawable.store_logo_steam
    "gog", "gogcom" -> Res.drawable.store_logo_gog
    "epic", "epicgames", "epicgamestore", "epicgamesstore" -> Res.drawable.store_logo_epic
    "humble", "humblestore", "humblebundle" -> Res.drawable.store_logo_humble
    "fanatical" -> Res.drawable.store_logo_fanatical
    "greenmangaming", "gmg" -> Res.drawable.store_logo_greenmangaming
    "gamersgate" -> Res.drawable.store_logo_gamersgate
    "indiegala", "indiegalastore" -> Res.drawable.store_logo_indiegala
    "gamesplanet", "gamesplanetus", "gamesplanetuk", "gamesplanetde", "gamesplanetfr" -> Res.drawable.store_logo_gamesplanet
    "ubisoft", "ubisoftstore", "ubisoftconnect", "uplay" -> Res.drawable.store_logo_ubisoft
    "ea", "eastore", "eaapp", "electronicarts", "origin" -> Res.drawable.store_logo_ea
    "blizzard", "battlenet", "battledotnet" -> Res.drawable.store_logo_blizzard
    "microsoft", "microsoftstore", "windowsstore", "msstore" -> Res.drawable.store_logo_microsoft
    "newegg" -> Res.drawable.store_logo_newegg
    "nuuvem" -> Res.drawable.store_logo_nuuvem
    "wingamestore" -> Res.drawable.store_logo_wingamestore
    "gamebillet" -> Res.drawable.store_logo_gamebillet
    "macgamestore" -> Res.drawable.store_logo_macgamestore
    "2game", "twogame" -> Res.drawable.store_logo_twogame
    "dlgamer" -> Res.drawable.store_logo_dlgamer
    "allyouplay" -> Res.drawable.store_logo_allyouplay
    "dreamgame" -> Res.drawable.store_logo_dreamgame
    "gamesload" -> Res.drawable.store_logo_gamesload
    "zoomplatform" -> Res.drawable.store_logo_zoomplatform
    "etailmarket" -> Res.drawable.store_logo_etailmarket
    "fireflower" -> Res.drawable.store_logo_fireflower
    "gamesporium" -> Res.drawable.store_logo_gamesporium
    "joybuggy" -> Res.drawable.store_logo_joybuggy
    "planetplay" -> Res.drawable.store_logo_planetplay
    "playsum" -> Res.drawable.store_logo_playsum
    "itch", "itchio" -> Res.drawable.store_logo_itchio
    "gamejolt" -> Res.drawable.store_logo_gamejolt
    "xbox", "xboxstore" -> Res.drawable.store_logo_xbox
    else -> null
}

private fun String.normalizeStoreName(): String =
    lowercase().filter { it.isLetterOrDigit() }
