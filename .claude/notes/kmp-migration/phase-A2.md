# Phase A2 — `:feature:webview` to KMP + iOS WKWebView actual

**Branch:** `feature/kmp-migration-phase-A2-ios-webview`
**Started:** 2026-05-04
**Status:** Build-green on Android + iOS framework; iOS simulator manually verified (page loads in WKWebView).

## What was done

The last feature module still living in `androidMain/` got its commonMain
move and its iOS actual. Pure mechanical port for the Android side; the
iOS actual needed a real WKWebView implementation per Decision **C1** in
PLAN.md.

### Sources moved to `commonMain`
- `WebView.kt` — split into a commonMain Composable + an `expect fun PlatformWebView(url, onLoadingChange, modifier)` for the native rendering surface.
- `navigation/WebViewNavigation.kt` — moved verbatim; uses `Destination.WebView` from `:common`.
- `composeResources/drawable/browser.xml` — moved from `androidMain/res/drawable/`.
- `composeResources/values/strings.xml` — moved from `androidMain/res/values/`.

### Android actual (`androidMain/.../WebView.android.kt`)
Existing `AndroidView`-hosted `android.webkit.WebView` extracted into the actual. Logic unchanged — `WebViewClient` callbacks (onPageStarted/Finished, onReceivedError/HttpError) still drive `onLoadingChange`, sub-frame errors still don't clear loading. `lastLoadedUrl` recompose guard preserved. `onRelease` still does the full teardown (`stopLoading`, swap to a no-op client, `loadUrl("about:blank")`, remove from parent, `removeAllViews`, `destroy`).

### iOS actual (`iosMain/.../WebView.ios.kt`)
`UIKitView` hosting a `WKWebView` with a `WKNavigationDelegate` that maps main-frame events to `onLoadingChange`. Key implementation details:

- **Navigation delegate is a `NSObject` + `WKNavigationDelegateProtocol`.** All four overloads (`didStartProvisionalNavigation`, `didFinish`, `didFail`, `didFailProvisionalNavigation`) carry `@ObjCSignatureOverride` because they share the `webView` selector base name; without it Kotlin/Native rejects the multi-actual-of-same-selector pattern.
- **WKWebView is opaque with a forced white background.** Earlier attempt set `setOpaque(false) + setBackgroundColor(UIColor.whiteColor)` to avoid a "dark flash" before paint — this turned out to be the cause of the "WebView is just black" report. With a non-opaque layer the dark Compose host bled through and (depending on layer compositing) the page never appeared. Fix: keep the view opaque, set background white on both the view and `scrollView`.
- **`loadRequest` runs in `update`, not `factory`.** During `factory` the WKWebView has no frame yet (CMP sizes it later via the layout pass). Loading a URL into a zero-frame WKWebView paints nothing on iOS even after the frame is later assigned. Moving `loadRequest` to `update` (with the same `lastLoadedUrl` guard the Android side uses) makes the load happen against an in-hierarchy, sized view.
- **`onRelease`**: `stopLoading()` + null the navigation delegate. Lighter than the Android teardown because WKWebView doesn't accumulate the same lifecycle state.

### iOS app wiring (`iosApp/`)
- `iosApp/build.gradle.kts` adds `implementation(project(":feature:webview"))`.
- `MainViewController.kt` drops the inline `composable<Destination.WebView>` placeholder + the `IosPlaceholder` helper (and their now-unused imports), and replaces them with `webViewScreen(onBack = { navController.popBackStack() })` — the same registration the Android side uses.

### Defensive sizing fix in commonMain
`WebView.kt` passes `Modifier.fillMaxSize().padding(contentPadding)` to `PlatformWebView` instead of just `Modifier.padding(contentPadding)`. AndroidView/WebView already fill via `View.onMeasure` so this is a no-op on Android; UIKitView+WKWebView (intrinsic size = `CGSizeZero`) can otherwise wrap to nothing inside a Scaffold content slot.

### Test changes (`androidInstrumentedTest/.../WebViewTest.kt`)
- Dropped `R.string.*` lookups + `InstrumentationRegistry.getInstrumentation().targetContext`. Strings ("Back", "Open in browser") are now hard-coded in the test, mirroring the values declared in `commonMain/composeResources/values/strings.xml`. Reason: `Res.string.*` is a suspending API not usable in test setup, and `R.string` no longer exists once strings move to `composeResources`.
- All five tests preserved as-is (title display + back, open-in-browser, main-frame error clears loading, main-frame http error clears loading, sub-frame error keeps loading).

## Build verification

| Task | Result |
|---|---|
| `:feature:webview:compileDebugKotlinAndroid` | ✅ |
| `:feature:webview:compileKotlinIosSimulatorArm64` | ✅ |
| `:iosApp:compileKotlinIosSimulatorArm64` | ✅ |
| iOS Simulator: navigate to a deal → "Open" → WebView | ✅ (page renders) |

Instrumented tests + full `:app:assembleDebug` not yet re-run on this branch.

## Deviations from the plan

- **No `expect class WebViewHost`** as PLAN.md sketched. The actual shape that survived contact with WKWebView is `expect @Composable fun PlatformWebView(...)` — a single composable, no class. The decision to surface load state through `onLoadingChange: (Boolean) -> Unit` rather than a host object kept the `expect` surface tiny and matches what each platform's native delegate already produces.
- **No iOS-side ATS exception added.** PLAN.md doesn't mention ATS, and the URLs the app passes (CheapShark `/redirect?dealID=…`) are HTTPS, so the default ATS policy is fine. If a deal redirect chains to an HTTP store URL we'll see it as `didFailProvisionalNavigation` — at which point an `NSAppTransportSecurity` exception in `iosApp/iosApp/Info.plist` would be the right fix, but it's deferred until that's actually observed.

## Lessons (candidates for `.claude/lessons.md`)

- **WKWebView in CMP: load in `update`, not `factory`.** The `factory` lambda runs before the host sizes the view. WKWebView with frame=zero accepts `loadRequest` silently and paints nothing even after the frame is later assigned. Move every `WKWebView.loadRequest` to `update` (gated by an URL-change check to avoid reloading on every recomposition).
- **WKWebView opacity tradeoff.** `setOpaque(false)` is a tempting "fix" for the dark-default-bg flash but breaks page rendering in dark mode (host shows through, paints look like nothing happened). Correct shape: keep opaque (default) + `setBackgroundColor(UIColor.whiteColor)` on both the view and its `scrollView`.
- **CMP 1.10.3 `UIKitView` has no `background` parameter.** The `background: Color` parameter exists on `UIKitViewController`, not `UIKitView`. Don't try to use it for a UIKitView host — the compile error is `No parameter with name 'background' found.`
- **`Res.string.*` is suspending; tests need a different strategy.** When migrating an Android-only `R.string` test to a feature whose strings live in `composeResources`, the lowest-friction approach is to hard-code the literal in the test (mirroring `strings.xml`). `runBlocking { Res.string.X.getString() }` works but adds suspension boilerplate to every Compose test; not worth it for a stable string asset.
