package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.Flow

/**
 * App-facing feature-flag seam. Deliberately tiny and provider-agnostic — a sibling of `Analytics`, not an
 * extension of it (reading flags and sending events are separate concerns). Feature code references the typed
 * [FeatureFlag] catalogue and never imports a flag-provider SDK; the concrete binding (the PostHog-backed
 * [PostHogFeatureFlags] today, or a future provider) is swapped in Koin. Bound to [NoOpFeatureFlags] whenever no
 * provider is configured (e.g. a build with an empty PostHog key), so callers never need to null-check.
 *
 * Remote providers deliver flags asynchronously over the network, so the two reads serve different needs:
 * [isEnabled] is a synchronous snapshot for one-shot decisions, while [observe] is reactive so UI updates the
 * moment a [refresh] lands (no app restart). Both fall back to [FeatureFlag.default] until a value is available.
 */
interface FeatureFlags {

    /**
     * Synchronous snapshot of [flag]. Returns the provider's currently-cached value, or [FeatureFlag.default]
     * if nothing has been loaded yet (cold start / offline / no provider). Cheap; safe to call on any thread.
     */
    fun isEnabled(flag: FeatureFlag): Boolean

    /**
     * Reactive view of [flag]: emits [FeatureFlag.default] immediately, then re-emits whenever a [refresh]
     * delivers a new value. Distinct-until-changed. Collect this (e.g. via a `StateFlow` in a ViewModel) when
     * the UI should react to a flag flipping at runtime.
     */
    fun observe(flag: FeatureFlag): Flow<Boolean>

    /**
     * Triggers a remote (re)load of all flags, updating what [observe] emits when it completes. Fire-and-forget;
     * a no-op for providers without remote config ([NoOpFeatureFlags]). Call once after the provider is set up,
     * and again whenever the targeting identity changes (e.g. after `identify`).
     */
    fun refresh()
}
