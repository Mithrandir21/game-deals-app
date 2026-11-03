package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings

/**
 * Platform port for the single phone-local sale reminder: Android a one-time WorkManager job, iOS a pending
 * `UNNotificationRequest`. [schedule] replaces any pending reminder, so there is never more than one.
 */
interface WinterSaleReminderScheduler {
    fun schedule(delayMillis: Long)
    fun cancel()
}

/** Posts the reminder when the Android job fires; iOS posts it from the pending request without running app code. */
fun interface WinterSaleReminderPresenter {
    suspend fun present()
}

enum class WinterSaleReminderDecision { Show, Retry, Skip }

/**
 * The one Winter Sale reminder per event for users with alerts on: sale start plus a stable random 1–3 hours.
 * Every entry point is best-effort and never throws, so a reminder problem can't break Home, the guide or the alert poll.
 */
interface WinterSaleReminder {
    /** Fetches the event document and aligns the pending reminder with it and the alerts opt-in. */
    suspend fun sync()

    /** Aligns the pending reminder with an event state the caller already fetched. */
    suspend fun reconcile(state: WinterSaleEventState)

    /** Alerts were switched off. A later [sync] with alerts on schedules it again unless it already fired. */
    suspend fun cancel()

    /** A waitlist or series summary was just posted; drop a reminder due within the next hour. */
    suspend fun onSummaryPosted()

    /** Android fire-time check. [WinterSaleReminderDecision.Show] is returned at most once per event. */
    suspend fun claimDelivery(): WinterSaleReminderDecision
}

internal class WinterSaleReminderImpl(
    private val events: WinterSaleEventRepository,
    private val settings: NotificationSettings,
    private val scheduler: WinterSaleReminderScheduler,
    private val storage: Storage,
    private val clock: Clock,
    private val random: Random = Random.Default,
) : WinterSaleReminder {

    private val mutex = Mutex()

    override suspend fun sync() {
        val state = safely { events.refresh() } ?: return
        reconcile(state)
    }

    override suspend fun reconcile(state: WinterSaleEventState) {
        safely { mutex.withLock { reconcileLocked(state, settings.isEnabled()) } }
    }

    override suspend fun cancel() {
        safely { mutex.withLock { load()?.let { cancelPending(it) } } }
    }

    override suspend fun onSummaryPosted() {
        safely {
            mutex.withLock {
                val record = load() ?: return@withLock
                val fireAt = record.fireAtDeviceEpochMillis ?: return@withLock
                val untilFire = fireAt - clock.nowMillis()
                if (record.status == Status.Scheduled && untilFire in 0..SUMMARY_WINDOW_MILLIS) {
                    save(record.copy(status = Status.Done))
                    scheduler.cancel()
                }
            }
        }
    }

    override suspend fun claimDelivery(): WinterSaleReminderDecision {
        val state = safely { events.refresh() } ?: return WinterSaleReminderDecision.Retry
        return safely {
            mutex.withLock {
                val enabled = settings.isEnabled()
                val record = load()?.takeIf { it.status == Status.Scheduled } ?: return@withLock WinterSaleReminderDecision.Skip
                when {
                    !enabled -> { cancelPending(record); WinterSaleReminderDecision.Skip }
                    state is WinterSaleEventState.Hidden && state.reason == WinterSaleHiddenReason.Unavailable -> WinterSaleReminderDecision.Retry
                    state is WinterSaleEventState.Available && state.event.id == record.eventId && state.phase == WinterSalePhase.OnSale -> {
                        save(record.copy(status = Status.Done))
                        WinterSaleReminderDecision.Show
                    }
                    else -> {
                        // Fired early (clock skew, moved start), or the event went away: let the normal rules re-arm or cancel.
                        reconcileLocked(state, enabled, firing = true)
                        WinterSaleReminderDecision.Skip
                    }
                }
            }
        } ?: WinterSaleReminderDecision.Retry
    }

    private suspend fun reconcileLocked(state: WinterSaleEventState, enabled: Boolean, firing: Boolean = false) {
        val record = load()
        if (state is WinterSaleEventState.Hidden) {
            val gone = state.reason == WinterSaleHiddenReason.Disabled || state.reason == WinterSaleHiddenReason.Ended
            if ((gone || !enabled) && record != null) cancelPending(record)
            return
        }
        val available = state as WinterSaleEventState.Available
        val event = available.event
        val current = record?.takeIf { it.eventId == event.id } ?: Record(event.id, randomOffset(), Status.Idle).also {
            save(it)
            if (record?.status == Status.Scheduled) scheduler.cancel()
        }
        if (current.status == Status.Done) return
        if (!enabled) { cancelPending(current); return }

        val serverNow = available.observedServerNowEpochMillis
        val fireAt = event.saleStartEpochMillis + current.offsetMillis
        val alreadyDue = current.status == Status.Scheduled && current.fireAtServerEpochMillis?.let { serverNow >= it } == true
        if (fireAt >= event.endEpochMillis || serverNow >= fireAt || (alreadyDue && !firing)) {
            // Too late to remind: it already fired, or the user is in the app during the sale anyway.
            save(current.copy(status = Status.Done))
            if (current.status == Status.Scheduled) scheduler.cancel()
            return
        }
        val delay = fireAt - serverNow
        val fireAtDevice = clock.nowMillis() + delay
        val unchanged = current.status == Status.Scheduled && current.fireAtServerEpochMillis == fireAt &&
            current.fireAtDeviceEpochMillis?.let { abs(it - fireAtDevice) < RESCHEDULE_TOLERANCE_MILLIS } == true
        if (unchanged && !firing) return
        // Save first: on Android re-arming replaces, and so cancels, a firing job that is calling this.
        save(current.copy(status = Status.Scheduled, fireAtServerEpochMillis = fireAt, fireAtDeviceEpochMillis = fireAtDevice))
        scheduler.schedule(delay)
    }

    private suspend fun cancelPending(record: Record) {
        if (record.status != Status.Scheduled) return
        save(record.copy(status = Status.Idle, fireAtServerEpochMillis = null, fireAtDeviceEpochMillis = null))
        scheduler.cancel()
    }

    private fun randomOffset(): Long = random.nextLong(MIN_OFFSET_MILLIS, MAX_OFFSET_MILLIS + 1)

    private suspend fun load(): Record? = storage.getNullable<Record>(KEY)

    private suspend fun save(record: Record) {
        storage.save(KEY, record)
    }

    private suspend fun <T> safely(block: suspend () -> T): T? = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    @Serializable
    internal enum class Status { Idle, Scheduled, Done }

    @Serializable
    internal data class Record(
        val eventId: String,
        val offsetMillis: Long,
        val status: Status,
        val fireAtServerEpochMillis: Long? = null,
        val fireAtDeviceEpochMillis: Long? = null,
    )

    internal companion object {
        const val KEY = "winter_sale_reminder_v1"
        const val MIN_OFFSET_MILLIS = 60L * 60 * 1_000
        const val MAX_OFFSET_MILLIS = 3L * 60 * 60 * 1_000
        const val SUMMARY_WINDOW_MILLIS = 60L * 60 * 1_000
        const val RESCHEDULE_TOLERANCE_MILLIS = 60L * 1_000
    }
}
