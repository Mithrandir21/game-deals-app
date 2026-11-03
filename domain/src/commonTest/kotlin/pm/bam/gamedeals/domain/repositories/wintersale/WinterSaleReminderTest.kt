package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinterSaleReminderTest {
    private var deviceNow = 5_000_000L
    private val events = FakeEvents()
    private val settings = FakeSettings()
    private val scheduler = RecordingScheduler()
    private val storage = ReminderMemoryStorage()

    private fun reminder(random: Random = FixedOffset(OFFSET)) =
        WinterSaleReminderImpl(events, settings, scheduler, storage, Clock { deviceNow }, random)

    @Test
    fun schedules_once_at_sale_start_plus_offset_measured_from_server_time() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.reconcile(available(serverNow = SALE_START - HOUR))

        assertEquals(listOf(HOUR + OFFSET), scheduler.scheduled)
        assertEquals(0, scheduler.cancels)
    }

    @Test
    fun offset_is_between_one_and_three_hours_and_kept_when_the_start_moves() = runTest {
        repeat(50) { seed ->
            val scheduler = RecordingScheduler()
            val reminder = WinterSaleReminderImpl(events, settings, scheduler, ReminderMemoryStorage(), Clock { deviceNow }, Random(seed))
            reminder.reconcile(available(serverNow = SALE_START - HOUR))
            reminder.reconcile(available(serverNow = SALE_START - HOUR, saleStart = SALE_START + 2 * HOUR))

            val (first, moved) = scheduler.scheduled
            assertTrue(first - HOUR in HOUR..3 * HOUR, "offset ${first - HOUR}")
            assertEquals(first + 2 * HOUR, moved)
        }
    }

    @Test
    fun a_device_clock_jump_reschedules_but_small_drift_does_not() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        deviceNow += 30_000
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        deviceNow += 2 * HOUR
        reminder.reconcile(available(serverNow = SALE_START - HOUR))

        assertEquals(listOf(HOUR + OFFSET, HOUR + OFFSET), scheduler.scheduled)
    }

    @Test
    fun alerts_off_never_schedules_and_turning_them_off_cancels() = runTest {
        settings.enabled = false
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        assertEquals(emptyList(), scheduler.scheduled)

        settings.enabled = true
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        settings.enabled = false
        reminder.cancel()
        assertEquals(1, scheduler.cancels)

        settings.enabled = true
        events.state = available(serverNow = SALE_START - HOUR)
        reminder.sync()
        assertEquals(2, scheduler.scheduled.size)
    }

    @Test
    fun alerts_switched_off_elsewhere_cancel_on_the_next_reconcile() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        settings.enabled = false
        reminder.reconcile(WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable))

        assertEquals(1, scheduler.cancels)
    }

    @Test
    fun disabled_or_ended_event_cancels_and_a_re_enabled_document_schedules_again() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.reconcile(WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled))
        assertEquals(1, scheduler.cancels)

        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        assertEquals(2, scheduler.scheduled.size)

        reminder.reconcile(WinterSaleEventState.Hidden(WinterSaleHiddenReason.Ended))
        assertEquals(2, scheduler.cancels)
    }

    @Test
    fun an_unreachable_or_not_yet_visible_document_leaves_the_reminder_alone() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.reconcile(WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable))
        reminder.reconcile(WinterSaleEventState.Hidden(WinterSaleHiddenReason.BeforeVisibility))

        assertEquals(0, scheduler.cancels)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun once_the_fire_time_passes_it_is_never_scheduled_again_for_that_event() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.reconcile(available(serverNow = SALE_START + OFFSET + 1))
        reminder.reconcile(available(serverNow = SALE_START + OFFSET + 2, saleStart = SALE_START + 5 * HOUR))

        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun a_first_open_after_the_fire_time_schedules_nothing() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START + OFFSET))
        reminder.reconcile(available(serverNow = SALE_START + OFFSET, saleStart = SALE_START + 5 * HOUR))

        assertEquals(emptyList(), scheduler.scheduled)
    }

    @Test
    fun a_new_event_id_replaces_the_old_reminder() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.reconcile(available(serverNow = SALE_START - HOUR, id = "next"))

        assertEquals(1, scheduler.cancels)
        assertEquals(2, scheduler.scheduled.size)
    }

    @Test
    fun a_summary_within_the_hour_before_the_reminder_drops_it_for_good() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        deviceNow += HOUR + OFFSET - 30 * MINUTE
        reminder.onSummaryPosted()
        reminder.reconcile(available(serverNow = SALE_START + OFFSET - 30 * MINUTE))

        assertEquals(1, scheduler.cancels)
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun an_earlier_summary_keeps_the_reminder() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.onSummaryPosted()

        assertEquals(0, scheduler.cancels)
    }

    @Test
    fun claim_shows_once_during_the_sale() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        events.state = available(serverNow = SALE_START + OFFSET)

        assertEquals(WinterSaleReminderDecision.Show, reminder.claimDelivery())
        assertEquals(WinterSaleReminderDecision.Skip, reminder.claimDelivery())
        reminder.reconcile(available(serverNow = SALE_START - HOUR, saleStart = SALE_START + 5 * HOUR))
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun claim_skips_a_disabled_event_or_alerts_turned_off() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        events.state = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled)
        assertEquals(WinterSaleReminderDecision.Skip, reminder.claimDelivery())

        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        settings.enabled = false
        events.state = available(serverNow = SALE_START + OFFSET)
        assertEquals(WinterSaleReminderDecision.Skip, reminder.claimDelivery())
        assertEquals(2, scheduler.cancels)
    }

    @Test
    fun claim_retries_when_the_document_cannot_be_checked() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        events.state = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)
        assertEquals(WinterSaleReminderDecision.Retry, reminder.claimDelivery())

        events.fail = true
        assertEquals(WinterSaleReminderDecision.Retry, reminder.claimDelivery())
    }

    @Test
    fun claim_before_the_sale_re_arms_for_the_right_time() = runTest {
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        events.state = available(serverNow = SALE_START - 10 * MINUTE)

        assertEquals(WinterSaleReminderDecision.Skip, reminder.claimDelivery())
        assertEquals(listOf(HOUR + OFFSET, 10 * MINUTE + OFFSET), scheduler.scheduled)
    }

    @Test
    fun storage_failures_never_escape() = runTest {
        storage.fail = true
        val reminder = reminder()
        reminder.reconcile(available(serverNow = SALE_START - HOUR))
        reminder.onSummaryPosted()
        reminder.cancel()

        assertEquals(WinterSaleReminderDecision.Retry, reminder.claimDelivery())
    }

    private fun available(serverNow: Long, saleStart: Long = SALE_START, id: String = "winter") = WinterSaleEventState.Available(
        WinterSaleEvent(id, saleStart - 7 * DAY, saleStart, saleStart + 18 * DAY, "Winter", "Before", "During"),
        if (serverNow < saleStart) WinterSalePhase.BeforeSale else WinterSalePhase.OnSale,
        serverNow,
        usingCachedDocument = false,
    )

    private class FixedOffset(private val offset: Long) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextLong(from: Long, until: Long): Long = offset
    }

    private class FakeEvents : WinterSaleEventRepository {
        var state: WinterSaleEventState = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)
        var fail = false
        override suspend fun refresh(): WinterSaleEventState = if (fail) error("offline") else state
    }

    private class FakeSettings : NotificationSettings {
        var enabled = true
        override fun observeEnabled(): Flow<Boolean> = flowOf(enabled)
        override suspend fun isEnabled() = enabled
        override suspend fun setEnabled(enabled: Boolean) { this.enabled = enabled }
    }

    private class RecordingScheduler : WinterSaleReminderScheduler {
        val scheduled = mutableListOf<Long>()
        var cancels = 0
        override fun schedule(delayMillis: Long) { scheduled += delayMillis }
        override fun cancel() { cancels++ }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
        const val SALE_START = 1_797_530_400_000L
        const val OFFSET = 90 * MINUTE
    }
}

private class ReminderMemoryStorage : Storage {
    private val values = mutableMapOf<String, Any>()
    var fail = false

    override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
        getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("Missing $storageKey")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? {
        if (fail) error("disk")
        return values[storageKey] as? T ?: defaultValue
    }

    override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
        if (fail) error("disk")
        values[storageKey] = data
        return true
    }

    override suspend fun containsKey(storageKey: String): Boolean = storageKey in values
    override suspend fun remove(storageKey: String): Boolean = values.remove(storageKey) != null
}
