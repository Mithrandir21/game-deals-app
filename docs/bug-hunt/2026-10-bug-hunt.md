# Bug hunt, October 2026

Three independent read-only reviews of `dev` at `49bc9fdb` (2026-10-04), covering Android, iOS and the build/release pipeline:
- **N (naive):** walked every screen like a first-time user.
- **B (balanced):** went module by module and traced each call path.
- **A (adversarial):** needed a concrete failure scenario for every finding.

The "Seen by" column shows which reviewers found each issue independently. Open GitHub issues and the June hunt (`android-bug-hunt-report.md`) were excluded. They are listed at the end where still present.

The iOS release pipeline was checked by all three reviewers and has no blockers. Signing, version derivation, Info.plist, the privacy-manifest reason codes, icons and the Release Kotlin framework are all fine. The remaining iOS release risks are M11 (dSYM), M14 ("Coming soon" row), L4 (privacy label) and #112 (no test gate).

Issues are grouped by severity so each group can be worked as one batch. Tick an item when its fix lands.

---

## High

- [ ] **H1. A failed waitlist, collection, ignore or note action crashes the app.** Both platforms. Seen by B.
  - **Problem:** A signed-in user taps one of these while offline, on a flaky connection, or during an ITAD 5xx or 429. The repositories are remote-first and rethrow (`getOrThrow()`), and `RepoUpdateResult` has no failure value. The feature view models launch the call bare on `viewModelScope`, and there is no `CoroutineExceptionHandler`, so the exception reaches the platform's uncaught handler. The Account-hub view models already wrap the same calls in `runCatchingLogged`.
  - **Where:**
    - Repositories: `WaitlistRepository.kt:160-175`, `CollectionRepository.kt:95-107`, `IgnoredRepository.kt:70-82`, `NotesRepository.kt:62-76`
    - Callers: `HomeViewModel.kt:241,250`, `DealsViewModel.kt:403,412`, `GamePageViewModel.kt:338-368`, `BundleDetailViewModel.kt:106`, `DiscoverResultsViewModel.kt:171`, `StoreViewModel.kt:166`, `GamePeekDelegate.kt:73`
  - **Fix:** Add `RepoUpdateResult.FAILED`. Have the repositories catch non-cancellation throwables, log them and return `FAILED`. Show a "Couldn't update, try again" message on `FAILED`.

- [ ] **H2. After sign-out and sign-in as another account, the app keeps using the first account's token.** Both platforms. Seen by A and B.
  - **Problem:** Ktor's bearer plugin caches the token it loads (`cacheTokens = true` by default), and nothing ever calls `clearToken()`. After logout and login as account B in the same process:
    - `/user/info` is fetched with A's token, so A's username is stored.
    - The library sync pulls A's waitlist and collection.
    - Every write lands on A's account.
    - "Reconnect" (scope upgrade) keeps sending the old, narrower-scope token.
  - **Where:** `ItadAuthHttpClient.kt:76-81`, `AccountRepository.kt:64-67`, `ItadLoginSourceImpl.kt:56-71`
  - **Fix:** Invalidate the bearer cache whenever the stored tokens change (login, logout, refresh), or read the store on every request.

- [ ] **H3. Any failed token refresh signs the user out and wipes the local library.** Both platforms. Seen by A and B.
  - **Problem:** `ItadTokenProvider.refresh()` clears the session on *any* throwable: a timeout, a dropped connection, or an ITAD 5xx or 429 on `/oauth/token`. The library lifecycle then wipes the local waitlist, collection and ignored lists. A coroutine cancelled mid-refresh can also lose a rotated refresh token.
  - **Where:** `ItadTokenProvider.kt:31-57`, `ItadAuthHttpClient.kt:76-81`
  - **Fix:** Clear the session only on a definitive OAuth rejection (400 or 401 from the token endpoint). For transport errors, 5xx and 429, return `null` without clearing, so the session survives for the next attempt. Set `nonCancellableRefresh = true`.

- [ ] **H4. Backing out of the sign-in browser leaves the app stuck "signing in".** Android. Seen by N, A and B.
  - **Problem:** The launcher awaits a `CompletableDeferred` that only the redirect activity completes. If the user abandons the browser, it never completes:
    - On the Account tab the spinner never stops.
    - The sign-in sheet keeps both buttons disabled.
    - On onboarding's last slide, "Sign in" and "Maybe later" stay disabled, so a new user cannot finish onboarding.
  - **Where:** `AndroidAuthBrowserLauncher.kt:20-31`; UI at `AccountScreen.kt:325-331`, `SignInPromptHost.kt:97,112`, `OnboardingScreen.kt:611-635`
  - **Fix:** When the app returns to the foreground with no redirect (after a short grace period), complete the pending login as `Cancelled`.

