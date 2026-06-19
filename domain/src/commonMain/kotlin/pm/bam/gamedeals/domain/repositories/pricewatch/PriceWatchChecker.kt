package pm.bam.gamedeals.domain.repositories.pricewatch

import pm.bam.gamedeals.domain.models.PriceWatch
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * The client-side half of background notification delivery (Phase 3): for each [PriceWatch], fetch the
 * game's current best price (one batched `/games/prices/v3` via [GamesRepository.getGamePrices]) and emit a
 * [PendingNotificationAlert] for every watch whose target has just been crossed. Platform-agnostic and
 * driven by the same poll as the ITAD notification sync, so it inherits the user's background-notification
 * opt-in + schedule.
 *
 * Dedupe: a crossed watch records the price it fired at ([PriceWatch.lastNotifiedPriceValue]). It won't
 * re-alert while the price stays at/above that mark, re-alerts if the price drops *further*, and resets
 * (so a future drop alerts again) once the price climbs back above the target.
 */
interface PriceWatchChecker {
    suspend fun collectCrossedAlerts(): List<PendingNotificationAlert>
}

internal class PriceWatchCheckerImpl(
    private val repository: PriceWatchRepository,
    private val gamesRepository: GamesRepository,
    private val priceAlertTitle: (gameTitle: String, priceDenominated: String) -> String,
) : PriceWatchChecker {

    override suspend fun collectCrossedAlerts(): List<PendingNotificationAlert> {
        val watches = repository.getWatches()
        if (watches.isEmpty()) return emptyList()

        val pricesById = runCatching { gamesRepository.getGamePrices(watches.map { it.gameId }) }
            .getOrDefault(emptyList())
            .associateBy { it.gameId }

        val alerts = mutableListOf<PendingNotificationAlert>()
        for (watch in watches) {
            val best = pricesById[watch.gameId]?.bestPriceValue
            if (best == null) continue // no current deal — nothing to compare

            if (best > watch.targetPriceValue + PRICE_EPSILON) {
                // Above target again → reset so the next genuine drop alerts.
                if (watch.lastNotifiedPriceValue != null) repository.markNotified(watch.gameId, null)
                continue
            }

            // At/below target. Skip if we already alerted and it hasn't dropped further since.
            val lastNotified = watch.lastNotifiedPriceValue
            val alreadyAlerted = lastNotified != null && best >= lastNotified - PRICE_EPSILON
            if (alreadyAlerted) continue

            val priceDenominated = pricesById[watch.gameId]?.bestPriceDenominated ?: watch.targetPriceDenominated
            alerts += PendingNotificationAlert(
                notificationId = "$PRICE_WATCH_ID_PREFIX${watch.gameId}",
                title = priceAlertTitle(watch.title, priceDenominated),
                games = emptyList(),
                gameId = watch.gameId,
            )
            repository.markNotified(watch.gameId, best)
        }
        return alerts
    }

    private companion object {
        const val PRICE_EPSILON = 0.005 // half a cent — float-safe "at or below target"
        const val PRICE_WATCH_ID_PREFIX = "pricewatch:"
    }
}
