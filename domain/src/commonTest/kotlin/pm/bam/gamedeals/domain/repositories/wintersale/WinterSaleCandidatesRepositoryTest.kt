package pm.bam.gamedeals.domain.repositories.wintersale

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinterSaleCandidatesRepositoryTest {
    @Test
    fun popular_excludes_non_games_unknown_adult_owned_and_cheaper_ordinary_offers() = runTest {
        val fixture = fixture()
        fixture.popular.games = listOf(
            popular("sale"), popular("ordinary"), popular("dlc", type = "dlc"), popular("unknown", mature = null),
            popular("owned"), popular("ignored"), popular("waitlisted"),
        )
        fixture.offers.values = mapOf("sale" to offer("sale"), "ordinary" to offer("ordinary", cut = 0))
        val section = fixture.repo.popular("GB", false)

        assertEquals(listOf("sale"), section.games.map { it.gameId })
        assertEquals(listOf(100), fixture.popular.limits)
        assertTrue(section.moreCandidatesAvailable)
    }

    @Test
    fun waitlist_refreshes_stale_snapshot_and_keeps_failed_price_state() = runTest {
        val fixture = fixture()
        fixture.waitlist.snapshot = WaitlistDisplaySnapshot(regionCode = "US", refreshedAtEpochMs = 100L)
        fixture.waitlist.refreshed = WaitlistDisplaySnapshot(
            items = listOf(waitlist("safe"), waitlist("unknown", mature = null), waitlist("zero")),
            regionCode = "GB", refreshedAtEpochMs = 10_000L, priceFetchIncomplete = true,
        )
        fixture.waitlist.entries = listOf("safe", "unknown", "zero").map { WaitlistEntry(it, it) }
        fixture.offers.values = mapOf("safe" to offer("safe"), "zero" to offer("zero", cut = 0))
        val section = fixture.repo.waitlist("GB", false, null, false)

        assertEquals(listOf("safe"), section.games.map { it.gameId })
        assertTrue(section.incomplete)
        assertEquals(1, fixture.waitlist.refreshes)
    }

    @Test
    fun signed_out_waitlist_requires_sign_in_but_popular_still_loads() = runTest {
        val fixture = fixture(signedIn = false)
        fixture.popular.games = listOf(popular("sale"))
        fixture.offers.values = mapOf("sale" to offer("sale"))
        assertTrue(fixture.repo.waitlist("GB", false, null, false).signInRequired)
        assertEquals(listOf("sale"), fixture.repo.popular("GB", false).games.map { it.gameId })
    }

    private fun fixture(signedIn: Boolean = true): Fixture {
        val waitlist = FakeWaitlist()
        val popular = FakePopular()
        val offers = FakeOffers()
        val collection = mock<CollectionRepository>(MockMode.autofill)
        val ignored = mock<IgnoredRepository>(MockMode.autofill)
        val auth = mock<AuthTokenStore>(MockMode.autofill)
        everySuspend { auth.getAccessToken() } returns if (signedIn) "token" else null
        every { collection.observeCollectionIds() } returns flowOf(persistentSetOf("owned"))
        every { ignored.observeIgnoredIds() } returns flowOf(persistentSetOf("ignored"))
        val repo = WinterSaleCandidatesRepositoryImpl(waitlist, collection, ignored, auth, popular, offers, Clock { 10_000L })
        return Fixture(repo, waitlist, popular, offers)
    }

    private data class Fixture(val repo: WinterSaleCandidatesRepository, val waitlist: FakeWaitlist, val popular: FakePopular, val offers: FakeOffers)

    private class FakeWaitlist : WaitlistRepository {
        var entries = listOf(WaitlistEntry("waitlisted", "waitlisted"))
        var snapshot: WaitlistDisplaySnapshot? = null
        var refreshed: WaitlistDisplaySnapshot? = null
        var refreshes = 0
        override fun observeWaitlistIds() = flowOf(persistentSetOf("waitlisted"))
        override fun observeIsWaitlisted(gameId: String) = flowOf(false)
        override suspend fun getWaitlist() = entries
        override fun observeWaitlistDisplay() = flowOf(snapshot)
        override suspend fun refreshWaitlistDisplay() { refreshes++; snapshot = refreshed }
        override suspend fun clearLocal() = Unit
        override suspend fun toggleWaitlist(gameId: String) = pm.bam.gamedeals.domain.models.RepoUpdateResult.UPDATED
    }

    private class FakePopular : WinterSalePopularSource {
        var games = emptyList<WinterSalePopularGame>()
        val limits = mutableListOf<Int>()
        override suspend fun fetch(country: String, includeAdult: Boolean, limit: Int): List<WinterSalePopularGame> {
            limits += limit
            return games
        }
    }

    private class FakeOffers : WinterSaleOffersRepository {
        var values = emptyMap<String, WinterSaleOffer>()
        override suspend fun load(gameIds: List<String>, country: String) = WinterSaleOfferLoad(country, 10_000L,
            gameIds.associateWith { id -> values[id]?.let { WinterSalePriceStatus.Priced(it) } ?: WinterSalePriceStatus.NoCurrentOffer })
    }

    private fun popular(id: String, type: String = "game", mature: Boolean? = false) = WinterSalePopularGame(id, id, GameArtwork(), type, mature)
    private fun waitlist(id: String, mature: Boolean? = false) = WaitlistDisplayItem(
        id, id, type = "game", mature = mature, bestPriceDenominated = "£10", discountPercent = 20,
    )
    private fun offer(id: String, cut: Int = 20) = WinterSaleOffer(
        id, "GB", 1, "Shop", WinterSaleMoney(10.0, "GBP"), null, cut, "https://example.com/$id", null, emptyList(), null, null, null,
    )
}
