package pm.bam.gamedeals.domain.db.entities

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import java.time.LocalDateTime

internal class MappersTest {

    @Test
    fun `Deal toEntity copies every field and stamps expires`() {
        val deal = Deal(
            dealID = "deal-1",
            internalName = "INTERNAL",
            title = "Title",
            metacriticLink = "/m/link",
            storeID = 7,
            gameID = 42,
            salePriceValue = 9.99,
            salePriceDenominated = "$9.99",
            normalPriceValue = 19.99,
            normalPriceDenominated = "$19.99",
            isOnSale = true,
            savings = 50.0,
            metacriticScore = 88,
            steamRatingText = "Very Positive",
            steamRatingPercent = 95,
            steamRatingCount = "12345",
            steamAppID = 123,
            releaseDate = 1_700_000_000,
            lastChange = 1_700_500_000,
            dealRating = 8.5,
            thumb = "https://thumb",
        )

        val entity = deal.toEntity(expiresAt = 4_242L)

        assertEquals(deal.dealID, entity.dealID)
        assertEquals(deal.internalName, entity.internalName)
        assertEquals(deal.title, entity.title)
        assertEquals(deal.metacriticLink, entity.metacriticLink)
        assertEquals(deal.storeID, entity.storeID)
        assertEquals(deal.gameID, entity.gameID)
        assertEquals(deal.salePriceValue, entity.salePriceValue, 0.0)
        assertEquals(deal.salePriceDenominated, entity.salePriceDenominated)
        assertEquals(deal.normalPriceValue, entity.normalPriceValue, 0.0)
        assertEquals(deal.normalPriceDenominated, entity.normalPriceDenominated)
        assertEquals(deal.isOnSale, entity.isOnSale)
        assertEquals(deal.savings, entity.savings, 0.0)
        assertEquals(deal.metacriticScore, entity.metacriticScore)
        assertEquals(deal.steamRatingText, entity.steamRatingText)
        assertEquals(deal.steamRatingPercent, entity.steamRatingPercent)
        assertEquals(deal.steamRatingCount, entity.steamRatingCount)
        assertEquals(deal.steamAppID, entity.steamAppID)
        assertEquals(deal.releaseDate, entity.releaseDate)
        assertEquals(deal.lastChange, entity.lastChange)
        assertEquals(deal.dealRating, entity.dealRating, 0.0)
        assertEquals(deal.thumb, entity.thumb)
        assertEquals(4_242L, entity.expires)
    }

    @Test
    fun `Deal toEntity defaults expires to zero`() {
        val deal = sampleDeal()
        assertEquals(0L, deal.toEntity().expires)
    }

    @Test
    fun `Store toEntity copies every field and stamps expires`() {
        val store = Store(
            storeID = 3,
            storeName = "Steam",
            isActive = true,
            images = Store.StoreImages(banner = "/b", logo = "/l", icon = "/i"),
        )

        val entity = store.toEntity(expiresAt = 9_999L)

        assertEquals(store.storeID, entity.storeID)
        assertEquals(store.storeName, entity.storeName)
        assertEquals(store.isActive, entity.isActive)
        assertEquals(store.images, entity.images)
        assertEquals(9_999L, entity.expires)
    }

    @Test
    fun `Store toEntity defaults expires to zero`() {
        val store = Store(1, "n", true, Store.StoreImages("b", "l", "i"))
        assertEquals(0L, store.toEntity().expires)
    }

    @Test
    fun `Game toEntity copies every field`() {
        val game = Game(
            gameID = 11,
            steamAppID = 22,
            cheapestValue = 4.99,
            cheapestDenominated = "$4.99",
            cheapestDealID = "d-1",
            title = "Title",
            internalName = "INTERNAL",
            thumb = "https://thumb",
        )

        val entity = game.toEntity()

        assertEquals(game.gameID, entity.gameID)
        assertEquals(game.steamAppID, entity.steamAppID)
        assertEquals(game.cheapestValue, entity.cheapestValue, 0.0)
        assertEquals(game.cheapestDenominated, entity.cheapestDenominated)
        assertEquals(game.cheapestDealID, entity.cheapestDealID)
        assertEquals(game.title, entity.title)
        assertEquals(game.internalName, entity.internalName)
        assertEquals(game.thumb, entity.thumb)
    }

    @Test
    fun `Release toEntity copies every field`() {
        val release = Release(title = "Game", date = 1_700_000_000, image = "https://i")
        val entity = release.toEntity()

        assertEquals(release.title, entity.title)
        assertEquals(release.date, entity.date)
        assertEquals(release.image, entity.image)
    }

    @Test
    fun `Giveaway toEntity copies every field`() {
        val published = LocalDateTime.of(2026, 5, 1, 12, 30)
        val giveaway = Giveaway(
            id = 99,
            title = "Free Game",
            worthDenominated = "$10.00",
            worth = 10.0,
            thumbnail = "https://thumb",
            image = "https://image",
            description = "desc",
            instructions = "claim it",
            openGiveawayUrl = "https://open",
            publishedDate = published,
            type = GiveawayType.GAME,
            platforms = listOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM),
            endDate = "2026-06-01",
            users = 1234,
            status = "Active",
            gamerpowerUrl = "https://gp",
            openGiveaway = "https://og",
        )

        val entity = giveaway.toEntity()

        assertEquals(giveaway.id, entity.id)
        assertEquals(giveaway.title, entity.title)
        assertEquals(giveaway.worthDenominated, entity.worthDenominated)
        assertEquals(giveaway.worth, entity.worth)
        assertEquals(giveaway.thumbnail, entity.thumbnail)
        assertEquals(giveaway.image, entity.image)
        assertEquals(giveaway.description, entity.description)
        assertEquals(giveaway.instructions, entity.instructions)
        assertEquals(giveaway.openGiveawayUrl, entity.openGiveawayUrl)
        assertEquals(giveaway.publishedDate, entity.publishedDate)
        assertEquals(giveaway.type, entity.type)
        assertEquals(giveaway.platforms, entity.platforms)
        assertEquals(giveaway.endDate, entity.endDate)
        assertEquals(giveaway.users, entity.users)
        assertEquals(giveaway.status, entity.status)
        assertEquals(giveaway.gamerpowerUrl, entity.gamerpowerUrl)
        assertEquals(giveaway.openGiveaway, entity.openGiveaway)
    }

    private fun sampleDeal() = Deal(
        dealID = "x", internalName = "x", title = "x", metacriticLink = null,
        storeID = 0, gameID = 0,
        salePriceValue = 0.0, salePriceDenominated = "",
        normalPriceValue = 0.0, normalPriceDenominated = "",
        isOnSale = false, savings = 0.0, metacriticScore = 0,
        steamRatingText = null, steamRatingPercent = 0, steamRatingCount = "",
        steamAppID = null, releaseDate = 0, lastChange = 0,
        dealRating = 0.0, thumb = "",
    )
}