- [ ] **H5. Double-tapping a back arrow pops Home too and leaves a blank screen.** Both platforms, worse on iOS. Seen by N, A and B.
  - **Problem:** Every `onBack` is a bare `navController.popBackStack()`. During the exit animation, the outgoing screen's arrow is still tappable, so a second tap pops the start destination. The NavHost is then empty: no content and no bars. On iOS there is no system back, so the user must force-quit. Fast double taps on rows also push duplicate pages.
  - **Where:** every navigation `onBack`, e.g. `GamePageNavigation.kt:40`, `AccountNavigation.kt:51-86`, `BundlesNavigation.kt:16,29`, `DiscoverNavigation.kt:21,38`, `StoreNavigation.kt:16`, `DebugNavigation.kt:15`, `MainViewController.kt:480`
  - **Fix:** Only pop when the current entry is RESUMED and something is behind it, or use `dropUnlessResumed`.

- [ ] **H6. In the first session after install, the bottom tabs pile up instead of switching.** Both platforms. Seen by N and B.
  - **Problem:** On first run, the NavHost start destination is Onboarding, and finishing onboarding removes it from the stack. `navigateTopLevel` pops up to `graph.findStartDestination()`, which is still Onboarding. That target is no longer on the stack, so nothing is popped or saved. Each tab tap pushes a new copy, Back walks through every tab visited, and returning to a tab reloads it. This lasts until the app process restarts.
  - **Where:** `Navigation.kt:30-57`, `MainViewController.kt:358-364,445-451`
  - **Fix:** Pop up to Home rather than the graph's start destination, or make Home the start destination once onboarding is done.

- [ ] **H7. Release builds log full API responses to the console and to Sentry breadcrumbs.** Both platforms. Seen by A and B.
  - **Problem:** `ApiResponse.log()` logs `"Success: ${this.data}"`, the whole response including private note text, the username, the waitlist and the collection. Every listener is enabled in release:
    - Android writes to logcat.
    - iOS writes to NSLog.
    - Sentry turns every VERBOSE to WARN line into a breadcrumb, which is uploaded with the next crash.

    On Android, the Room query callback also logs every SQL statement, which floods the 100-slot breadcrumb trail.
  - **Where:** `remote/.../logic/Extensions.kt:38-41`, `SentryLoggingListener.kt:17-21`, `LoggingModule.kt:15`, `LoggingIosModule.kt:15`, `DomainAndroidModule.kt:25-28`
  - **Fix:**
    - Log only a summary on success, never the body.
    - Keep VERBOSE and DEBUG out of Sentry.
    - Install the Room query callback only in debug builds.

---

## Medium

