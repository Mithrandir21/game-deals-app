package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.Flow

/**
 * App-facing feature-flag seam. Deliberately tiny and provider-agnostic — a sibling of `Analytics`, not an
 * extension of it (reading flags and sending events are separate concerns). Feature code references the typed
 * [FeatureFlag] catalogue and never imports a flag-provider SDK; a concrete provider binding is swapped in Koin.
 * No remote provider is integrated today, so every build binds [NoOpFeatureFlags] and callers never need to
 * null-check.
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
    /**
     * Synchronous snapshot of [flag]'s **JSON payload**, or `null` when the flag carries none, nothing has
     * loaded yet, or the provider has no remote config. Callers parse it themselves (kotlinx-serialization),
     * so a flag can carry structured configuration and not just an on/off bit.
     *
     * Payloads cross this seam as JSON *text* on purpose. Providers hand back loosely-typed objects whose
     * concrete shape differs per platform — a Kotlin `Map` on Android, a bridged `NSDictionary` on iOS — so
     * each provider normalises to one well-defined representation rather than pushing that difference onto
     * every caller.
     */
    fun payload(flag: FeatureFlag): String?

    /**
     * Reactive view of [flag]'s JSON payload: emits `null` immediately, then re-emits whenever a [refresh]
     * delivers a new value. Distinct-until-changed. The payload counterpart of [observe].
     */
    fun observePayload(flag: FeatureFlag): Flow<String?>

    fun refresh()
}
