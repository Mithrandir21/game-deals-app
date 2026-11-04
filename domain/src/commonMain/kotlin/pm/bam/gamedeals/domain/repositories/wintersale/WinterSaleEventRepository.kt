package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.logging.warn

data class WinterSaleEventPayload(val body: String, val serverDateEpochMillis: Long?)

interface WinterSaleEventSource {
    suspend fun fetch(): WinterSaleEventPayload
}

data class WinterSaleEvent(
    val id: String,
    val visibleFromEpochMillis: Long,
    val saleStartEpochMillis: Long,
    val endEpochMillis: Long,
    val title: String,
    val beforeSaleCopy: String,
    val duringSaleCopy: String,
)

enum class WinterSalePhase { BeforeSale, OnSale }

enum class WinterSaleHiddenReason { Disabled, BeforeVisibility, Ended, Unavailable }

sealed interface WinterSaleEventState {
    data class Available(
        val event: WinterSaleEvent,
        val phase: WinterSalePhase,
        val observedServerNowEpochMillis: Long,
        val usingCachedDocument: Boolean,
    ) : WinterSaleEventState

    data class Hidden(val reason: WinterSaleHiddenReason) : WinterSaleEventState
}

interface WinterSaleEventRepository {
    suspend fun refresh(): WinterSaleEventState
}

/** Fresh check for a deep link into the guide; any failure reads as unavailable. */
suspend fun WinterSaleEventRepository.isAvailable(): Boolean = try {
    refresh() is WinterSaleEventState.Available
} catch (error: CancellationException) {
    throw error
} catch (_: Throwable) {
    false
}