| # | Issue | Platform | Seen by | Where | Fix |
|---|---|---|---|---|---|
| M1 | After opening the app from a notification, every rotation, theme change or restore from Recents opens Notifications again | Android | N A B | `MainActivity.kt:34,63-72` | Handle the intent only when `savedInstanceState == null`, remove the extra after use, and use `launchSingleTop` |
| M2 | The previous account's game notes show under the next account; the cache is cleared only while a Game page observes it | Both | A | `NotesRepository.kt:47-60,99-103` | Clear notes from the app-wide auth lifecycle on logout |
| M3 | A failed notes load is cached as "no notes", so saving overwrites the user's real note on the server | Both | A | `NotesRepository.kt:83-85,99-103` | Leave the cache empty on failure so the next view retries, and show an error |
| M4 | Signing out while a token refresh is in flight signs the user back in under a blank name | Both | A | `ItadTokenProvider.kt:31-45`, `AuthTokenStore.kt:122-151` | Save refreshed tokens only if the stored refresh token is unchanged (compare-and-set) |
| M5 | When the in-app theme differs from the system theme, the status bar is unreadable and system sheets ignore the choice | iOS | N A B | `common/ui/src/iosMain/.../theme/Theme.kt:9-21` | Set `overrideUserInterfaceStyle` on the key window from the theme mode |
| M6 | Cache maintenance never runs on iOS, so caches grow without limit and `CACHE_SCHEMA_VERSION` resets never reach iPhones | iOS | N B | `CacheMaintenance.kt:55-58`, `MainViewController.kt:163-241` | Run `runStartupMaintenance()` from the iOS bootstrap |
| M7 | Game page waitlist, collection, note and ignore buttons look enabled but do nothing for games with no ITAD match, and a typed note is discarded | Both | N | `GamePageViewModel.kt:338-384`, `GamePageScreen.kt:297-361` | Disable the actions when there is no ITAD id, or explain why |
| M8 | Store, deal and giveaway links open Safari, although the in-app `SFSafariViewController` already exists | iOS | N B | `MainViewController.kt:406-476` | Use `LocalPlatformActions.current.openInApp(url)` as Android does, and guard non-http(s) URLs |
| M9 | One giveaway with a missing or odd `published_date` breaks the whole Giveaways feed | Both | A B | `GiveawayMappers.kt:27` | Parse per row with a fallback, or drop and log bad rows |
| M10 | If Android kills the app while the sign-in browser is open, the sign-in is silently lost | Android | A | `AuthRedirectBus.kt:13,24-28`, `OAuthRedirectActivity.kt:13-17` | Persist the PKCE verifier and state, or at least tell the user to try again |
| M11 | The dSYM upload phase has no input dependency on the dSYM, and its failure is masked, so iOS crash reports may be unsymbolicated | iOS build | A B | `project.pbxproj:172-187` | Add the dSYM as an input file, fail on CI, and verify in Sentry after the next tag |
| M12 | When a refresh fails, the Giveaways tab goes blank even though cached giveaways exist | Both | N | `GiveawaysViewModel.kt:63-67`, `GiveawaysScreen.kt:259` | Show the cached list with an error banner, and use the error state only when there is nothing to show |
| M13 | The max-price filter is 5/10/20/50 in every currency, so it is useless in yen, won and rupees, and it persists across region changes | Both | N | `DealsScreen.kt:197,1121-1126` | Scale the tiers per currency, and clear `maxPrice` on region change |
| M14 | A visible "Coming soon" Linked accounts row is a likely App Review rejection (guideline 2.1) | iOS review | N | `AccountScreen.kt:397-404` | Hide the row until the feature exists |
| M15 | The Giveaways filter sheet doesn't scroll, so the sort options are cut off on small screens or with large text | Both | N | `GiveawaysScreen.kt:579` | Add `verticalScroll` as the Deals sheet does |
| M16 | The last Giveaways row sits under the home indicator or gesture bar | Both | N | `GiveawaysScreen.kt:233-240` | Add the navigation-bar inset to the grid's bottom padding |
| M17 | In iPhone landscape, list content runs under the notch | iOS | N | `ContentView.swift`, `AppShellScaffold.kt:122` | Apply horizontal `safeDrawing` insets at the NavHost, or lock iPhone to portrait |

---

## Low

### iOS

| # | Issue | Seen by | Where | Fix |
|---|---|---|---|---|
| L1 | Each launch resubmits the background poll with a fresh 6-hour delay, so alerts rarely fire for frequent users | A B | `IosNotificationScheduler.kt:32-35` | Skip submitting when a request is already pending |
| L2 | iOS cutting the background task short is logged as an error (a Sentry issue) | A B | `NotificationBackgroundPoll.kt:44-52` | Rethrow `CancellationException` |
| L3 | The Room database is in `Documents/`, so it is iCloud-backed | N A B | `DomainIosModule.kt:22-28` | Move it to Application Support and exclude it from backup |
| L4 | The privacy manifest says crash data is "not linked", but Sentry gets a stable install ID | N B | `PrivacyInfo.xcprivacy:14,26`, `MainViewController.kt:248-253` | Declare an identifier type, or drop `setUser` |
| L5 | The sign-in token survives an uninstall, so a reinstall comes back signed in | N A B | `KeychainBackend.kt` | Clear Keychain items on the first launch after install |
| L6 | Offline errors become Sentry issues on iOS | N B | `NetworkFailureClassifier.ios.kt:9` | Classify `DarwinHttpRequestException` and NSURLErrorDomain codes as offline |
| L7 | A "Notifications blocked" warning flashes each time Account opens | N | `NotificationPermission.ios.kt:39` | Use a tri-state (unknown, granted, denied) |
| L8 | Dates show the wrong year for users on Buddhist or Japanese calendars | B | `PlatformDateFormatter.ios.kt` | Force the Gregorian calendar |

### Android

| # | Issue | Seen by | Where | Fix |
|---|---|---|---|---|
| L9 | The notification small icon is the full-colour adaptive launcher icon: it shows as a white blob, with a possible crash on Android 8.0 | B | `AndroidNotificationPresenter.kt:97` | Add a monochrome `ic_stat_notification` |
| L10 | Below Android 13, the alerts toggle reads "on" when notifications are blocked in system settings | B | `NotificationPermission.android.kt:61-64` | Use `areNotificationsEnabled()` on all API levels |
| L11 | Rotating during the permission dialog loses the result | A B | `NotificationPermission.android.kt:24-28` | Keep the pending flag in `rememberSaveable` |
| L12 | After process death, giveaway filters show as active but aren't applied | N | `GiveawaysScreen.kt:135-150` | Push the restored parameters into the view model on first composition |
| L13 | A release build silently ships with blank API keys if a CI secret is missing (iOS fails loudly) | A | `app/build.gradle.kts:75-83` | Fail the release build on blank credentials |
| L14 | Cleartext HTTP is allowed, and the backup rules are untouched templates (also in `security-audit.md`) | N | `AndroidManifest.xml:11,19` | Drop cleartext, and exclude the secure-storage file from backup |
| L15 | `coil3.test` ships on the release classpath | B | `app/build.gradle.kts` | Move it to a test configuration |

