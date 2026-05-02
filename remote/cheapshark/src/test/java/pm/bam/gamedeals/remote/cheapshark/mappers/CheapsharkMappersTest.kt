package pm.bam.gamedeals.remote.cheapshark.mappers

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.DealsSortBy
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation

@OptIn(ExperimentalSerializationApi::class)
class CheapsharkMappersTest {

    private val currency: CurrencyTransformation = mockk {
        every { valueToDenominated(any()) } answers { "$${firstArg<Double>()}" }
    }

    private val dates: DateTimeFormatter = mockk {
        every { formatToISODate(any<Long>()) } answers { if (firstArg<Long>() == 0L) "1970-01-01" else "2020-01-01" }
        every { formatToISODateNullable(any<Long>()) } answers { if (firstArg<Long>() == 0L) "1970-01-01" else "2020-01-01" }
    }

    private val ctx = CheapsharkMapperContext(currency, dates)

    @Test
    fun `RemoteRelease toDomain copies fields`() {
        val result = RemoteRelease(date = 1234, title = "Game", image = "img.png").toDomain()
        assertEquals(1234, result.date)
        assertEquals("Game", result.title)
        assertEquals("img.png", result.image)
    }

    @Test
    fun `RemoteStore toDomain prefixes image base url and parses isActive`() {
        val store = RemoteStore(
            storeID = 1,
            storeName = "Steam",
            isActive = 1,
            images = RemoteStore.RemoteStoreImages(
                banner = "/b.png",
                logo = "/l.png",
                icon = "/i.png",
            ),
        ).toDomain()

        assertEquals(1, store.storeID)
        assertEquals("Steam", store.storeName)
        assertEquals(true, store.isActive)
        assertEquals("https://www.cheapshark.com/b.png", store.images.banner)
        assertEquals("https://www.cheapshark.com/l.png", store.images.logo)
        assertEquals("https://www.cheapshark.com/i.png", store.images.icon)
    }

    @Test
    fun `RemoteStore isActive 0 maps to false`() {
        val store = baseStore(isActive = 0).toDomain()
        assertEquals(false, store.isActive)
    }

    @Test(expected = Exception::class)
    fun `RemoteStore isActive 2 throws`() {
        baseStore(isActive = 2).toDomain()
    }

    @Test
    fun `SearchParameters toDomain encodes booleans as 0 1 ints`() {
        val q = SearchParameters(
            storeID = 5,
            pageNumber = 2,
            sortBy = DealsSortBy.SAVINGS,
            exact = true,
            aaa = false,
            steamworks = true,
            onSale = false,
        ).toDomain()

        assertEquals(5, q.storeID)
        assertEquals(2, q.pageNumber)
        assertEquals(RemoteDealsSortBy.SAVINGS, q.sortBy)
        assertEquals(1, q.exact)
        assertEquals(0, q.aaa)
        assertEquals(1, q.steamworks)
        assertEquals(0, q.onSale)
    }

    @Test
    fun `SearchParameters toDomain leaves null bool fields null`() {
        val q = SearchParameters(exact = null, aaa = null).toDomain()
        assertNull(q.exact)
        assertNull(q.aaa)
    }

    @Test
    fun `DealsSortBy toDomain covers every enum value`() {
        assertEquals(RemoteDealsSortBy.DEALRATING, DealsSortBy.DEALRATING.toDomain())
        assertEquals(RemoteDealsSortBy.TITLE, DealsSortBy.TITLE.toDomain())
        assertEquals(RemoteDealsSortBy.SAVINGS, DealsSortBy.SAVINGS.toDomain())
        assertEquals(RemoteDealsSortBy.PRICE, DealsSortBy.PRICE.toDomain())
        assertEquals(RemoteDealsSortBy.METACRITIC, DealsSortBy.METACRITIC.toDomain())
        assertEquals(RemoteDealsSortBy.REVIEWS, DealsSortBy.REVIEWS.toDomain())
        assertEquals(RemoteDealsSortBy.RELEASE, DealsSortBy.RELEASE.toDomain())
        assertEquals(RemoteDealsSortBy.STORE, DealsSortBy.STORE.toDomain())
        assertEquals(RemoteDealsSortBy.RECENT, DealsSortBy.RECENT.toDomain())
    }

