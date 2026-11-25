package pm.bam.gamedeals.common.exceptions

/**
 * A failed call that got an HTTP answer. The network layer's exceptions implement it, so code that can't see that
 * layer (domain, features) can still tell "not found" or "rate limited" apart from other failures.
 */
interface HttpStatusFailure {
    val httpStatus: Int
}

/** The status of the first [HttpStatusFailure] in this throwable or its causes, or null when there is none. */
fun Throwable.httpStatusOrNull(): Int? {
    var cause: Throwable? = this
    repeat(MAX_CAUSE_DEPTH) {
        val current = cause ?: return null
        if (current is HttpStatusFailure) return current.httpStatus
        cause = current.cause?.takeUnless { it === current }
    }
    return null
}

/** The server answered 404: the thing asked for doesn't exist. */
fun Throwable.isHttpNotFound(): Boolean = httpStatusOrNull() == HTTP_NOT_FOUND

/** The server answered 429: too many calls for now, worth trying again later. */
fun Throwable.isRateLimited(): Boolean = httpStatusOrNull() == HTTP_TOO_MANY_REQUESTS

private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val MAX_CAUSE_DEPTH = 8
