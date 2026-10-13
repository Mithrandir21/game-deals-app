package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.repositories.cache.LIBRARY_REUSE_MILLIS
import pm.bam.gamedeals.domain.repositories.cache.RecentResult
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's ITAD notifications (epic #272, P2 #277), kept on the device ([NotificationCacheStore]) so most launches and
 * screen opens make no ITAD call at all — the whole app shares one ITAD request budget.
 *
 * - **The list** is refetched only when a new ITAD batch may have been issued since the last fetch ([listRefreshDueAt]:
 *   ITAD issues notifications in ~6-hour batches) or when [getNotifications] is forced (the background poll, pull-to-refresh).
 * - **A detail** is a snapshot taken when ITAD issued the notification, so it is fetched once and kept until the notification
 *   leaves the list. Its art (which ITAD's detail lacks) is joined by game id from the waitlist, else a per-game lookup.
 *
 * Notifications older than [RETENTION_MILLIS] (7 days) are **ignored** — dropped before they're published, so the list
 * screen, [observeUnreadGameCount] and the background tray poll all see the same recent window (ITAD has no delete API).
 * Everything is auth-gated and wiped by [clearLocal] on logout. Writes are **remote-first** (confirm the ITAD mark-read
 * before flipping the local state).
 */
interface NotificationsRepository {
    /** The loaded notifications, auth-gated (empty when logged out). Reactive — reflects mark-read edits. */
    fun observeNotifications(): Flow<List<ItadNotification>>

    /**
     * The unread badge tally, in the **same unit the Notifications list shows**: for every calendar day with at
     * least one unread entry, that day's distinct games ("· N games"), summed. Games are only known once a
     * notification's detail has been resolved (via [getNotificationDetail] or [resolveUnreadGames]); an
     * unresolved entry contributes nothing yet, exactly as it shows no games on the list. Reactive to mark-read.
     */
    fun observeUnreadGameCount(): Flow<Int>

    /** The notifications; refetched from ITAD only when a new batch may be out ([listRefreshDueAt]) or when [force]d. */
    suspend fun getNotifications(force: Boolean = false): List<ItadNotification>

    /**
     * Resolves the games of every entry on a day that still has unread notifications, so
     * [observeUnreadGameCount] is populated without opening the list. Entries already resolved are skipped;
     * per-entry failures are swallowed (that entry just counts no games, as on the list). No-op when logged out.
     */
    suspend fun resolveUnreadGames()
    suspend fun markRead(id: String)
    suspend fun markAllRead()

    /** The games a waitlist notification refers to, for deep-linking (#288). Empty when logged out. */
    suspend fun getWaitlistGames(notificationId: String): List<NotificationGame>

    /** The full deal content of a waitlist notification (games + alert-time deals + art). Fetched once, then kept. Empty when logged out. */
    suspend fun getNotificationDetail(notificationId: String): NotificationDetail

    /** Wipes what is kept on the device, so the next account to log in starts clean. */
    suspend fun clearLocal()
}

/** How far back notifications are kept; anything older is ignored at load. */
internal const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

/** ITAD's observed cadence for issuing notifications (not documented; seen as ~6 h, drifting a few minutes per batch). */
internal const val BATCH_INTERVAL_MILLIS = 6L * 60 * 60 * 1000

/** Slack after an expected batch before refetching, covering the observed drift. */
internal const val BATCH_GRACE_MILLIS = 15L * 60 * 1000

/** The longest a fetched list is reused, whatever the batch timing says — the cap if ITAD's cadence changes. */
internal const val MAX_LIST_AGE_MILLIS = 3L * 60 * 60 * 1000

/** How long a failed waitlist-art fetch is remembered, so parallel detail loads don't each retry it. */
internal const val WAITLIST_ART_RETRY_MILLIS = 60L * 1000

/**
 * When a list fetched at [fetchedAtMillis] should next be refetched: just after the first batch expected after that fetch
 * (phased from [latestNotificationMillis], the newest notification's time), but never later than [MAX_LIST_AGE_MILLIS].
 */
internal fun listRefreshDueAt(fetchedAtMillis: Long, latestNotificationMillis: Long?): Long {
    val cap = fetchedAtMillis + MAX_LIST_AGE_MILLIS
    if (latestNotificationMillis == null) return cap
    val batchesSince = ((fetchedAtMillis - latestNotificationMillis) / BATCH_INTERVAL_MILLIS).coerceAtLeast(0)
    val nextBatch = latestNotificationMillis + (batchesSince + 1) * BATCH_INTERVAL_MILLIS
    return minOf(cap, nextBatch + BATCH_GRACE_MILLIS)
}

internal class NotificationsRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val dealsSource: DealsSource,
    private val authTokenStore: AuthTokenStore,
    private val cacheStore: NotificationCacheStore,
    private val clock: Clock,
    private val analytics: Analytics,
) : NotificationsRepository {

    // Null until read from [cacheStore]. Every change goes through [mutate], which also persists it.
    private val cache = MutableStateFlow<NotificationCache?>(null)
    private val cacheMutex = Mutex()

    // Serialises art resolution so details resolving in parallel share one waitlist fetch and one lookup per game.
    private val artMutex = Mutex()
    private var waitlistArtFailedAtMillis: Long? = null

    // One waitlist download serves every detail resolving in the same burst (e.g. a first load of many notifications).
    private val recentWaitlistArt = RecentResult<Map<String, GameArtwork>>(clock, LIBRARY_REUSE_MILLIS)

    override fun observeNotifications(): Flow<List<ItadNotification>> =
        combine(authTokenStore.observeAuthState(), cache) { authState, cache ->
            if (authState is AuthState.LoggedIn) cache?.notifications.orEmpty().withinRetention() else emptyList()
        }

    override fun observeUnreadGameCount(): Flow<Int> =
        combine(observeNotifications(), cache) { list, cache ->
            unreadGameCount(list, cache?.details.orEmpty().mapValues { (_, detail) -> detail.games.mapTo(mutableSetOf()) { it.gameId } })
        }

    override suspend fun getNotifications(force: Boolean): List<ItadNotification> {
        if (!loggedIn()) {
            clearLocal()
            return emptyList()
        }
        val current = loaded()
        if (!force && current.fetchedAtMillis > 0 && clock.nowMillis() < listRefreshDueAt(current.fetchedAtMillis, current.latestNotificationMillis())) {
            return current.notifications.withinRetention()
        }
        val list = accountSource.getNotifications().withinRetention()
        val loadedIds = list.mapTo(mutableSetOf()) { it.id }
        mutate { it.copy(notifications = list, fetchedAtMillis = clock.nowMillis(), details = it.details.filterKeys { id -> id in loadedIds }).prunedArt() }
        return list
    }

    override suspend fun resolveUnreadGames() {
        if (!loggedIn()) return
        val current = loaded()
        val pending = current.notifications.withinRetention().onUnreadDays().filter { it.id !in current.details }
        coroutineScope {
            pending.map { entry -> async { runCatching { getNotificationDetail(entry.id) } } }.awaitAll()
        }
    }

    /** Drops notifications outside the [RETENTION_MILLIS] window. Unparseable timestamps are kept (don't hide one over a format quirk). */
    private fun List<ItadNotification>.withinRetention(): List<ItadNotification> {
        val cutoff = clock.nowMillis() - RETENTION_MILLIS
        return filter { (it.timestampMillis() ?: return@filter true) >= cutoff }
    }

    override suspend fun markRead(id: String) {
        if (!loggedIn()) return
        // Remote-first: confirm the ITAD write before updating the local state.
        accountSource.markNotificationRead(id)
        mutate { it.copy(notifications = it.notifications.map { n -> if (n.id == id) n.copy(read = true) else n }) }
        analytics.capture(AnalyticsEvents.NOTIFICATION_MARKED_READ)
    }

    override suspend fun markAllRead() {
        if (!loggedIn()) return
        accountSource.markAllNotificationsRead()
        mutate { it.copy(notifications = it.notifications.map { n -> n.copy(read = true) }) }
        analytics.capture(AnalyticsEvents.NOTIFICATIONS_MARKED_ALL_READ)
    }

    override suspend fun getWaitlistGames(notificationId: String): List<NotificationGame> {
        if (!loggedIn()) return emptyList()
        return accountSource.getWaitlistNotificationGames(notificationId)
    }

    override suspend fun getNotificationDetail(notificationId: String): NotificationDetail {
        if (!loggedIn()) {
            clearLocal()
            return NotificationDetail(notificationId, emptyList())
        }
        val detail = loaded().details[notificationId]
            ?: accountSource.getWaitlistNotificationDetail(notificationId).also { fetched ->
                mutate { it.copy(details = it.details + (notificationId to fetched)) }
            }
        resolveArt(detail.games.map { it.gameId })
        val art = loaded().art
        return detail.copy(games = detail.games.map { game -> game.copy(artwork = art[game.gameId] ?: game.artwork) })
    }

    override suspend fun clearLocal() {
        cacheMutex.withLock {
            cache.value = NotificationCache()
            cacheStore.clear()
        }
        artMutex.withLock { waitlistArtFailedAtMillis = null }
        recentWaitlistArt.invalidate()
    }

    /**
     * Fills in art for any of [gameIds] not yet known: from the waitlist first (one fetch covers every followed game), then
     * a `/games/info` lookup for the rest (e.g. games unfollowed since). Failures are left out and retried on a later read;
     * while the waitlist is unreachable no per-game lookups are made, since it would cover most of them.
     */
    private suspend fun resolveArt(gameIds: List<String>) {
        if (gameIds.all { it in loaded().art }) return
        artMutex.withLock {
            val missing = gameIds.filter { it !in loaded().art }.distinct()
            if (missing.isEmpty()) return
            if (waitlistArtFailedAtMillis?.let { clock.nowMillis() - it < WAITLIST_ART_RETRY_MILLIS } == true) return
            val waitlistArt = runCatching { recentWaitlistArt.get { accountSource.getWaitlist().associate { it.gameId to it.artwork } } }.getOrNull()
            if (waitlistArt == null) {
                waitlistArtFailedAtMillis = clock.nowMillis()
                return
            }
            waitlistArtFailedAtMillis = null
            val fromWaitlist = waitlistArt.filterKeys { it in missing }
            val lookedUp = coroutineScope {
                missing.filter { it !in fromWaitlist }
                    .map { id -> async { runCatching { id to dealsSource.fetchGame(id).artwork }.getOrNull() } }
                    .awaitAll()
                    .filterNotNull()
            }
            mutate { it.copy(art = it.art + fromWaitlist + lookedUp) }
        }
    }

    private suspend fun loaded(): NotificationCache =
        cache.value ?: cacheMutex.withLock { cache.value ?: (cacheStore.get() ?: NotificationCache()).also { cache.value = it } }

    private suspend fun mutate(change: (NotificationCache) -> NotificationCache) {
        loaded()
        cacheMutex.withLock {
            val updated = change(cache.value ?: NotificationCache())
            cache.value = updated
            runCatching { cacheStore.replace(updated) }
        }
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
}

private fun ItadNotification.timestampMillis(): Long? = runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull()

private fun NotificationCache.latestNotificationMillis(): Long? = notifications.mapNotNull { it.timestampMillis() }.maxOrNull()

/** Keeps only the art of games some kept detail still refers to. */
private fun NotificationCache.prunedArt(): NotificationCache {
    val referenced = details.values.flatMapTo(mutableSetOf()) { detail -> detail.games.map { it.gameId } }
    return copy(art = art.filterKeys { it in referenced })
}

/** The entries on calendar days that have at least one unread entry — the days the list marks unread. */
private fun List<ItadNotification>.onUnreadDays(): List<ItadNotification> =
    groupBy { it.day }.values.filter { entries -> entries.any { !it.read } }.flatten()

/**
 * Mirrors the Notifications list's day rows: group by [ItadNotification.day], keep days with any unread entry,
 * and sum each day's **distinct** games across all of that day's entries (read ones included — the row's
 * "· N games" counts them too). Kept pure so the parity with the list is unit-testable.
 */
internal fun unreadGameCount(notifications: List<ItadNotification>, gameIdsByEntry: Map<String, Set<String>>): Int =
    notifications.groupBy { it.day }.values
        .filter { entries -> entries.any { !it.read } }
        .sumOf { entries -> entries.flatMapTo(mutableSetOf()) { gameIdsByEntry[it.id].orEmpty() }.size }
