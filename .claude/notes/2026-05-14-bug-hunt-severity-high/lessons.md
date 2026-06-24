# Campaign lessons — 2026-05-14-bug-hunt-severity-high

## Campaign lessons

- (wave 1) Issue #144 — Kotlin/Native exposes Objective-C **category** properties as top-level extensions, not members. `popoverPresentationController` on `UIViewController` is a category extension, so accessing it from K/N requires an explicit `import platform.UIKit.popoverPresentationController`. Regular members like `UIPopoverPresentationController.sourceView` need no import. This is non-obvious and the first compile failed on it.

- (wave 1) Issue #144 — `CGRectMake` and `bounds.useContents { ... }` (any `cValue<T>` reading) require file-level `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` in current Kotlin/Native.

- (wave 1) Issue #143 — When a `commonMain` `expect`/`actual` pair has platform asymmetry (one side caches state at class-load, the other reads live), the cheaper-construction side ought to mirror the per-call-read shape so behavior tracks the OS user-locale/timezone changes. Recommend: rule of thumb is "build per-call unless the constructor cost is provably expensive AND state is immutable for the process lifetime."

## Promotion candidates (project-wide)

- [x] (promoted 2026-05-15) Kotlin/Native iOS bindings: Objective-C category properties on UIKit types must be imported as top-level extension properties (e.g. `import platform.UIKit.popoverPresentationController`). Trying to access them as members compiles fine for regular ObjC members but silently fails / errors for category extensions. — drafted from issue #144

- [x] (promoted 2026-05-15) `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` is required for `CGRectMake` and any `bounds.useContents { ... }` / `cValue<T>` reading in current K/N. — drafted from issue #144

- [x] (promoted 2026-05-15) For platform `actual` implementations of a `commonMain` `expect`: when one platform reads system state live per-call (e.g. iOS reads `NSLocale.currentLocale` each call), the other platform's actual should match — cached-at-class-load state stays stale after user settings changes until process death. — drafted from issue #143
