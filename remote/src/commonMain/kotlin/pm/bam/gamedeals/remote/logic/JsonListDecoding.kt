package pm.bam.gamedeals.remote.logic

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.serializer
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decodes a JSON array **element by element**, dropping (and logging) any element that fails to parse
 * rather than failing the whole batch.
 *
 * External feeds (ITAD, IGDB, GamerPower) change after launch; with a single `decodeFromString<List<T>>`
 * one malformed row or unknown enum value throws and the *entire* response is lost until an app update
 * ships. Use this only at high-value LIST boundaries — never for objects whose absence is itself a real
 * failure (e.g. an OAuth token response).
 *
 * Pairs with per-DTO nullable defaults + unknown-enum fallbacks: those let *more* rows decode; this
 * contains the blast radius of the rows that still can't.
 */
fun <T> Json.decodeListSkippingInvalid(
    array: JsonArray,
    elementDeserializer: DeserializationStrategy<T>,
    logger: Logger? = null,
    tag: String? = null,
): List<T> = array.mapNotNull { element ->
    try {
        decodeFromJsonElement(elementDeserializer, element)
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        logger?.log(LogLevel.WARN, tag, t) { "Dropping unparseable list element: ${t.message}" }
        null
    }
}

/** [decodeListSkippingInvalid] over a raw JSON string that is itself a top-level array. */
fun <T> Json.decodeListSkippingInvalid(
    rawJson: String,
    elementDeserializer: DeserializationStrategy<T>,
    logger: Logger? = null,
    tag: String? = null,
): List<T> {
    val array = parseToJsonElement(rawJson) as? JsonArray ?: return emptyList()
    return decodeListSkippingInvalid(array, elementDeserializer, logger, tag)
}

/**
 * Reads this response body as a top-level JSON array and decodes it with [decodeListSkippingInvalid].
 * Convenience for the list endpoints that return a bare array (GamerPower giveaways, IGDB game lists).
 */
suspend inline fun <reified T> HttpResponse.bodyAsListSkippingInvalid(
    json: Json,
    logger: Logger? = null,
    tag: String? = null,
): List<T> = json.decodeListSkippingInvalid(bodyAsText(), serializer<T>(), logger, tag)
