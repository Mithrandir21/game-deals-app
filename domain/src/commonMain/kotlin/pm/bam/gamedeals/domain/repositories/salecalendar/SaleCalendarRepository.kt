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

/** How much a sale matters to the countdown. */
enum class SaleKind {
    /** A store-wide seasonal sale (Steam Winter, Epic Holiday). */
    BIG,

    /** A smaller themed sale (a Steam fest); it gives way to a big sale about to start. */
    FEST,
}

/** One sale, from the Worker's hand-typed `sales.json` (`store/site/sales.json`). */
@Serializable
data class StoreSale(
    val id: String,
    /** "Steam", "Epic Games Store" or "GOG". */
    val store: String,
    val name: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val kind: SaleKind = SaleKind.BIG,
)

/** What a countdown shows: the sale running now, or the next one. */
sealed interface SaleCountdown {
    val sale: StoreSale

    /** [sale] is on; it ends at its `endEpochMs`. */
    data class Running(override val sale: StoreSale) : SaleCountdown

    /** [sale] is the next to start. */
    data class Upcoming(override val sale: StoreSale) : SaleCountdown
}

/** A fest gives way to a big sale starting within this long. */
const val BIG_SALE_LEAD_MS: Long = 7L * 24 * 60 * 60 * 1000

/**
 * In order: the running big sale that ends soonest; a big sale starting within [BIG_SALE_LEAD_MS]; the running fest
 * that ends soonest; the next sale to start. Null when there is none. Two big sales overlapping (Epic's and Steam's
 * winter sales) show the one ending first, so the most urgent countdown wins.
 */
fun List<StoreSale>.countdownAt(nowEpochMs: Long): SaleCountdown? {
    fun runningOf(kind: SaleKind) =
        filter { it.kind == kind && nowEpochMs in it.startEpochMs until it.endEpochMs }.minByOrNull { it.endEpochMs }
    val upcoming = filter { it.startEpochMs > nowEpochMs }

    runningOf(SaleKind.BIG)?.let { return SaleCountdown.Running(it) }
    upcoming.filter { it.kind == SaleKind.BIG && it.startEpochMs - nowEpochMs <= BIG_SALE_LEAD_MS }
        .minByOrNull { it.startEpochMs }?.let { return SaleCountdown.Upcoming(it) }
    runningOf(SaleKind.FEST)?.let { return SaleCountdown.Running(it) }
    return upcoming.minByOrNull { it.startEpochMs }?.let { SaleCountdown.Upcoming(it) }
}

/** Fetches `https://gamedeals.bam.pm/events/sales.json` as text. Throws on a failed call. */
interface SaleCalendarSource {
    suspend fun fetch(): String
}

/**
 * The sale dates for the countdown widgets and the iOS Live Activity. Kept on the phone, refreshed when older than
 * [REFRESH_AFTER_MS]; a failed refresh keeps the copy it has, since the dates rarely change. With no copy yet, the
 * dates built into the app stand in, so the widget isn't blank on a first run without a connection.
 */
interface SaleCalendarRepository {
    suspend fun getSales(): List<StoreSale>

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
    private val bundledJson: String = BUNDLED_SALES_JSON,
) : SaleCalendarRepository {

    override suspend fun getSales(): List<StoreSale> {
        val cached = runCatching { storage.getNullable<CachedSales>(CACHE_KEY) }.getOrNull()
        val now = clock.nowMillis()
        if (cached != null && now - cached.fetchedAtEpochMs < SaleCalendarRepository.REFRESH_AFTER_MS) return cached.sales
        val fresh = try {
            parseSales(source.fetch(), json)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warn(logger, error) { "Sale dates couldn't be loaded" }
            return cached?.sales ?: bundledSales()
        }
        runCatching { storage.save(CACHE_KEY, CachedSales(now, fresh)) }
        return fresh
    }

    override suspend fun currentCountdown(): SaleCountdown? = getSales().countdownAt(clock.nowMillis())

    /** The copy of `sales.json` built into the app ([BUNDLED_SALES_JSON]); empty if it can't be read. */
    private fun bundledSales(): List<StoreSale> =
        runCatching { parseSales(bundledJson, json) }
            .onFailure { warn(logger, it) { "The built-in sale dates couldn't be read" } }
            .getOrDefault(emptyList())

    @Serializable
    private data class CachedSales(val fetchedAtEpochMs: Long, val sales: List<StoreSale>)

    private companion object {
        const val CACHE_KEY = "sale_calendar_cache"
    }
}

@Serializable
private data class RemoteSales(val schemaVersion: Int, val sales: List<RemoteSale> = emptyList())

@Serializable
private data class RemoteSale(
    val id: String,
    val store: String,
    val name: String,
    val start: String,
    val end: String,
    /** "big" or "fest"; missing, or a kind this app doesn't know, counts as big. */
    val kind: String? = null,
)

/**
 * Reads `sales.json`. A document of an unknown schema version throws (the cached copy stays in use); a sale with an
 * unreadable date, or one that ends before it starts, is skipped rather than failing the rest.
 */
internal fun parseSales(text: String, json: Json): List<StoreSale> {
    val document = json.decodeFromString(RemoteSales.serializer(), text)
    require(document.schemaVersion == 1) { "Unsupported sales schema ${document.schemaVersion}" }
    return document.sales.mapNotNull { sale ->
        val start = runCatching { Instant.parse(sale.start).toEpochMilliseconds() }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { Instant.parse(sale.end).toEpochMilliseconds() }.getOrNull() ?: return@mapNotNull null
        if (end <= start || sale.name.isBlank()) return@mapNotNull null
        StoreSale(
            id = sale.id,
            store = sale.store,
            name = sale.name.trim(),
            startEpochMs = start,
            endEpochMs = end,
            kind = if (sale.kind == "fest") SaleKind.FEST else SaleKind.BIG,
        )
    }
}
