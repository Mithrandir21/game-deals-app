package pm.bam.gamedeals.testing.fixtures

import pm.bam.gamedeals.domain.models.FavouriteGame

fun favouriteGame(
    gameID: String = "1",
    title: String = "Test Game",
    thumb: String = "thumb",
    dateAddedMs: Long = 0L,
) = FavouriteGame(
    gameID = gameID,
    title = title,
    thumb = thumb,
    dateAddedMs = dateAddedMs,
)
