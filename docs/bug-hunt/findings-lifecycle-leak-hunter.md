# Lifecycle & Memory-Leak Findings — Game Deals (Android)

Specialist: `android-bug-hunting-lifecycle-leak-hunter`
Scope: Android production source (`src/main`, `src/androidMain`) plus `commonMain` holding Android Context/lifecycle references. Test sources excluded.

## Summary

This codebase is unusually clean for lifecycle/leak defects. The Android-specific surface is small (two `ComponentActivity` subclasses, one `Application`, one `CoroutineWorker`, and a handful of DI-provided singletons), and the patterns that normally cause leaks are absent or handled correctly:

- **No** `Handler`/`postDelayed`, **no** `registerReceiver`, **no** `bindService`, **no** `registerListener`/`registerContentObserver`, **no** RxJava, **no** `WeakReference`, **no** Fragment `_binding`, **no** `LiveData.observe(this, …)`, **no** static/`object` field holding `Activity`/`View`/`Context`.
- All Android `Context`-holding singletons (`AndroidNotificationPresenter`, `AndroidNotificationScheduler`, `AndroidAuthBrowserLauncher`, `AndroidPlatformActions`, `SharedPreferencesBackend`) receive the **Application** context via Koin `androidContext(this@GameDealsApplication)` — not an Activity context. Verified at `GameDealsApplication.kt:74`.
- Every `addObserver`/`DisposableEffect` pair (`NotificationPermission.android.kt:51-57`) has a matching `removeObserver` in `onDispose`.
- All `GamePeekController.load(...)` callers pass `viewModelScope`; the controller is a private VM field that dies with the VM. The only `CoroutineScope(...)` constructed outside `Application` is `rememberCoroutineScope()` (composition-bound).
- `applicationScope` in `GameDealsApplication.kt:68` is process-lived by design and documented as such.

Two findings below are **Low** severity latent/defensive observations, both anchored to concrete lines. No Critical/High/Medium lifecycle or leak defects were found.

**Findings by severity:** Critical: 0 · High: 0 · Medium: 0 · Low: 2

---

### BUG-001: `AuthRedirectBus` static `CompletableDeferred` orphaned if the launcher coroutine is replaced without going through `clear()`

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle violation (static retained reference) |
| **Location** | `remote/itad/src/androidMain/kotlin/pm/bam/gamedeals/remote/itad/auth/oauth/AuthRedirectBus.kt:13` |
| **Effort** | Trivial |
| **Confidence** | Low |

