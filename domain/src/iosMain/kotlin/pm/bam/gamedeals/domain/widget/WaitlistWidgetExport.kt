@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package pm.bam.gamedeals.domain.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToURL
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.error

/**
 * App Group shared by the app and the widget extension. Both targets' entitlements list it, and it has to be registered
 * in the Apple Developer account and enabled on both App IDs (pm.bam.gamedeals.ios and pm.bam.gamedeals.ios.widget).
 */
const val APP_GROUP_ID = "group.pm.bam.gamedeals"

/** File in the App Group container the Swift Waitlist deals widget reads. */
const val WAITLIST_WIDGET_FILE = "waitlist-deals.json"

/**
 * Swift-facing: starts keeping the Waitlist deals widget's file up to date. The app delegate calls it once at launch,
 * after Koin starts (also on background launches), passing a closure that reloads the widget's timeline — WidgetKit is
 * Swift-only. Every change to sign-in, the waitlist or its priced snapshot rewrites the file and reloads the widget.
 */
fun startWaitlistWidgetExport(reloadWidget: () -> Unit) = WaitlistWidgetExport.start(reloadWidget)

/**
 * Swift-facing: a Waitlist deals widget is on the Home Screen. The widget can't fetch prices itself (they need the
 * user's ITAD sign-in), so the app refreshes the waitlist snapshot when it is older than [STALE_AFTER_MILLIS] or priced
 * for another region, and from then on also when a newly waitlisted game has no price yet. Only called when a widget is
 * placed, so users without one add no ITAD traffic.
 */
fun onWaitlistWidgetInstalled() = WaitlistWidgetExport.onWidgetInstalled()

private const val STALE_AFTER_MILLIS = 3 * 60 * 60 * 1000L

private object WaitlistWidgetExport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { encodeDefaults = true }
    private var started = false

    // Set by onWidgetInstalled(); read on the collector's thread.
    private var widgetInstalled = false

    // The waitlist ids a "games missing from the snapshot" refresh last ran for. A game ITAD can't price would
    // otherwise trigger a refresh on every emission.
    private var refreshedForIds: Set<String>? = null

    fun start(reloadWidget: () -> Unit) {
        if (started) return
        started = true
        val koin = KoinPlatform.getKoin()
        val waitlist = koin.get<WaitlistRepository>()
        scope.launch {
            combine(
                koin.get<AuthTokenStore>().observeAuthState().map { it is AuthState.LoggedIn },
                waitlist.observeWaitlistIds(),
                waitlist.observeWaitlistDisplay(),
            ) { signedIn, ids, snapshot -> Triple(signedIn, ids, snapshot) }
                .distinctUntilChanged()
                .collect { (signedIn, ids, snapshot) ->
                    try {
                        // Waitlisting a game changes the ids but not the priced snapshot, so the new game would wait for
                        // the stale refresh; and signing in with a widget already placed leaves no snapshot at all (the
                        // launch-time refreshIfStale ran signed out). Price it now, once per id set; the result comes back
                        // through this collector.
                        val needsPricing = if (snapshot == null) ids.isNotEmpty() else snapshot.isMissingAny(ids)
                        if (widgetInstalled && signedIn && needsPricing && refreshedForIds != ids) {
                            refreshedForIds = ids
                            launch { refreshQuietly() }
                        }
                        val region = koin.get<RegionRepository>().getSelectedCountryCode()
                        if (write(buildWaitlistWidgetData(signedIn, region, snapshot, ids))) {
                            withContext(Dispatchers.Main) { reloadWidget() }
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (t: Throwable) {
                        error(koin.get<Logger>(), t) { "Waitlist widget export failed." }
                    }
                }
        }
    }

    fun onWidgetInstalled() {
        widgetInstalled = true
        refreshIfStale()
    }

    private fun refreshIfStale() {
        scope.launch {
            val koin = KoinPlatform.getKoin()
            try {
                if (koin.get<AuthTokenStore>().observeAuthState().first() !is AuthState.LoggedIn) return@launch
                val waitlist = koin.get<WaitlistRepository>()
                val snapshot = waitlist.observeWaitlistDisplay().first()
                val region = koin.get<RegionRepository>().getSelectedCountryCode()
                val age = koin.get<Clock>().nowMillis() - (snapshot?.refreshedAtEpochMs ?: 0L)
                // The refreshed snapshot flows back through start()'s collector, which rewrites the file.
                if (snapshot == null || snapshot.regionCode != region || age > STALE_AFTER_MILLIS) waitlist.refreshWaitlistDisplay()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                error(koin.get<Logger>(), t) { "Waitlist widget refresh failed." }
            }
        }
    }

    private suspend fun refreshQuietly() {
        val koin = KoinPlatform.getKoin()
        try {
            koin.get<WaitlistRepository>().refreshWaitlistDisplay()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            error(koin.get<Logger>(), t) { "Waitlist widget refresh for new games failed." }
        }
    }

    /** Writes [data] to the App Group container; false when the container is unavailable (App Group not provisioned). */
    private fun write(data: WaitlistWidgetData): Boolean {
        val logger = KoinPlatform.getKoin().get<Logger>()
        val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
        if (container == null) {
            debug(logger) { "App Group $APP_GROUP_ID unavailable; the Waitlist deals widget can't be updated." }
            return false
        }
        val file = container.URLByAppendingPathComponent(WAITLIST_WIDGET_FILE) ?: return false
        return NSString.create(string = json.encodeToString(WaitlistWidgetData.serializer(), data))
            .writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
