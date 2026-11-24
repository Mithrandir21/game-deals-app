package pm.bam.gamedeals.domain.repositories.salecalendar

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.warn

/** One big seasonal sale, from the Worker's hand-typed `sales.json` (`store/site/sales.json`). */
@Serializable
data class BigSale(
    val id: String,
    /** "Steam", "Epic Games Store" or "GOG". */
    val store: String,
    val name: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
)

/** What a countdown shows: the sale running now, or the next one. */
sealed interface SaleCountdown {
    val sale: BigSale

    /** [sale] is on; it ends at its `endEpochMs`. */
    data class Running(override val sale: BigSale) : SaleCountdown

    /** [sale] is the next to start. */
    data class Upcoming(override val sale: BigSale) : SaleCountdown
}

/**
 * The running sale that ends soonest, else the next to start; null when there is neither. Two sales overlapping
 * (Epic's and Steam's winter sales) show the one ending first, so the most urgent countdown wins.
 */
fun List<BigSale>.countdownAt(nowEpochMs: Long): SaleCountdown? {
    filter { nowEpochMs in it.startEpochMs until it.endEpochMs }.minByOrNull { it.endEpochMs }
        ?.let { return SaleCountdown.Running(it) }
    return filter { it.startEpochMs > nowEpochMs }.minByOrNull { it.startEpochMs }?.let { SaleCountdown.Upcoming(it) }
}

/** Fetches `https://gamedeals.bam.pm/events/sales.json` as text. Throws on a failed call. */
interface SaleCalendarSource {
    suspend fun fetch(): String
}

/**
 * The big-sale dates for the countdown widgets and the iOS Live Activity. Kept on the phone, refreshed when older
 * than [REFRESH_AFTER_MS]; a failed refresh keeps the copy it has, since the dates rarely change.
 */
interface SaleCalendarRepository {
    suspend fun getSales(): List<BigSale>

    /** The countdown to show right now ([countdownAt] over [getSales]). */
    suspend fun currentCountdown(): SaleCountdown?

    companion object {
        const val REFRESH_AFTER_MS: Long = 6 * 60 * 60 * 1000
    }
}

internal class SaleCalendarRepositoryImpl(
    private val source: SaleCalendarSource,
    private val storage: Storage,
    private val json: Json,
    private val clock: Clock,
    private val logger: Logger,
) : SaleCalendarRepository {

    override suspend fun getSales(): List<BigSale> {
        val cached = runCatching { storage.getNullable<CachedSales>(CACHE_KEY) }.getOrNull()
        val now = clock.nowMillis()
        if (cached != null && now - cached.fetchedAtEpochMs < SaleCalendarRepository.REFRESH_AFTER_MS) return cached.sales
        val fresh = try {
            parseSales(source.fetch(), json)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warn(logger, error) { "Sale dates couldn't be loaded" }
            return cached?.sales.orEmpty()
        }
        runCatching { storage.save(CACHE_KEY, CachedSales(now, fresh)) }
        return fresh
    }

    override suspend fun currentCountdown(): SaleCountdown? = getSales().countdownAt(clock.nowMillis())

    @Serializable
    private data class CachedSales(val fetchedAtEpochMs: Long, val sales: List<BigSale>)

    private companion object {
        const val CACHE_KEY = "sale_calendar_cache"
    }
}

@Serializable
private data class RemoteSales(val schemaVersion: Int, val sales: List<RemoteSale> = emptyList())

@Serializable
private data class RemoteSale(val id: String, val store: String, val name: String, val start: String, val end: String)

/**
 * Reads `sales.json`. A document of an unknown schema version throws (the cached copy stays in use); a sale with an
 * unreadable date, or one that ends before it starts, is skipped rather than failing the rest.
 */
internal fun parseSales(text: String, json: Json): List<BigSale> {
    val document = json.decodeFromString(RemoteSales.serializer(), text)
    require(document.schemaVersion == 1) { "Unsupported sales schema ${document.schemaVersion}" }
    return document.sales.mapNotNull { sale ->
        val start = runCatching { Instant.parse(sale.start).toEpochMilliseconds() }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { Instant.parse(sale.end).toEpochMilliseconds() }.getOrNull() ?: return@mapNotNull null
        if (end <= start || sale.name.isBlank()) return@mapNotNull null
        BigSale(id = sale.id, store = sale.store, name = sale.name.trim(), startEpochMs = start, endEpochMs = end)
    }
}
