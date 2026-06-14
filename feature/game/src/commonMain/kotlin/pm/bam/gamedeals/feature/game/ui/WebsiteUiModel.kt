package pm.bam.gamedeals.feature.game.ui

import pm.bam.gamedeals.domain.models.IgdbGame

/**
 * UI model for an external website link on the Game Page (epic #291) — pairs the IGDB website with a
 * resolved favicon reference (URL + stable cache key) so the link chip can show the site's icon.
 */
internal data class WebsiteUiModel(
    val url: String,
    val category: IgdbGame.IgdbWebsite.Category,
    val faviconUrl: String?,
    val faviconCacheKey: String?,
)