**Description.** `AuthRedirectBus` is a process-level `object` holding `private var pending: CompletableDeferred<AuthRedirectResult>?`. `register()` overwrites `pending` unconditionally. If two `authorize()` flows ever overlap (or a flow's `finally { clear() }` is skipped), the bus can retain a stale `CompletableDeferred` for the life of the process.

**Impact.** The retained object is a bare `CompletableDeferred<AuthRedirectResult>` — it holds **no** Activity/Context/View — so the practical leak is a single small object, not a UI hierarchy. The more realistic symptom is a *logic* hazard: a stale deferred could be completed by a late redirect, or a second login could clobber an in-flight one. Not a memory-pressure concern.

**Evidence.**
```kotlin
object AuthRedirectBus {
    private var pending: CompletableDeferred<AuthRedirectResult>? = null
    fun register(deferred: CompletableDeferred<AuthRedirectResult>) {
        pending = deferred            // unconditional overwrite; no "already pending" guard
    }
    fun clear() { pending = null }
}
```
The normal path *is* safe: `AndroidAuthBrowserLauncher.authorize()` (`AndroidAuthBrowserLauncher.kt:22-31`) registers, awaits, and clears in a `finally`, so coroutine cancellation (e.g. `viewModelScope` teardown) nulls `pending`. This finding is only the residual risk of overlap / a future caller that doesn't use the `finally` wrapper.

**Recommended fix.** Either reject re-entrant registration (`require(pending == null)` or fail the previous deferred), or scope the deferred to the launcher instance instead of a process-global `object`. A defensive `pending?.cancel()` before reassigning in `register()` closes the orphan window:
```kotlin
fun register(deferred: CompletableDeferred<AuthRedirectResult>) {
    pending?.cancel()               // abandon any prior in-flight authorization
    pending = deferred
}
```

**Confidence rationale.** Low because the only reachable caller (`AndroidAuthBrowserLauncher`) already clears in `finally`, so under current usage there is no leak and no overlap. Flagged for human judgment as a latent issue that becomes real if a second concurrent `authorize()` is ever introduced. The retained type holds no Context, so even in the worst case it is not a classic memory leak.

---

### BUG-002: `NotificationRouteBus.deliver()` can buffer an undeliverable route while no `NavGraph` collector is active

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Lifecycle violation (buffered hand-off across collector lifetime) |
| **Location** | `app/src/main/java/pm/bam/gamedeals/notifications/NotificationRouteBus.kt:22-27`; collector at `app/src/main/java/pm/bam/gamedeals/navigation/NavGraph.kt:54-61` |
| **Effort** | Small |
| **Confidence** | Low |

**Description.** `NotificationRouteBus` is a process-level `object` backed by `Channel<NotificationRoute>(Channel.BUFFERED)`, consumed via `receiveAsFlow()`. The single collector lives inside `NavGraph`'s `LaunchedEffect(Unit)` (`NavGraph.kt:54`). `NavGraph` is only composed once `startDestination` resolves (`MainActivity.kt:33-36`), and is **not** composed at all while the onboarding flow short-circuits or before the Storage read completes. A `deliver()` that fires when no collector is attached parks the route in the channel buffer until *some* future collector drains it.

**Impact.** Not a memory leak (the buffered item is a tiny `data object`). The user-facing risk is a **stale deferred navigation**: a notification tapped during a window with no active collector (e.g. cold-start tap that lands on onboarding, or a tap delivered between Activity recreation and recomposition) is replayed the next time `NavGraph` is composed, potentially navigating the user unexpectedly on a later launch. `receiveAsFlow()` guarantees single consumption, so it won't double-navigate, but the *timing* is decoupled from intent.

**Evidence.**
```kotlin
object NotificationRouteBus {
    private val channel = Channel<NotificationRoute>(Channel.BUFFERED)
    val routes: Flow<NotificationRoute> = channel.receiveAsFlow()
    fun deliver(route: NotificationRoute) { channel.trySend(route) }   // no collector ⇒ item parked
}
```
```kotlin
// NavGraph.kt — the only collector; absent until startDestination resolves & onboarding is past
LaunchedEffect(Unit) {
    NotificationRouteBus.routes.collect { route -> /* navigate */ }
}
```
The KDoc explicitly describes the buffer-until-subscribe behaviour as intentional for cold-start taps, so this is partly by design; the latent edge is the *onboarding / pre-resolution* window where the buffered route is replayed much later.

**Recommended fix.** If a route should expire when undeliverable, drop it on a timeout or clear the buffer when leaving a tab-bearing destination. Alternatively, gate `deliver()` so routes are only buffered once past onboarding. If the current "hold until NavGraph appears" semantics are genuinely desired, no change is needed — document the onboarding-window replay explicitly so it isn't mistaken for a bug later.

**Confidence rationale.** Low: this is a deliberate design (buffered cold-start hand-off) and the buffered payload holds no Context, so it is not a memory leak. Flagged because the replay-after-onboarding timing is a behavioural edge a reviewer should consciously accept or close. Whether it is a defect depends on product intent the analyzer cannot see.

---

## Detectors run and cleared (no findings)

| Detector | Result |
|---|---|
| D1 static/`object` field holding Context/Activity/View/Fragment | None — only DI singletons holding app Context |
| D2 singleton taking `Context` without `.applicationContext` | Clear — Koin `androidContext()` supplies Application context (`GameDealsApplication.kt:74`) |
| D3 Fragment ViewBinding after `onDestroyView` | N/A — no Fragments, no ViewBinding (single-activity Compose) |
| D4 `LiveData.observe(this, …)` in Fragment | N/A — no LiveData/Fragments |
| D5 listeners/receivers/sensors without unregister | Clear — none registered; lifecycle observer in `NotificationPermission.android.kt` has symmetric `removeObserver` |
| D6 inner/anonymous class held by long-lived object; Handler/postDelayed | Clear — no `Handler`/`postDelayed` |
| D7 `lifecycleScope` vs `viewLifecycleOwner.lifecycleScope` | N/A — no Fragments |
| D8 RxJava Disposable not disposed | N/A — no RxJava |
| D9 `bindService` without `unbindService` | Clear — none |
| D10 Cursor/Stream stored in long-lived field | Clear — none |
| D11 implicit Activity ref passed to threads | Clear — no raw `Thread`; `applicationScope` is process-scoped by design |
| D12 `WeakReference` misuse | N/A — none used |
| D13 ViewModel holding Context/Activity/View/Fragment | Clear — no VM holds a Context field; `GamePeekController` scoped to `viewModelScope` |
| D14 long-running op in `onCreate` without scope binding | Clear — `MainActivity.onCreate` uses `produceState`/`setContent` (composition-scoped); `Application` work on `applicationScope` |
