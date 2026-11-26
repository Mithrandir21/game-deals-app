---
**Path scope:** **/src/commonMain/kotlin/**, **/src/androidMain/kotlin/**, **/src/iosMain/kotlin/**, **/src/commonTest/kotlin/**, **/src/androidHostTest/kotlin/**, **/src/androidDeviceTest/kotlin/**, iosApp/**, **/composeResources/**
**Last surveyed:** 34b01013 on 2026-05-18
---

# Kotlin Multiplatform

This category owns the common/platform boundary itself — the mechanics of `expect`/`actual`, the source-set anatomy every library module shares, how the iOS app is composed, how Kotlin meets Swift, and how shared Compose resources are wired. It is deliberately narrow: sibling categories own their own KMP-touched patterns. See `di.md` for the Koin DSL and how modules are registered; `data.md` for Ktor client specifics; `testing.md` for the triple test source-set layout and `commonTest` conventions; `build.md` for the convention plugins that configure every KMP module; `observability.md` for the per-platform logging listeners; and `compose-correctness.md` for Compose-specific rules that apply to shared UI. When in doubt, this file is the cross-reference root.

## Patterns

### Expect/Actual Platform Abstractions via Sibling Files

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** HTTP client factory, Ktor logger, build-util, Compose theme, platform actions.

**The pattern.** Every platform-specific binding has an `expect` declaration in `commonMain` and `actual` implementations in `androidMain` and `iosMain`. File naming follows a sibling-file convention: the `expect` lives in `…/PlatformThing.kt`; the actuals are sibling files named `PlatformThing.android.kt` and `PlatformThing.ios.kt` (rather than separate directory hierarchies under each source set). Cross-platform call sites import from `commonMain` and never see the platform branches.

**Why this works for us.** The `.android.kt` / `.ios.kt` suffix makes the platform a file-level concern that is grep-able and discoverable — you can find every platform binding from one search. `expect`/`actual` gives compile-time enforcement that every active target has an implementation; a missing platform fails the build instead of the runtime.

**Known trade-offs / when it strains.** Each piece of platform code costs an extra `expect` declaration plus two actual files. The sibling-file naming is a convention; Kotlin and Gradle don't enforce it, so it has to be maintained by review. When the platform API surface gets large, the temptation to flatten everything into one fat `expect` interface grows — resist it; keep `expect` surfaces small.

**How to apply it.**
```kotlin
// commonMain/.../HttpClientFactory.kt
expect fun httpClient(): HttpClient

// androidMain/.../HttpClientFactory.android.kt
actual fun httpClient(): HttpClient = HttpClient(OkHttp) {
    // OkHttp-specific config
}

// iosMain/.../HttpClientFactory.ios.kt
actual fun httpClient(): HttpClient = HttpClient(Darwin) {
    // Darwin-specific config
}
```

**Seen in.**
- `remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.kt`
- `remote/src/androidMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.android.kt`
- `remote/src/iosMain/kotlin/pm/bam/gamedeals/remote/logic/HttpClientFactory.ios.kt`
- `remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.kt`
- `remote/src/androidMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.android.kt`
- `remote/src/iosMain/kotlin/pm/bam/gamedeals/remote/logic/KtorPlatformLogger.ios.kt`
- `common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt`
- `common/ui/src/androidMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt`
- `common/ui/src/iosMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt`
- `common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/platform/PlatformActions.kt`
- `common/ui/src/androidMain/kotlin/pm/bam/gamedeals/common/ui/platform/PlatformActions.android.kt`

**Related lessons.** L-2026-05-04-06, L-2026-05-15-01, L-2026-05-15-02, L-2026-05-15-07

**Tags:** `kmp`, `expect-actual`, `platform-abstraction`

---

### KMP Source-Set Anatomy (commonMain / androidMain / iosMain + paired tests)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Every KMP library module in the repo.

**The pattern.** Every library module is laid out the same way: `src/commonMain/kotlin/` for shared code, `src/androidMain/kotlin/` for Android-only code, `src/iosMain/kotlin/` for iOS-only code, plus `src/commonTest/kotlin/` for shared tests, `src/androidHostTest/kotlin/` for JVM-hosted unit tests, and `src/androidDeviceTest/kotlin/` for instrumented device tests. Compose resources live at `src/commonMain/composeResources/`. There is no more `src/main/java/` or `src/test/` in library modules.

**Why this works for us.** One uniform shape lets engineers move between modules without re-learning the layout — `common/`, `remote/`, `feature/home/` all have identical skeletons. The source-set names match exactly what the Kotlin Multiplatform Gradle plugin expects, so there is no custom Gradle wiring per module to tell the build where to find sources.

**Known trade-offs / when it strains.** There are more directories per module than in a typical Android-only project, and engineers new to KMP need to learn which set is canonical for shared vs platform code. The `androidHostTest` vs `androidDeviceTest` split (see `testing.md`) is also new vocabulary versus the old `test/` vs `androidTest/` distinction.

**How to apply it.** When adding a new library module, generate this directory tree from scratch. Default new files to `commonMain` unless they need platform-specific behavior — only drop into `androidMain`/`iosMain` when an `expect` declaration forces it. Note that `:app` is still Android-only and keeps `src/main/kotlin/` + `src/androidTest/`.

**Seen in.**
- Every KMP library module: `common/`, `common/ui/`, `domain/`, `logging/`, `remote/`, `remote/cheapshark/`, `remote/gamerpower/`, and all `feature/*` modules.
- Counter-example (Android-only): `app/` retains the standard `src/main/kotlin/` + `src/androidTest/` layout.

**References.** `testing.md` for the test source-set patterns; `build.md` for the convention plugins that configure each source set.

**Related lessons.** L-2026-05-17-08, L-2026-05-17-15

**Tags:** `kmp`, `source-sets`, `module-layout`

---

### Platform-Suffixed DI Modules per Layer

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `common`, `common/ui`, `domain`, `logging`, `remote` (sub-modules vary).

**The pattern.** Layers that need platform-specific bindings ship a `commonModule` plus per-platform companions: `commonIosModule`, `commonAndroidModule`, `loggingAndroidModule`, `loggingIosModule`, `domainIosModule`, `remoteIosModule`, and so on. The suffix is always `{Android|Ios}Module`. The platform modules are passed into `startKoin(modules = …)` only on their respective platform — the Android bootstrap composes the Android set, the iOS bootstrap composes the iOS set — without conditional logic in shared code.

**Why this works for us.** Platform-specific bindings stay out of `commonMain`. The suffix is grep-able, so finding "what does iOS bind for logging?" is a search away. Both bootstrap sites end up with a simple flat list of modules to register — no `when (Platform.current)` branching inside the DI graph.

**Known trade-offs / when it strains.** More files per layer than a single-platform Koin setup. The convention is purely a naming discipline; nothing in Koin enforces the `…IosModule` / `…AndroidModule` suffix, so review is the gate.

**How to apply it.**
```kotlin
// commonMain/.../di/CommonModule.kt
val commonModule = module {
    single<SomeSharedThing> { SomeSharedThingImpl() }
}

// androidMain/.../di/CommonAndroidModule.kt
val commonAndroidModule = module {
    single<PlatformBinding> { AndroidPlatformBinding(get()) }
}

// iosMain/.../di/CommonIosModule.kt
val commonIosModule = module {
    single<PlatformBinding> { IosPlatformBinding() }
}
```

**Seen in.**
- `common/src/androidMain/kotlin/pm/bam/gamedeals/common/di/CommonAndroidModule.kt`
- `common/src/iosMain/kotlin/pm/bam/gamedeals/common/di/CommonIosModule.kt`
- `logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/di/LoggingModule.kt`
- `logging/src/iosMain/kotlin/pm/bam/gamedeals/logging/di/LoggingIosModule.kt`
- `domain/src/iosMain/kotlin/pm/bam/gamedeals/domain/di/DomainIosModule.kt`

**References.** `di.md` for the Koin DSL itself and the broader DI shape.

**Tags:** `kmp`, `koin`, `platform-modules`

---

### iOS UIViewController Host with Compose Entry-Point

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** iOS app composition root.

**The pattern.** The iOS app entry is a Kotlin `MainViewController()` function in `iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt` that returns a `UIViewController` built from `ComposeUIViewController { … }`. Inside, it calls `bootstrapKoin()` (loading the same module list as Android plus the iOS-specific platform modules) and then composes the shared `GameDealsApp` tree. The Swift host in `iosApp/iosApp/` wraps this `UIViewController` with a `UIViewControllerRepresentable` SwiftUI bridge.

**Why this works for us.** Gives iOS a parallel composition root to Android's `MainActivity` — both platforms have a single, well-known place where DI starts and the Compose tree begins. The same Compose UI runs on both platforms; Swift code stays minimal and is only responsible for the UIKit-to-SwiftUI bridge.

**Known trade-offs / when it strains.** Compose-only errors surface in Swift via opaque crash messages — debugging requires Xcode plus the Kotlin/Native debugger. The Koin bootstrap is hand-maintained in two places (Android `MainActivity` / `Application`, plus the iOS `MainViewController`); keep the two module lists in sync as new layers are added.

**How to apply it.**
```kotlin
// iosApp/src/iosMain/kotlin/.../MainViewController.kt
fun MainViewController(): UIViewController {
    bootstrapKoin()
    return ComposeUIViewController {
        GameDealsTheme { NavGraph() }
    }
}
```

**Seen in.**
- `iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt`
- `iosApp/iosApp/ContentView.swift`

**References.** `di.md` for the Koin bootstrap shape; `compose-correctness.md` for rules that apply to the shared Compose tree.

**Related lessons.** L-2026-05-04-05, L-2026-05-11-02

**Tags:** `kmp`, `ios`, `compose`, `bootstrap`

---

### Swift-UIKit Interop via UIViewControllerRepresentable

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** iOS Swift host.

**The pattern.** The Swift side uses `UIViewControllerRepresentable` to bridge the Kotlin `MainViewController()` `UIViewController` into SwiftUI. The Gradle configuration for `iosApp/` declares the `ComposeApp` framework and re-exports `:domain` so Swift code can see domain models if needed (currently minimal). No `@Composable` calls cross the Swift boundary; everything Compose-flavored is built in Kotlin and exposed as a single `UIViewController`.

**Why this works for us.** Cleanly separates the two languages — Swift handles platform integration (launch, deep-linking, share sheets, App Delegate hooks) and Kotlin handles app logic plus UI. The framework export surface stays small, which keeps the generated Objective-C header readable and the Kotlin/Native link time bounded.

**Known trade-offs / when it strains.** Any new shared API surface that Swift needs (e.g., pushing a screen from Swift, exposing a coordinator) has to be wired through the framework export list in `iosApp/build.gradle.kts`. Compose-side state is not directly observable from Swift; if Swift needs to react to shared state, that state has to be surfaced through a Kotlin-side interface and exported.

**How to apply it.**
```swift
// iosApp/iosApp/ContentView.swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

**Seen in.**
- `iosApp/iosApp/ContentView.swift`
- `iosApp/build.gradle.kts` (ComposeApp framework + `:domain` export)

**Tags:** `kmp`, `ios`, `swift-interop`

---

### Shared Compose Resources via `composeResources/` in commonMain

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** Every Compose-bearing module.

**The pattern.** Each module that ships Compose resources places them under `src/commonMain/composeResources/` — `drawable/`, `font/`, `string/` subdirs follow the standard Compose Multiplatform layout. The CMP Gradle plugin generates a `Res` class scoped to the module's package; resources are referenced via `Res.drawable.foo` and `Res.string.bar`. The generator picks up `androidNamespace` from the convention plugin's path-derived namespace (see `build.md`), so no module has to declare its resource namespace by hand.

**Why this works for us.** One resource set works on both Android and iOS; there is no Android-specific `res/` directory in shared modules. The generated `Res` class is type-safe — a missing resource is a compile error, not a runtime crash.

**Known trade-offs / when it strains.** Not all Android `res/` features map cleanly. Night-mode resource variants need explicit handling, and platform-specific overrides (e.g., an iOS-only drawable variant) require manual conditioning rather than the implicit resource-qualifier system Android engineers are used to.

**How to apply it.** Drop assets into `src/commonMain/composeResources/{drawable,font,string,…}` and reference them via the generated `Res` class. No Gradle changes are needed beyond having the convention plugin applied — the resource generator is already wired.

**Seen in.**
- `feature/home/src/commonMain/composeResources/`
- `feature/search/src/commonMain/composeResources/`
- `common/ui/src/commonMain/composeResources/`

**References.** `build.md` for the convention plugin and the path-derived namespace.

**Tags:** `kmp`, `compose-resources`

---

## What we don't do

- **No `androidTarget()` + `com.android.library` combo.** AGP 9.1.1 requires `com.android.kotlin.multiplatform.library` instead; the old AGP-8 pattern is incompatible with Kotlin 2.3. **Why we avoid it:** combining the two produces silent classpath drift and resource-merger failures on KMP modules — the new plugin is the supported integration point for Kotlin 2.3.21 + AGP 9.1.

- **No `iosX64` target.** Intel Mac simulator support was dropped by Compose Multiplatform 1.11 and Kotlin 2.3.21. Apple-Silicon Mac simulators are covered by `iosSimulatorArm64`. **Why we avoid it:** adding `iosX64` would force us off the supported toolchain matrix and we have no Intel Mac sim users to serve.

- **No platform-specific code in `commonMain`.** Anything calling `android.*`, `java.*` (beyond the Kotlin-stdlib-supported subset), or platform APIs must go in `androidMain`/`iosMain` via `expect`/`actual`. **Why we avoid it:** code in `commonMain` that touches Android-only types compiles for Android but fails to link for iOS, often with cryptic Kotlin/Native errors deep in the build.

- **No Sentry-KMP in `iosMain` dependencies yet.** The iOS variant of `sentry-kotlin-multiplatform` needs Sentry-Cocoa wired via SPM; we have deliberately deferred that work. iOS logging currently uses `IosConsoleLoggingListener` (NSLog) only — see `observability.md`. **Why we avoid it:** adding it half-wired would create a false impression of iOS crash reporting; until SPM integration is in place, NSLog is the honest answer.

- **No Room database constructors in `commonMain`.** Room KMP generates the platform implementations automatically at compile time; the database class itself is `expect`/`actual` via Room's annotation processing, not hand-written. **Why we avoid it:** hand-rolling the platform constructor duplicates what KSP already does and risks drifting from Room's expected wiring.
