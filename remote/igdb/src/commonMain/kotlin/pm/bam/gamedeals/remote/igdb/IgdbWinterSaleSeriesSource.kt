package pm.bam.gamedeals.remote.igdb

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.IGDB_EROTIC_THEME_ID
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleSeriesGame
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleSeriesSource
import pm.bam.gamedeals.remote.igdb.api.IgdbGamesApi
import pm.bam.gamedeals.remote.igdb.mappers.toIgdbGame

internal class IgdbWinterSaleSeriesSource(private val api: IgdbGamesApi) : WinterSaleSeriesSource {
    override suspend fun fetch(franchiseId: Long, limit: Int): List<WinterSaleSeriesGame> =
        api.fetchFranchiseGames(franchiseId, limit).getOrThrow().filter { it.isGameProduct() }.map { game ->
            WinterSaleSeriesGame(
                igdbId = game.id,
                title = game.name,
                steamAppId = game.toIgdbGame().steamAppId,
                erotic = game.themes?.any { it.id == IGDB_EROTIC_THEME_ID },
                coverImageId = game.cover?.imageId,
            )
        }

    private fun pm.bam.gamedeals.remote.igdb.models.RemoteIgdbGame.isGameProduct(): Boolean {
        if (parentGame != null) return false
        if (category != null) return category in setOf(0, 4, 8, 9, 10, 11)
        return gameType?.type?.lowercase() in setOf("main game", "standalone expansion", "remake", "remaster", "expanded game", "port")
    }
}
