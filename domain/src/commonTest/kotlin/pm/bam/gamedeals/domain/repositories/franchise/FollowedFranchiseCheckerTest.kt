package pm.bam.gamedeals.domain.repositories.franchise

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FollowedFranchiseCheckerTest {

    private val followedRepository: FollowedFranchiseRepository = mock(MockMode.autoUnit)
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val seenStore: FollowedDealSeenStore = mock(MockMode.autoUnit)
    private val checker = FollowedFranchiseCheckerImpl(followedRepository, igdbRepository, gamesRepository, seenStore) { game, franchise, cut, price ->
        "$game|$franchise|$cut|$price"
    }

    private fun franchise(id: Long, name: String) = FollowedFranchise(franchiseId = id, name = name, addedAtMs = 0L)

    private fun igdbGame(id: Long, name: String, steamAppId: Int?) =
        IgdbGame(id = id, name = name, summary = null, steamAppId = steamAppId)

    private fun price(id: String, best: Double?, cut: Int?) = BundleGamePrice(
        gameId = id,
        bestShopName = null,
        bestPriceValue = best,
        bestPriceDenominated = best?.let { "$$it" },
        bestCutPercent = cut,
        historicalLowValue = null,
        historicalLowDenominated = null,
    )

    @Test
    fun no_followed_franchises_skips_all_lookups_and_returns_empty() = runTest {
        everySuspend { followedRepository.getFollowed() } returns emptyList()

        assertTrue(checker.collectCrossedAlerts().isEmpty())

        verifySuspend(exactly(0)) { igdbRepository.fetchFranchiseGames(any(), any()) }
        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
    }

    @Test
    fun an_on_sale_game_emits_an_alert_carrying_the_itad_game_id_and_is_remembered() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = 500))
        everySuspend { gamesRepository.findGameIdBySteamAppId(any(), any()) } returns "itad-10"
        everySuspend { seenStore.get() } returns emptySet()
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("itad-10", best = 7.49, cut = 75))

        val alerts = checker.collectCrossedAlerts()

        assertEquals(1, alerts.size)
        assertEquals("itad-10", alerts.first().gameId)
        assertEquals("Halo 5|Halo|75|\$7.49", alerts.first().title)
        verifySuspend(exactly(1)) { seenStore.replace(setOf("itad-10@7.49")) }
    }

    @Test
    fun an_already_seen_deal_at_the_same_price_is_suppressed() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = 500))
        everySuspend { gamesRepository.findGameIdBySteamAppId(any(), any()) } returns "itad-10"
        everySuspend { seenStore.get() } returns setOf("itad-10@7.49")
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("itad-10", best = 7.49, cut = 75))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
        // Still pruned to the current on-sale set, so an ended-then-returned deal re-alerts later.
        verifySuspend(exactly(1)) { seenStore.replace(setOf("itad-10@7.49")) }
    }

    @Test
    fun a_deeper_discount_re_alerts() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = 500))
        everySuspend { gamesRepository.findGameIdBySteamAppId(any(), any()) } returns "itad-10"
        everySuspend { seenStore.get() } returns setOf("itad-10@7.49")
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("itad-10", best = 5.0, cut = 80))

        val alerts = checker.collectCrossedAlerts()

        assertEquals(1, alerts.size)
        assertEquals("itad-10", alerts.first().gameId)
    }

    @Test
    fun a_game_with_no_current_deal_is_skipped() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = 500))
        everySuspend { gamesRepository.findGameIdBySteamAppId(any(), any()) } returns "itad-10"
        everySuspend { seenStore.get() } returns emptySet()
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("itad-10", best = null, cut = null))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
        verifySuspend(exactly(1)) { seenStore.replace(emptySet()) }
    }

    @Test
    fun a_game_without_a_steam_id_never_reaches_the_itad_lookup() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = null))
        everySuspend { seenStore.get() } returns emptySet()

        assertTrue(checker.collectCrossedAlerts().isEmpty())

        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), any()) }
        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
    }

    @Test
    fun a_game_itad_does_not_track_is_dropped_before_pricing() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } returns listOf(igdbGame(10L, "Halo 5", steamAppId = 500))
        everySuspend { gamesRepository.findGameIdBySteamAppId(any(), any()) } returns null
        everySuspend { seenStore.get() } returns emptySet()

        assertTrue(checker.collectCrossedAlerts().isEmpty())

        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
    }

    @Test
    fun a_franchise_whose_igdb_fetch_fails_contributes_nothing() = runTest {
        everySuspend { followedRepository.getFollowed() } returns listOf(franchise(1L, "Halo"))
        everySuspend { igdbRepository.fetchFranchiseGames(any(), any()) } throws RuntimeException("igdb down")
        everySuspend { seenStore.get() } returns emptySet()

        assertTrue(checker.collectCrossedAlerts().isEmpty())
    }
}
