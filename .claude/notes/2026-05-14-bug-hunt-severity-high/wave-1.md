# Wave 1 — 2026-05-14

Campaign: `2026-05-14-bug-hunt-severity-high` (labels: `bug-hunt`, `severity:high`)
Base branch: `dev` @ `9783ad4`

## Summary
- Attempted: 2
- Succeeded: 2
- Failed: 0

## Per-issue

### #143 — Android `formatLocaleAwareDate` caches locale and timezone at class-load
- **Status:** open (PR #154)
- **Branch:** `wave/2026-05-14-bug-hunt-severity-high/issue-143-android-date-formatter-locale`
- **PR:** https://github.com/Mithrandir21/game-deals-android-app/pull/154
- **Title:** `fix(common): read Locale and ZoneId per call in Android date formatter`
- **Files changed:**
  - `common/src/androidMain/kotlin/pm/bam/gamedeals/common/datetime/formatting/PlatformDateFormatter.android.kt`
  - `common/src/androidUnitTest/kotlin/pm/bam/gamedeals/common/datetime/formatting/DateTimeFormatterImplTest.kt`
- **Tests:** Added `formatLocaleAwareDate reads Locale per call` that flips `Locale.setDefault` between calls and asserts outputs differ. Fit the existing JUnit/Locale-default test pattern in the file.
- **Validation:** `:common:compileDebugKotlinAndroid` + `:common:testDebugUnitTest` both green (4/4 passing).
- **Reviewer notes:** Existing `@Before` comment in the test file explicitly anticipated this fix ("the Android actual reads `Locale.getDefault()` and `ZoneId.systemDefault()`") — that comment is now actually true. Per-call `DateTimeFormatter` allocation cost is negligible for typical UI usage and matches iOS approach.

### #144 — iOS share uses deprecated `keyWindow` and crashes on iPad
- **Status:** open (PR #155)
- **Branch:** `wave/2026-05-14-bug-hunt-severity-high/issue-144-ios-share-keywindow-ipad`
- **PR:** https://github.com/Mithrandir21/game-deals-android-app/pull/155
- **Title:** `fix(ios): resolve key window via connectedScenes and anchor share popover for iPad`
- **Files changed:**
  - `common/ui/src/iosMain/kotlin/pm/bam/gamedeals/common/ui/platform/PlatformActions.ios.kt`
- **Tests:** None. `:common:ui` has no `iosTest` source set; the fix's behaviour can only be exercised under a real simulator/device host. Mocking `UIApplication.connectedScenes` would just verify a tautology.
- **Validation:** `:common:ui:compileKotlinIosSimulatorArm64`, `:common:ui:compileKotlinIosArm64`, and `:common:ui:compileDebugKotlinAndroid` all green.
- **Reviewer notes:**
  - K/N Objective-C category extensions: `popoverPresentationController` on `UIViewController` is a category, so requires `import platform.UIKit.popoverPresentationController` (non-obvious; tripped the first compile).
  - `CGRectMake` and `bounds.useContents { ... }` require `@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)` at file level.
  - `expect`/`actual` shape preserved; `PlatformActions.kt` (common) and `PlatformActions.android.kt` not touched.
  - When no window resolvable: silent early return (no crash). No logger available in `:common:ui` iosMain.

## Conflicts deferred
None. Only 2 candidate issues matched the label filter and they were fully disjoint (different modules + different platform source sets).

## Sanity-check results
- #143: branch present on origin (✓), 1 commit ahead of dev (✓), PR OPEN with base=dev (✓).
- #144: branch present on origin (✓), 1 commit ahead of dev (✓), PR OPEN with base=dev (✓).
