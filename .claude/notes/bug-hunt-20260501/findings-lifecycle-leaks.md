# Lifecycle Leak Hunt — 2026-05-01

## Summary

Counts by severity: **Critical 0, High 0, Medium 0, Low 0**. No Android lifecycle / Activity / Fragment / Context leak defects found in `*/src/main/**/*.kt` outside the previously fixed WebView area.

## Scope reviewed

- **WebView fix (PR #67) verified.** `feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt:120-127`: `onRelease` correctly calls `stopLoading()`, swaps in a fresh `WebViewClient()` (releasing the captured Compose `loading` state lambda), loads `about:blank`, detaches from parent, calls `removeAllViews()` and `destroy()`. There are **no other `WebView` or `AndroidView` factory call sites** in main sources.
- **Injected `Context` is exclusively `@ApplicationContext`** — `app/src/main/java/pm/bam/gamedeals/di/AppModule.kt:51`, `common/src/main/java/pm/bam/gamedeals/common/di/CommonModule.kt:27`, `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:33,44`. No Activity/View-scoped Context captured by `@Singleton` provisions (Coil `ImageLoader`, SharedPreferences, Room).
- **All ViewModels are clean.** `HomeViewModel`, `GiveawaysViewModel`, `SearchViewModel`, `StoreViewModel`, `GameViewModel`, `DealDetailsViewModel` inject only repositories/loggers — no `Context`/`Activity`/`View` references; no `onCleared()` overrides leaving callbacks around.
- **Zero callback registrations.** No `BroadcastReceiver`, `ComponentCallbacks`, `OnSharedPreferenceChangeListener`, `NetworkCallback`, `OnBackPressedCallback`, or `ActivityLifecycleCallbacks` registrations exist in main sources, so there are no missing-unregister hazards.
- **No Fragment subclasses.** `LoggingBaseFragment` exists at `base/src/main/java/pm/bam/gamedeals/base/LoggingBaseFragment.kt` but is unused — no ViewBinding-after-`onDestroyView` hazards.
- **`LocalContext.current` usages are safe.** Confined to short-lived `context.getString(...)` calls in Composables (`feature/home/.../HomeScreen.kt:182`, `feature/search/.../SearchScreen.kt:143`, `feature/giveaways/.../GiveawaysScreen.kt:135`, `feature/game/.../GameScreen.kt:299`) and Material You color schemes in `common/ui/.../Theme.kt:263`.
- **Theme `LaunchedEffect` is null-guarded.** `common/ui/src/main/java/pm/bam/gamedeals/common/ui/theme/Theme.kt:271-281` checks `LocalActivity.current != null` before touching `activity.window` — no NPE / leak when hosted under non-Activity contexts (Previews, dialogs).
- **No rogue scopes/threads.** No `GlobalScope`, `MainScope()`, hand-rolled `CoroutineScope(...)`, `Handler`, `Thread`, `HandlerThread`, or `Timer` usages in main sources; all coroutines run under `viewModelScope` or composable-bound `LaunchedEffect`.
- **Single-Activity manifest is minimal.** `app/src/main/AndroidManifest.xml:19-28` — no exported services or content providers that could outlive the process and pin Activity references.

## Findings

None.