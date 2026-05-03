package pm.bam.gamedeals.remote.exceptions

/**
 * Transformation functionality allowing for transformation between specific Remote module [Throwable]s and module-external [Throwable]s.
 */
fun interface RemoteExceptionTransformer {

    /**
     * Transform API exceptions (e.g. Ktor's `ResponseException`) into the
     * transport-agnostic [RemoteHttpException] taxonomy that callers outside this
     * module can react to without depending on the underlying HTTP client.
     */
    fun transformApiException(throwable: Throwable): Throwable

}