    @Test
    fun `RemoteDeal toDomain denominates prices via context`() {
        val deal = baseDeal(salePrice = 9.99, normalPrice = 19.99).toDomain(ctx)
        assertEquals(9.99, deal.salePriceValue, 0.0)
        assertEquals("$9.99", deal.salePriceDenominated)
        assertEquals(19.99, deal.normalPriceValue, 0.0)
        assertEquals("$19.99", deal.normalPriceDenominated)
        assertEquals(true, deal.isOnSale)
    }

    @Test
    fun `RemoteDeal isOnSale 0 maps to false`() {
        val deal = baseDeal(isOnSale = 0).toDomain(ctx)
        assertEquals(false, deal.isOnSale)
    }

    @Test(expected = Exception::class)
    fun `RemoteDeal isOnSale 2 throws`() {
        baseDeal(isOnSale = 2).toDomain(ctx)
    }

    @Test
    fun `RemoteDealDetails toDomain wires gameInfo cheaperStores and cheapestPrice together`() {
        val details = RemoteDealDetails(
            gameInfo = baseDealInfo(),
            cheaperStores = listOf(
                RemoteDealDetails.RemoteCheaperStore(
                    dealID = "d1",
                    storeID = 2,
                    salePrice = 1.0,
                    retailPrice = 2.0,
                ),
                RemoteDealDetails.RemoteCheaperStore(
                    dealID = "d2",
                    storeID = 3,
                    salePrice = 3.0,
                    retailPrice = 4.0,
                ),
            ),
            cheapestPrice = RemoteDealDetails.RemoteCheapestPrice(price = 4.99, date = 1234),
        ).toDomain(ctx)

        assertEquals("Game", details.gameInfo.name)
        assertEquals(2, details.cheaperStores.size)
        assertEquals("d1", details.cheaperStores[0].dealID)
        assertEquals("d2", details.cheaperStores[1].dealID)
        assertEquals(4.99, details.cheapestPrice?.priceValue!!, 0.0)
    }

    @Test
    fun `RemoteDealDetails toDomain propagates null cheapestPrice`() {
        val details = RemoteDealDetails(
            gameInfo = baseDealInfo(),
            cheaperStores = emptyList(),
            cheapestPrice = RemoteDealDetails.RemoteCheapestPrice(price = null, date = 0),
        ).toDomain(ctx)

        assertNull(details.cheapestPrice)
    }

    @Test
    fun `RemoteDealDetails GameInfo zeroes out 0 ratings and metacritic`() {
        val info = baseDealInfo(steamRatingPercent = 0, metacriticScore = 0).toDomain(ctx)
        assertNull(info.steamRatingPercent)
        assertNull(info.metacriticScore)
    }

    @Test
    fun `RemoteDealDetails GameInfo keeps positive ratings`() {
        val info = baseDealInfo(steamRatingPercent = 90, metacriticScore = 80).toDomain(ctx)
        assertEquals(90, info.steamRatingPercent)
        assertEquals(80, info.metacriticScore)
    }

    @Test
    fun `RemoteDealDetails GameInfo passes releaseDate through formatter`() {
        val info = baseDealInfo(releaseDate = 0L).toDomain(ctx)
        assertEquals("1970-01-01", info.releaseDate)
    }

    @Test
    fun `RemoteDealDetails GameInfo steamworks parses to boolean`() {
        assertEquals(true, baseDealInfo(steamworks = 1).toDomain(ctx).steamworks)
        assertEquals(false, baseDealInfo(steamworks = 0).toDomain(ctx).steamworks)
        assertNull(baseDealInfo(steamworks = null).toDomain(ctx).steamworks)
    }

    @Test
    fun `RemoteCheapestPrice null price returns null`() {
        val result = RemoteDealDetails.RemoteCheapestPrice(price = null, date = 0).toDomain(ctx)
        assertNull(result)
    }

    @Test
    fun `RemoteCheapestPrice non-null price formats date via context`() {
        val result = RemoteDealDetails.RemoteCheapestPrice(price = 4.99, date = 1234).toDomain(ctx)
        assertEquals(4.99, result?.priceValue!!, 0.0)
        assertEquals("$4.99", result.priceDenominated)
        assertEquals("2020-01-01", result.date)
    }

