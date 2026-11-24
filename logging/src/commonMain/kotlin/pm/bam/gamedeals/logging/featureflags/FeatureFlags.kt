package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.Flow

/**
 * App-facing feature-flag seam. Deliberately tiny and provider-agnostic — a sibling of `Analytics`, not an
 * extension of it (reading flags and sending events are separate concerns). Feature code references the typed
 * [FeatureFlag] catalogue and never imports a provider; the binding is swapped in Koin. Apps bind the switch-file
 * reader in `:domain` (`RemoteFeatureFlags`, which reads the Worker's `/api/flags/v1`); tests bind a fake or
 * [NoOpFeatureFlags].
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

    /**
     * Starts a remote (re)load of all flags, updating what [observe] emits when it completes. Fire-and-forget; a
     * provider may skip it when it loaded recently. A no-op for [NoOpFeatureFlags].
     */
    fun refresh()

    /**
     * [refresh], waiting for it to finish (or be skipped). For background work that decides on the values right
     * after, such as a widget redraw or the background check. Never throws a load failure; the values stay as they were.
     */
    suspend fun awaitRefresh()
}
