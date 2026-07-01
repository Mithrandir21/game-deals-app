package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import pm.bam.gamedeals.common.ui.PreviewStore
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.RegionalPrice
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.RegionalPricesState
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.NoOpAnalytics

/**
 * Shared sample data for the Game Page `@Preview`s. Kept in one place so the per-file previews stay short
 * and consistent; not used at runtime.
 */

/** Wraps composables that `koinInject<Analytics>()` (e.g. [StoreGameDealRow]) so they render in a preview. */
@Composable
internal fun PreviewKoin(content: @Composable () -> Unit) {
    KoinApplication(application = { modules(module { single<Analytics> { NoOpAnalytics } }) }) {
        content()
    }
}

internal val PreviewSimilarGames = persistentListOf(
    IgdbGame.IgdbSimilarGame(id = 1L, name = "Celeste", coverImageId = null),
    IgdbGame.IgdbSimilarGame(id = 2L, name = "Hollow Knight", coverImageId = null),
    IgdbGame.IgdbSimilarGame(id = 3L, name = "Ori and the Blind Forest", coverImageId = null),
)

internal val PreviewIgdbGame = IgdbGame(
    id = 100L,
    name = "Hades",
    summary = "A rogue-like dungeon crawler in which you defy the god of the dead as you hack and slash " +
        "out of the Underworld of Greek myth. As you grow more powerful, each unique escape attempt " +
        "reveals more of the story.",
    storyline = "As the immortal Prince of the Underworld, wield the powers and mythic weapons of Olympus " +
        "to break free from the clutches of the god of the dead himself.",
    coverImageId = null,
    screenshotImageIds = persistentListOf("s1", "s2", "s3"),
    rating = 92.0,
    ratingCount = 4200,
    aggregatedRating = 93.0,
    aggregatedRatingCount = 120,
    genres = persistentListOf("Indie", "Adventure"),
    themes = persistentListOf("Action"),
    involvedCompanies = persistentListOf(
        IgdbGame.IgdbCompanyRole("Supergiant Games", IgdbGame.IgdbCompanyRole.Role.Developer),
        IgdbGame.IgdbCompanyRole("Supergiant Games", IgdbGame.IgdbCompanyRole.Role.Publisher),
    ),
    similarGames = PreviewSimilarGames,
    dlcs = persistentListOf(IgdbGame.IgdbSimilarGame(id = 4L, name = "Hades II", coverImageId = null)),
    platforms = persistentListOf("PC", "PS5", "Switch"),
    videos = persistentListOf(IgdbGame.IgdbVideo(videoId = "dQw4w9WgXcQ", name = "Launch Trailer")),
    franchises = persistentListOf(
        IgdbGame.IgdbFranchise(id = 9L, name = "Supergiant", games = PreviewSimilarGames),
    ),
    ageRatings = persistentListOf(IgdbGame.IgdbAgeRating(IgdbGame.IgdbAgeRating.Board.ESRB, "T")),
    gameModes = persistentListOf("Single player"),
    timeToBeat = IgdbGame.IgdbTimeToBeat(hastily = 72_000, normally = 108_000, completely = 360_000),
)

internal val PreviewGameMeta = GameMeta(
    gameId = "g1",
    developers = persistentListOf("Supergiant Games"),
    publishers = persistentListOf("Supergiant Games"),
    reviews = persistentListOf(
        GameMeta.Review(source = "Steam", score = 98, count = 320_000),
        GameMeta.Review(source = "Metacritic", score = 93, count = 120),
    ),
    players = GameMeta.Players(recent = 12_000, peak = 45_000),
)

internal val PreviewBundles = persistentListOf(
    Bundle(id = 1, title = "Indie Legends Bundle", storeName = "Humble Bundle", url = "", expiryEpochMs = null, gameCount = 8, priceDenominated = "$12.00", games = persistentListOf()),
    Bundle(id = 2, title = "Roguelike Megapack", storeName = "Fanatical", url = "", expiryEpochMs = null, gameCount = 12, priceDenominated = "$18.99", games = persistentListOf()),
)

internal val PreviewStoreDeals = persistentListOf(
    StoreDealPair(PreviewStore.copy(storeName = "Steam"), GameDetails.GameDeal(1, "d1", 12.49, "$12.49", 24.99, "$24.99", 50)),
    StoreDealPair(PreviewStore.copy(storeID = 2, storeName = "GOG"), GameDetails.GameDeal(2, "d2", 14.99, "$14.99", 24.99, "$24.99", 40)),
)

internal val PreviewWebsites = persistentListOf(
    WebsiteUiModel("https://store.steampowered.com", IgdbGame.IgdbWebsite.Category.Steam, faviconUrl = null, faviconCacheKey = null),
    WebsiteUiModel("https://hades.supergiant.net", IgdbGame.IgdbWebsite.Category.Official, faviconUrl = null, faviconCacheKey = null),
)

internal val PreviewRegionalPrices = persistentListOf(
    RegionalPrice(Country("US", "United States"), 12.49, "$12.49", ""),
    RegionalPrice(Country("PL", "Poland"), 39.99, "39.99 zł", ""),
    RegionalPrice(Country("BR", "Brazil"), 29.90, "R$29.90", ""),
)

/** ~15 months of prices with a few sale dips and a stable $24.99 MSRP — enough to exercise the step line, MSRP rule and low/current dots. */
internal val PreviewPriceHistory = PriceHistory(
    gameID = "g1",
    points = persistentListOf(
        PriceHistory.PricePoint(1_704_067_200_000, 24.99, "$24.99", cutPercent = 0, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_709_251_200_000, 18.74, "$18.74", cutPercent = 25, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_714_521_600_000, 24.99, "$24.99", cutPercent = 0, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_719_792_000_000, 12.49, "$12.49", cutPercent = 50, regularValue = 24.99, shopName = "GOG"),
        PriceHistory.PricePoint(1_725_148_800_000, 24.99, "$24.99", cutPercent = 0, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_733_011_200_000, 9.99, "$9.99", cutPercent = 60, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_738_368_000_000, 24.99, "$24.99", cutPercent = 0, regularValue = 24.99, shopName = "Steam"),
        PriceHistory.PricePoint(1_743_465_600_000, 12.49, "$12.49", cutPercent = 50, regularValue = 24.99, shopName = "GOG"),
    ),
)

internal val PreviewGameDetails = GameDetails(
    info = GameDetails.GameInfo(title = "Hades"),
    cheapestPriceEver = GameDetails.GameCheapestPriceEver(12.49, "$12.49", "January 13, 2024"),
    deals = persistentListOf(GameDetails.GameDeal(1, "d1", 12.49, "$12.49", 24.99, "$24.99", 50)),
)

/** A fully-populated page: deals, meta, bundles, IGDB record, websites and regional prices all present. */
internal val PreviewGamePageData = GamePageData.Data(
    title = "Hades",
    gameId = "g1",
    deals = SectionState.Loaded(PreviewGameDetails),
    dealDetails = PreviewStoreDeals,
    priceHistory = SectionState.Loaded(PreviewPriceHistory),
    gameMeta = SectionState.Loaded(PreviewGameMeta),
    bundles = PreviewBundles,
    igdb = SectionState.Loaded(PreviewIgdbGame),
    websites = PreviewWebsites,
    regionalPricesState = RegionalPricesState.Loaded(PreviewRegionalPrices),
)
