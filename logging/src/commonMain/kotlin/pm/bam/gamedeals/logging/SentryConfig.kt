package pm.bam.gamedeals.logging

import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb

/**
 * Shared Sentry [SentryOptions] configuration applied identically on Android and iOS, so the policy
 * (environment, sampling, PII, breadcrumb scrubbing) lives in one place and can't drift between
 * platforms. Only the values that differ per platform are passed in: [dsn], [release] and [dist] are
 * sourced from `BuildConfig` on Android and from the app bundle (`CFBundle*`) on iOS.
 *
 * Call from inside the platform `Sentry.init { options -> configureSentryOptions(options, …) }` block.
 */
fun configureSentryOptions(
    options: SentryOptions,
    dsn: String,
    release: String,
    dist: String,
    tracesSampleRate: Double,
    environment: String = "production",
) {
    options.dsn = dsn
    options.environment = environment
    options.release = release
    options.dist = dist
    // sentry-android-core's automatic activity/app-start transactions (and the iOS equivalents) ride this.
    options.tracesSampleRate = tracesSampleRate
    options.sendDefaultPii = false
    options.debug = false
    // Defence-in-depth: strip query strings off HTTP breadcrumb URLs so an ITAD api key / OAuth token
    // can never reach Sentry via a `?key=…` query param. (Headers are already excluded by sendDefaultPii.)
    options.beforeBreadcrumb = ::scrubBreadcrumb
}

/** Strips the query string from HTTP breadcrumb URLs; passes every other breadcrumb through untouched. */
private fun scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
    if (breadcrumb.category == "http") {
        (breadcrumb.getData()?.get("url") as? String)?.let { url ->
            breadcrumb.setData("url", url.substringBefore('?'))
        }
    }
    return breadcrumb
}
