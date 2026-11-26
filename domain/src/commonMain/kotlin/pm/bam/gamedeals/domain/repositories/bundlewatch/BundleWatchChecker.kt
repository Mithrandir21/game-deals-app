package pm.bam.gamedeals.domain.repositories.bundlewatch

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.notifications.AlertKind
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.warn

/**
 * Bundle watch: tells a signed-in user when a game on their ITAD waitlist turns up in a bundle or a giveaway. Runs
 * on the phone inside the background notification poll, so it inherits the user's notification opt-in; nothing is
 * sent anywhere.
 *
 * Bundles list their games by ITAD id, so they match exactly. GamerPower giveaways only have a title, so they match
 * only when the title minus "(Store) Giveaway" equals a waitlist title, ignoring case and punctuation
 * ([parseGiveawayTitle]); that misses some giveaways but never sends a false one.
 *
 * Each match is alerted once ([BundleWatchSeenStore]). The first check, and the first after signing in, only takes
 * note of what is already out there, so nobody gets a burst of alerts for bundles that have run for weeks.
 */
interface BundleWatchChecker {
    /** New bundle and giveaway matches since the last check, as alerts; empty when signed out. Never throws. */
    suspend fun collectNewAlerts(): List<PendingNotificationAlert>
}

/** Builds the tray lines; domain has no string resources, so the DI module supplies the English copy. */
internal interface BundleWatchCopy {
    fun bundle(gameTitle: String, bundleTitle: String, priceDenominated: String?): String
    fun giveaway(gameTitle: String, store: String?): String
}

/** The tray copy, in the same plain English as the followed-series alerts. */
internal object EnglishBundleWatchCopy : BundleWatchCopy {
    override fun bundle(gameTitle: String, bundleTitle: String, priceDenominated: String?): String =
        if (priceDenominated.isNullOrBlank()) "$gameTitle is in $bundleTitle" else "$gameTitle is in $bundleTitle, from $priceDenominated"

    override fun giveaway(gameTitle: String, store: String?): String =
        if (store.isNullOrBlank()) "$gameTitle is free in a giveaway" else "$gameTitle is free on $store"
}

