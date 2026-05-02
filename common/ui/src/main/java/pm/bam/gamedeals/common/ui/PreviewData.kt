package pm.bam.gamedeals.common.ui

import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import java.time.LocalDateTime

val PreviewStoreImages = Store.StoreImages("", "", "")
val PreviewStore = Store(1, "Store Name", true, PreviewStoreImages)
val PreviewDeal = Deal("dealId", "GameDeal Internal", "GameDeal", "", 1, 2, 1.2, "$1.2",2.4, "$2.4", false, 50.0, 97, "Positive", 92, "13", 1, 1, 1, 9.5, "Thumb")

val PreviewRelease = Release("Game Name", 123,  "Thumb")
val PreviewGiveaway = Giveaway(id = 123, title = "Giveaway Title", worthDenominated = "$100", worth = 100.00, thumbnail = "Thumb", image = "Image", description = "Description", instructions = "Instructions", openGiveawayUrl = "Open Giveaway URL", publishedDate = LocalDateTime.now(), type = GiveawayType.GAME, platforms = listOf(GiveawayPlatform.PC), endDate = "N/A", users = 3463, status = "Active", gamerpowerUrl = "Gamerpower URL", openGiveaway = "Open Giveaway")

val PreviewDealGameInfo = DealDetails.GameInfo(1, 123, "Game Name", 8870, 7.49, "$7.49", 39.99, "$39.99","Very Positive", 93, "1238", 97, "Link", "August 13, 2011", "Publisher", true, "Thumb")
val PreviewDealCheaperStore = DealDetails.CheaperStore("xyz", 1, 6.49, "$6.49", 29.99, "$29.99")
val PreviewDealCheapestPrice = DealDetails.CheapestPrice(6.49, "$6.49", "July 13, 2011")
val PreviewDealDetails = DealDetails(PreviewDealGameInfo, listOf(PreviewDealCheaperStore), PreviewDealCheapestPrice)

val PreviewGameDetailsInfo = GameDetails.GameInfo(title = "Game Title", steamAppID = null, thumb = "Thumb")
val PreviewGameCheapestPriceEver = GameDetails.GameCheapestPriceEver(19.99, "$19.99", date = "January 13, 2011")
val PreviewGameDeal = GameDetails.GameDeal(1, "123", 9.99, "$9.99", 29.99, "$29.99", 90)
val PreviewGameDetails = GameDetails(PreviewGameDetailsInfo, PreviewGameCheapestPriceEver, persistentListOf(PreviewGameDeal))

val PreviewGame = Game(1, 123, 7.49, "$7.49", "123", "Game Tile", "Game Internal Title", "Thumb")