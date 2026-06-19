package pm.bam.gamedeals.domain.repositories.pricewatch

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.PriceWatch
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PriceWatchCheckerTest {

    private val repository: PriceWatchRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val checker = PriceWatchCheckerImpl(repository, gamesRepository) { title, price -> "$title|$price" }

    private fun watch(id: String, target: Double, lastNotified: Double? = null) =
        PriceWatch(gameId = id, title = "Game $id", targetPriceValue = target, targetPriceDenominated = "$$target", country = "US", createdAtMs = 0L, lastNotifiedPriceValue = lastNotified)

    private fun price(id: String, best: Double?) = BundleGamePrice(
        gameId = id,
        bestShopName = null,
        bestPriceValue = best,
        bestPriceDenominated = best?.let { "$$it" },
        bestCutPercent = null,
        historicalLowValue = null,
        historicalLowDenominated = null,
    )

    @Test
    fun no_watches_skips_the_price_fetch_and_returns_empty() = runTest {
        everySuspend { repository.getWatches() } returns emptyList()

        assertTrue(checker.collectCrossedAlerts().isEmpty())

        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
    }

    @Test
    fun a_price_at_or_below_target_emits_an_alert_and_records_it() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = 18.0))

        val alerts = checker.collectCrossedAlerts()

        assertEquals(1, alerts.size)
        assertEquals("g1", alerts.first().gameId)
        verifySuspend(exactly(1)) { repository.markNotified("g1", 18.0) }
    }

    @Test
    fun a_price_above_target_does_not_alert() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = 25.0))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
    }

    @Test
    fun a_price_back_above_target_resets_the_dedupe() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0, lastNotified = 18.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = 25.0))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
        verifySuspend(exactly(1)) { repository.markNotified("g1", null) }
    }

    @Test
    fun an_already_notified_price_does_not_re_alert() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0, lastNotified = 18.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = 18.0))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
    }

    @Test
    fun a_further_drop_re_alerts() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0, lastNotified = 18.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = 12.0))

        val alerts = checker.collectCrossedAlerts()

        assertEquals(1, alerts.size)
        verifySuspend(exactly(1)) { repository.markNotified("g1", 12.0) }
    }

    @Test
    fun a_watch_with_no_current_deal_is_skipped() = runTest {
        everySuspend { repository.getWatches() } returns listOf(watch("g1", target = 20.0))
        everySuspend { gamesRepository.getGamePrices(any()) } returns listOf(price("g1", best = null))

        assertTrue(checker.collectCrossedAlerts().isEmpty())
    }
}