### Both platforms

| # | Issue | Seen by | Where | Fix |
|---|---|---|---|---|
| L16 | Rapid Deals filter taps lose updates | A | `DealsViewModel.kt:287-289`, `SettingsRepository.kt:149-151` | Update the in-memory filter atomically before persisting |
| L17 | Discover shows "No results" when the first five IGDB pages hold no trackable games | A | `TagDiscoveryRepository.kt:96-119` | Treat an empty page that is not the end as "keep scanning" |
| L18 | The sign-in sheet and onboarding sign-in swallow errors silently | A B | `SignInPromptViewModel.kt:37-42`, `OnboardingViewModel` | Emit an error event and show a message |
| L19 | A failed title search can't be retried, and resubmitting the same query does nothing | N B | `DealsScreen.kt:844-847`, `SearchController.kt` | Add a Retry action to the error state |
| L20 | Deals "load more" never retries after a failure until the user scrolls away | N | `DealsScreen.kt:585-596`, `DiscoverResultsScreen.kt:140-148` | Add a retry footer, or re-key the effect |
| L21 | A failed regional-prices load can't be retried | N | `GamePageViewModel.kt:443-446` | Allow Error → Loading on expand |
| L22 | The saved-search "×" is an 18dp target nested inside the chip | N B | `DealsScreen.kt:783-790` | Use a 48dp `IconButton` |
| L23 | The Background alerts switch has no spoken label | N | `AccountScreen.kt:541-555` | Make the row `toggleable(role = Switch)` |
| L24 | The bundle countdown is read aloud as "11 d 16 h 32 m" | N | `BundleCountdown.kt:52` | Reuse `spokenCountdown()` |
| L25 | The "Players" chips ripple but do nothing | N | `GamePageCommunityTab.kt:96,100` | Use a non-interactive label |
| L26 | A note being typed is lost on rotation, or when saving while signed out | N | `GamePageScreen.kt:262`, `GamePageDialogs.kt:58` | Use `rememberSaveable`, and gate on sign-in before opening |
| L27 | Recently viewed "Clear" wipes everything without a confirmation | N | `RecentlyViewedCarousel.kt:87-89` | Confirm, or offer undo |
| L28 | Deals sort and store choices reset on restart, and "Reset all" skips them | N | `DealsViewModel.kt:125-126`, `DealsScreen.kt:1038-1049` | Persist them with `DealsFilter` |
| L29 | The Game page top bar holds up to six icons, so the title truncates on small phones | N | `GamePageScreen.kt:286-369` | Move note and collection into the overflow menu |
| L30 | Notifications say "1 new deals", and the iOS notification text is hardcoded English | N | `strings.xml:5`, `IosNotificationPresenter.kt` | Use plurals and resources |
| L31 | The force-update gate can't fire in shipped builds, because the flags are NoOp | N | `LoggingModule.kt`, `LoggingIosModule.kt` | Wire a flag provider, or document that the gate is inert |
| L32 | Each Home load does full remote waitlist and collection syncs, which can undo a toggle made at the same time | B | `RecommendationsRepository` | Read the Room id sets instead |

### Build and docs

| # | Issue | Seen by | Where | Fix |
|---|---|---|---|---|
| L33 | `docs/ci-cd.md` suggests a `v0.0.1-rc1` smoke-test tag that `derive-version` rejects | A | `docs/ci-cd.md:288` | Correct the doc |
| L34 | `.claude/claude.md` imports a missing `AGENTS.md` | B | `.claude/claude.md` | Add the file, or drop the import |

---

## Already tracked, still present
- **#325:** API secrets are still in both binaries.
- **#324:** the Store and WebView routes are unreachable.
- **#112:** PR CI builds no iOS, and `release-ios` runs no tests, so a tag can stop the Play upload but still ship to TestFlight.
- **#117:** no iOS swipe-back, which makes H5 worse.
- **#136:** iOS share is presented from the root view controller.
- **#111 (iOS Sentry):** looks done in code. Close it once events are confirmed arriving.
- **June hunt:**
  - Fixed: BUG-001, 002, 003 and 006.
  - Still present: BUG-005, 007, 008, 009, 010, 011 and 012.
  - There are no regressions.
