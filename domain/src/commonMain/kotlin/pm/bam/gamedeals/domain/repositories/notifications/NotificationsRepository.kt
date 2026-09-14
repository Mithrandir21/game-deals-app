package pm.bam.gamedeals.domain.repositories.notifications

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
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
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * The user's ITAD notifications (epic #272, P2 #277). Unlike the waitlist/collection there is **no Room
 * cache** (the data is cheap to refetch and transient): the list is held in memory, (re)loaded on demand
 * via [getNotifications].
 *
 * Notifications older than [RETENTION_MILLIS] (7 days) are **ignored at load** — dropped from the in-memory
 * list before it's published, so the list screen, [observeUnreadGameCount] and the background tray poll all see
 * the same recent window (ITAD has no delete API, so this client-side cutoff is the only "cleanup" possible;
 * stale drops aren't worth acting on, even when still unread). [observeUnreadGameCount] derives the badge tally
 * from that list and is auth-gated
 * — it emits `0` whenever logged out, mirroring
 * [WaitlistRepository.observeWaitlistIds][pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository.observeWaitlistIds].
 * Writes are **remote-first** (confirm the ITAD mark-read before flipping the in-memory state).
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
    suspend fun getNotifications(): List<ItadNotification>

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

    /**
     * The full deal content of a waitlist notification (games + deals + joined banner art) for the in-app
     * detail screen. Cached in-memory: a second open is served from cache, and the per-game art is joined
     * (and itself cached) from the user's waitlist — the only cheap art source. A fresh [getNotifications]
     * (remote-as-truth reload) invalidates these caches. Empty when logged out.
     */
    suspend fun getNotificationDetail(notificationId: String): NotificationDetail
}

/** How far back notifications are kept; anything older is ignored at load. */
internal const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

internal class NotificationsRepositoryImpl(
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val clock: Clock,
    private val analytics: Analytics,
) : NotificationsRepository {

    private val notifications = MutableStateFlow<List<ItadNotification>>(emptyList())

    // Notification id → the distinct game ids it refers to, recorded whenever a detail resolves. Deliberately
    // NOT dropped by the [getNotifications] reload (unlike [detailCache]): a notification's game set never
    // changes, and keeping it stops the badge flickering to 0 while the list screen re-resolves. Pruned to the
    // loaded ids on reload; cleared on logout.
    private val gameIdsByEntry = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    // Cache-from-sync + lazy-on-open: the per-notification detail is cached so a second open (or a tray
    // tap that the sync already warmed) is free. [waitlistArt] is the one-shot game-id → art join source,
    // cached for the process so a multi-notification sync fetches the waitlist once. Both are invalidated
    // by [getNotifications] (the remote-as-truth reload) and on logout.
    private val cacheMutex = Mutex()
    private val detailCache = mutableMapOf<String, NotificationDetail>()
    private var waitlistArt: Map<String, GameArtwork>? = null

    override fun observeNotifications(): Flow<List<ItadNotification>> =
        combine(authTokenStore.observeAuthState(), notifications) { authState, list ->
            if (authState is AuthState.LoggedIn) list else emptyList()
        }

    override fun observeUnreadGameCount(): Flow<Int> =
        combine(observeNotifications(), gameIdsByEntry, ::unreadGameCount)

    override suspend fun getNotifications(): List<ItadNotification> {
        invalidateDetailCache() // remote-as-truth reload: drop any stale detail/art so reopens re-fetch.
        if (!loggedIn()) {
            notifications.value = emptyList()
            gameIdsByEntry.value = emptyMap()
            return emptyList()
        }
        val list = accountSource.getNotifications().filter { it.isWithinRetention() }
        notifications.value = list
        val loadedIds = list.mapTo(mutableSetOf()) { it.id }
        gameIdsByEntry.update { resolved -> resolved.filterKeys { it in loadedIds } }
        return list
    }

    override suspend fun resolveUnreadGames() {
        if (!loggedIn()) return
        val pending = notifications.value.onUnreadDays().filter { it.id !in gameIdsByEntry.value }
        coroutineScope {
            pending.map { entry -> async { runCatching { getNotificationDetail(entry.id) } } }.awaitAll()
        }
    }

    /** True when the notification is within the [RETENTION_MILLIS] window. Unparseable timestamps are kept
     *  (don't hide a notification over a format quirk). */
    private fun ItadNotification.isWithinRetention(): Boolean {
        val millis = runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull() ?: return true
        return millis >= clock.nowMillis() - RETENTION_MILLIS
    }

    override suspend fun markRead(id: String) {
        if (!loggedIn()) return
        // Remote-first: confirm the ITAD write before updating the in-memory state.
        accountSource.markNotificationRead(id)
        notifications.update { list -> list.map { if (it.id == id) it.copy(read = true) else it } }
        analytics.capture(AnalyticsEvents.NOTIFICATION_MARKED_READ)
    }

    override suspend fun markAllRead() {
        if (!loggedIn()) return
        accountSource.markAllNotificationsRead()
        notifications.update { list -> list.map { it.copy(read = true) } }
        analytics.capture(AnalyticsEvents.NOTIFICATIONS_MARKED_ALL_READ)
    }

    override suspend fun getWaitlistGames(notificationId: String): List<NotificationGame> {
        if (!loggedIn()) return emptyList()
        return accountSource.getWaitlistNotificationGames(notificationId)
    }

    override suspend fun getNotificationDetail(notificationId: String): NotificationDetail {
        if (!loggedIn()) {
            invalidateDetailCache()
            gameIdsByEntry.value = emptyMap()
            return NotificationDetail(notificationId, emptyList())
        }
        cacheMutex.withLock { detailCache[notificationId] }?.let { return it.also(::recordGameIds) }

        // Fetch the deal content and the art map outside the lock (the art map is fetched at most once per
        // process); join art by game id, then memoise the joined detail.
        val detail = accountSource.getWaitlistNotificationDetail(notificationId)
        val art = cachedWaitlistArt()
        val joined = detail.copy(
            games = detail.games.map { game -> game.copy(artwork = art[game.gameId] ?: game.artwork) },
        )
        cacheMutex.withLock { detailCache[notificationId] = joined }
        recordGameIds(joined)
        return joined
    }

    private fun recordGameIds(detail: NotificationDetail) {
        val ids = detail.games.mapTo(mutableSetOf()) { it.gameId }
        gameIdsByEntry.update { it + (detail.notificationId to ids) }
    }

    private suspend fun cachedWaitlistArt(): Map<String, GameArtwork> =
        cacheMutex.withLock {
            waitlistArt ?: runCatching { accountSource.getWaitlist().associate { it.gameId to it.artwork } }
                .getOrDefault(emptyMap())
                .also { waitlistArt = it }
        }

    private suspend fun invalidateDetailCache() = cacheMutex.withLock {
        detailCache.clear()
        waitlistArt = null
    }

    private suspend fun loggedIn(): Boolean = authTokenStore.getAccessToken() != null
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