internal class WinterSaleEventRepositoryImpl(
    private val source: WinterSaleEventSource,
    private val storage: Storage,
    private val clock: Clock,
    private val json: Json,
    private val logger: Logger,
) : WinterSaleEventRepository {

    // One report per process: every Home open checks the document, and a bad one is the same fault each time.
    private var reportedBadDocument = false

    override suspend fun refresh(): WinterSaleEventState {
        val response = try {
            withTimeoutOrNull(FETCH_TIMEOUT_MILLIS) { source.fetch() }
                .also { if (it == null) warn(logger) { "Winter Sale event check timed out" } }
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            // Offline and timeouts are downgraded to breadcrumbs by the Sentry listener; anything else is an issue.
            error(logger, t)
            null
        }

        if (response != null) {
            when (val parsed = parse(response.body)) {
                ParsedDocument.Disabled -> {
                    persist(CachedDocument(response.body, response.serverDateEpochMillis, clock.nowMillis()))
                    return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled)
                }
                is ParsedDocument.Enabled -> {
                    val serverNow = response.serverDateEpochMillis
                    if (serverNow != null) {
                        persist(CachedDocument(response.body, serverNow, clock.nowMillis()))
                        return stateAt(parsed.event, serverNow, usingCache = false)
                    }
                    reportBadDocument("Winter Sale event response has no usable Date header")
                }
                ParsedDocument.Invalid -> reportBadDocument("Winter Sale event document is invalid")
            }
        }

        return cachedState()
    }

    private suspend fun cachedState(): WinterSaleEventState {
        val cached = try {
            storage.getNullable<CachedDocument>(CACHE_KEY)
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            error(logger, t)
            null
        } ?: return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)

        val parsed = parse(cached.body)
        if (parsed == ParsedDocument.Disabled) return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Disabled)
        if (parsed !is ParsedDocument.Enabled) return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)

        val deviceNow = clock.nowMillis()
        if (deviceNow < cached.deviceFetchedAtEpochMillis) return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)
        val elapsed = deviceNow - cached.deviceFetchedAtEpochMillis
        val serverDate = cached.serverDateEpochMillis
        if (elapsed < 0 || elapsed >= CACHE_LIFETIME_MILLIS || serverDate == null || serverDate > Long.MAX_VALUE - elapsed) {
            return WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)
        }
        return stateAt(parsed.event, serverDate + elapsed, usingCache = true)
    }

    private suspend fun persist(cached: CachedDocument) {
        try {
            if (storage.save(CACHE_KEY, cached)) return
            warn(logger) { "Winter Sale event document could not be cached" }
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            error(logger, t)
        }
        if (parse(cached.body) == ParsedDocument.Disabled) {
            try {
                storage.remove(CACHE_KEY)
            } catch (error: CancellationException) {
                throw error
            } catch (t: Throwable) {
                error(logger, t)
            }
        }
    }

    private fun reportBadDocument(message: String) {
        if (reportedBadDocument) return
        reportedBadDocument = true
        error(logger) { message }
    }

    private fun stateAt(event: WinterSaleEvent, serverNow: Long, usingCache: Boolean): WinterSaleEventState = when {
        serverNow < event.visibleFromEpochMillis -> WinterSaleEventState.Hidden(WinterSaleHiddenReason.BeforeVisibility)
        serverNow >= event.endEpochMillis -> WinterSaleEventState.Hidden(WinterSaleHiddenReason.Ended)
        else -> WinterSaleEventState.Available(
            event = event,
            phase = if (serverNow < event.saleStartEpochMillis) WinterSalePhase.BeforeSale else WinterSalePhase.OnSale,
            observedServerNowEpochMillis = serverNow,
            usingCachedDocument = usingCache,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun parse(body: String): ParsedDocument {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return ParsedDocument.Invalid
        if (runCatching { root["enabled"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == false) return ParsedDocument.Disabled
        val wire = runCatching { json.decodeFromJsonElement(WinterSaleEventWire.serializer(), root) }.getOrNull() ?: return ParsedDocument.Invalid
        if (!wire.enabled || wire.schemaVersion != SCHEMA_VERSION || wire.eventId != EVENT_ID) return ParsedDocument.Invalid
        if (wire.copy.title.isBlank() || wire.copy.beforeSale.isBlank() || wire.copy.duringSale.isBlank()) return ParsedDocument.Invalid
        val visibleFrom = runCatching { Instant.parse(wire.visibleFrom).toEpochMilliseconds() }.getOrNull() ?: return ParsedDocument.Invalid
        val saleStart = runCatching { Instant.parse(wire.saleStart).toEpochMilliseconds() }.getOrNull() ?: return ParsedDocument.Invalid
        val end = runCatching { Instant.parse(wire.end).toEpochMilliseconds() }.getOrNull() ?: return ParsedDocument.Invalid
        if (visibleFrom >= saleStart || saleStart >= end) return ParsedDocument.Invalid
        return ParsedDocument.Enabled(
            WinterSaleEvent(wire.eventId, visibleFrom, saleStart, end, wire.copy.title, wire.copy.beforeSale, wire.copy.duringSale),
        )
    }

    private sealed interface ParsedDocument {
        data class Enabled(val event: WinterSaleEvent) : ParsedDocument
        data object Disabled : ParsedDocument
        data object Invalid : ParsedDocument
    }

    private companion object {
        const val CACHE_KEY = "winter_sale_event_document_v1"
        const val EVENT_ID = "steam-winter-2026"
        const val SCHEMA_VERSION = 1
        const val FETCH_TIMEOUT_MILLIS = 2_000L
        const val CACHE_LIFETIME_MILLIS = 12L * 60 * 60 * 1_000
    }
}

@Serializable
private data class CachedDocument(val body: String, val serverDateEpochMillis: Long?, val deviceFetchedAtEpochMillis: Long)

@Serializable
private data class WinterSaleEventWire(
    val schemaVersion: Int,
    val eventId: String,
    val enabled: Boolean,
    val visibleFrom: String,
    val saleStart: String,
    val end: String,
    val copy: WinterSaleEventCopyWire,
)

@Serializable
private data class WinterSaleEventCopyWire(val title: String, val beforeSale: String, val duringSale: String)