internal class BundleWatchCheckerImpl(
    private val waitlistRepository: WaitlistRepository,
    private val bundlesRepository: BundlesRepository,
    private val giveawaysRepository: GiveawaysRepository,
    private val seenStore: BundleWatchSeenStore,
    private val clock: Clock,
    private val copy: BundleWatchCopy,
    private val logger: Logger,
) : BundleWatchChecker {

    override suspend fun collectNewAlerts(): List<PendingNotificationAlert> = try {
        collect()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        warn(logger, error) { "Bundle watch check failed" }
        emptyList()
    }

    private suspend fun collect(): List<PendingNotificationAlert> {
        // Empty when signed out (the repository clears its cache then) or when the waitlist is empty.
        val waitlist = waitlistRepository.getWaitlist()
        if (waitlist.isEmpty()) {
            // Forget what was seen, so signing in (again) starts by taking note rather than alerting.
            seenStore.clear()
            return emptyList()
        }
        val bundles = bundleMatches(waitlist)
        val giveaways = giveawayMatches(waitlist)
        val matches = bundles.orEmpty() + giveaways.orEmpty()
        val seen = seenStore.get()
        // Remember only what is out there now, so the set can't grow without bound and a returning bundle alerts again.
        // A source that failed to load keeps what it had, or its next good check would re-alert everything.
        val kept = seen.orEmpty().filter { signature ->
            (bundles == null && signature.startsWith(BUNDLE_SIGNATURE)) || (giveaways == null && signature.startsWith(GIVEAWAY_SIGNATURE))
        }
        seenStore.replace(matches.map { it.signature }.toSet() + kept)
        if (seen == null) return emptyList()
        return matches.filter { it.signature !in seen }.distinctBy { it.signature }.map { it.alert }
    }

    /** Null when the bundles couldn't be loaded. */
    private suspend fun bundleMatches(waitlist: List<WaitlistEntry>): List<Match>? {
        val byId = waitlist.associateBy { it.gameId }
        val now = clock.nowMillis()
        val bundles = bestEffort("bundles") { bundlesRepository.getBundles() } ?: return null
        return bundles
            .filter { bundle -> bundle.expiryEpochMs?.let { it > now } ?: true }
            .flatMap { bundle -> bundle.allGames().mapNotNull { game -> byId[game.id]?.let { entry -> bundle to entry } } }
            .map { (bundle, entry) ->
                Match(
                    signature = "$BUNDLE_SIGNATURE${bundle.id}:${entry.gameId}",
                    alert = PendingNotificationAlert(
                        notificationId = "$BUNDLE_ALERT_ID_PREFIX${bundle.id}:${entry.gameId}",
                        title = copy.bundle(entry.title, bundle.title, bundle.priceDenominated),
                        games = emptyList(),
                        gameId = entry.gameId,
                        kind = AlertKind.WAITLIST_BUNDLE,
                    ),
                )
            }
    }

    /** Null when the giveaways couldn't be loaded. A failed refresh still checks the ones already stored. */
    private suspend fun giveawayMatches(waitlist: List<WaitlistEntry>): List<Match>? {
        val byKey = waitlist.groupBy { matchKey(it.title) }.filterKeys { it.isNotEmpty() }
        bestEffort("giveaway refresh") { giveawaysRepository.refreshGiveawaysIfOlderThan(GIVEAWAYS_MAX_AGE_MS); emptyList<Unit>() }
        val giveaways = bestEffort("giveaways") { giveawaysRepository.observeGiveaways().first() } ?: return null
        return giveaways
            .filter { it.type == GiveawayType.GAME || it.type == GiveawayType.DLC }
            .filter { it.status.equals(ACTIVE, ignoreCase = true) }
            .flatMap { giveaway ->
                val parsed = parseGiveawayTitle(giveaway.title)
                byKey[parsed.key].orEmpty().map { entry -> giveaway.toMatch(entry, parsed.store) }
            }
    }

    private fun Giveaway.toMatch(entry: WaitlistEntry, store: String?) = Match(
        signature = "$GIVEAWAY_SIGNATURE$id:${entry.gameId}",
        alert = PendingNotificationAlert(
            notificationId = "$GIVEAWAY_ALERT_ID_PREFIX$id:${entry.gameId}",
            title = copy.giveaway(entry.title, store),
            games = emptyList(),
            gameId = entry.gameId,
            kind = AlertKind.WAITLIST_GIVEAWAY,
        ),
    )

    private fun Bundle.allGames(): List<Bundle.BundleGame> = (games + tiers.flatMap { it.games }).distinctBy { it.id }

    private suspend fun <T> bestEffort(what: String, block: suspend () -> List<T>): List<T>? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        warn(logger, error) { "Bundle watch couldn't load $what" }
        null
    }

    private class Match(val signature: String, val alert: PendingNotificationAlert)

    companion object {
        const val BUNDLE_ALERT_ID_PREFIX = "bundlewatch:bundle:"
        const val GIVEAWAY_ALERT_ID_PREFIX = "bundlewatch:giveaway:"
        private const val ACTIVE = "Active"
        private const val BUNDLE_SIGNATURE = "b:"
        private const val GIVEAWAY_SIGNATURE = "g:"
        private const val GIVEAWAYS_MAX_AGE_MS = 6L * 60 * 60 * 1000
    }
}

/** The bundle and giveaway matches already alerted; null until the first check has taken note. */
internal interface BundleWatchSeenStore {
    suspend fun get(): Set<String>?
    suspend fun replace(signatures: Set<String>)
    suspend fun clear()
}

internal const val BUNDLE_WATCH_SEEN_KEY = "bundle_watch_seen_signatures"

internal class BundleWatchSeenStoreImpl(private val storage: Storage) : BundleWatchSeenStore {
    override suspend fun get(): Set<String>? =
        runCatching { storage.getNullable(BUNDLE_WATCH_SEEN_KEY, SetSerializer(String.serializer())) }.getOrNull()

    override suspend fun replace(signatures: Set<String>) {
        storage.save(BUNDLE_WATCH_SEEN_KEY, signatures, SetSerializer(String.serializer()))
    }

    override suspend fun clear() {
        storage.remove(BUNDLE_WATCH_SEEN_KEY)
    }
}