    @Test
    fun `RemoteCheaperStore denominates both prices`() {
        val s = RemoteDealDetails.RemoteCheaperStore(
            dealID = "d",
            storeID = 2,
            salePrice = 1.0,
            retailPrice = 2.0,
        ).toDomain(ctx)
        assertEquals("$1.0", s.salePriceDenominated)
        assertEquals("$2.0", s.retailPriceDenominated)
    }

    @Test
    fun `RemoteGame toDomain maps cheapest fields`() {
        val game = RemoteGame(
            gameID = 100,
            steamAppID = 200,
            cheapest = 5.99,
            cheapestDealID = "deal-1",
            external = "Halo",
            internalName = "HALO",
            thumb = "thumb",
        ).toDomain(ctx)

        assertEquals(100, game.gameID)
        assertEquals("Halo", game.title)
        assertEquals(5.99, game.cheapestValue, 0.0)
        assertEquals("$5.99", game.cheapestDenominated)
    }

    @Test
    fun `RemoteGameDetails GameDeal rounds savings`() {
        val deal = RemoteGameDetails.RemoteGameDeal(
            storeID = 1,
            dealID = "d",
            price = 1.0,
            retailPrice = 2.0,
            savings = 49.6,
        ).toDomain(ctx)
        assertEquals(50, deal.savings)
    }

    @Test
    fun `RemoteGameDetails CheapestPriceEver formats date and denominates price`() {
        val ever = RemoteGameDetails.RemoteGameCheapestPriceEver(price = 3.5, date = 1234).toDomain(ctx)
        assertEquals(3.5, ever.priceValue, 0.0)
        assertEquals("$3.5", ever.priceDenominated)
        assertEquals("2020-01-01", ever.date)
    }

    @Test
    fun `RemoteGameDetails toDomain maps info deals and cheapest`() {
        val details = RemoteGameDetails(
            info = RemoteGameDetails.RemoteGameInfo(title = "T", steamAppID = 1, thumb = "thumb"),
            cheapestPriceEver = RemoteGameDetails.RemoteGameCheapestPriceEver(price = 1.0, date = 1234),
            deals = listOf(
                RemoteGameDetails.RemoteGameDeal(
                    storeID = 1,
                    dealID = "d",
                    price = 1.0,
                    retailPrice = 2.0,
                    savings = 50.0,
                ),
            ),
        ).toDomain(ctx)

        assertEquals("T", details.info.title)
        assertEquals(1, details.deals.size)
    }

    private fun baseStore(isActive: Int) = RemoteStore(
        storeID = 1,
        storeName = "Steam",
        isActive = isActive,
        images = RemoteStore.RemoteStoreImages(banner = "/b", logo = "/l", icon = "/i"),
    )

    private fun baseDeal(
        salePrice: Double = 1.0,
        normalPrice: Double = 2.0,
        isOnSale: Int = 1,
    ) = RemoteDeal(
        internalName = "n",
        title = "t",
        dealID = "d",
        storeID = 1,
        gameID = 1,
        salePrice = salePrice,
        normalPrice = normalPrice,
        isOnSale = isOnSale,
        savings = 50.0,
        metacriticScore = 80,
        steamRatingPercent = 90,
        steamRatingCount = "100",
        releaseDate = 0,
        lastChange = 0,
        dealRating = 9.0,
        thumb = "thumb",
    )

    private fun baseDealInfo(
        steamRatingPercent: Int = 90,
        metacriticScore: Int = 80,
        releaseDate: Long = 0L,
        steamworks: Int? = null,
    ) = RemoteDealDetails.RemoteGameInfo(
        storeID = 1,
        gameID = 1,
        name = "Game",
        salePrice = 1.0,
        retailPrice = 2.0,
        steamRatingPercent = steamRatingPercent,
        steamRatingCount = "100",
        metacriticScore = metacriticScore,
        releaseDate = releaseDate,
        publisher = "ACME",
        steamworks = steamworks,
        thumb = "thumb",
    )
}
