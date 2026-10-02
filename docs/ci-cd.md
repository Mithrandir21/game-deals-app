# CI/CD

How this project is verified and released, and **why** it's set up this way.

- **Verification** → GitHub Actions (`.github/workflows/android.yml`)
- **Releases** → Bitrise (`bitrise.yml`), Android + iOS from one tag

```
 PR / push to dev,main ─► GitHub Actions ─► build + unit tests + Compose-stability + (R8 verify)
 push tag v*.*.*       ─► Bitrise pipeline `release` ─┬─► signed AAB ─► Play internal ─► (manual) production
                                                      └─► signed IPA ─► TestFlight ─► (manual) App Store review
```

The two systems never overlap: GHA's `push` trigger is branch-filtered, so version tags only ever
run on Bitrise.

---

## 1. Decisions & rationale

| Decision | Choice | Why |
|---|---|---|
| Release CI | **Bitrise for releases only**; GHA stays verification | GHA already verifies well; no reason to migrate it. Bitrise's real payoff is iOS (managed signing, macOS minutes), so standing it up now means the release infra already lives on the iOS-friendly platform when iOS ships. |
| Platforms | **Android + iOS, one tag** | A `v*.*.*` tag runs the `release` pipeline: `release-android` and `release-ios` in parallel. One version scheme, one set of release notes, no platform drifting behind. A failure in one platform doesn't cancel the other's upload — re-run the failed workflow from the pipeline. |
| iOS signing | **App Store Connect team API key (Admin) + uploaded Apple Distribution cert** | Team key (not individual) because individual keys can't use Apple's provisioning API. Admin because `xcode-archive`'s API-key signing creates/updates the App Store provisioning profile. The certificate itself must be uploaded — Bitrise can't generate it. |
| Release trigger | **Git tag `v*.*.*`** | Explicit, auditable, decoupled from merges. A release is a deliberate act (`git tag … && git push …`), not a side effect of merging. |
| Distribution | **Play internal → production** (staged) | Internal track is the QA gate; production is a manual promotion at a staged rollout %. |
| `versionCode` | **Derived from the tag** (`major*10000 + minor*100 + patch`) | Deterministic & strictly increasing. Lets production reuse the *exact* artifact tested on internal (see §4). A build-number scheme would be non-deterministic and break promotion. Constraint: minor/patch each `< 100`. |
| Release-build R8 check | **Runs in GHA** on main/nightly/dispatch (not PRs) | Moves R8/keep-rule failures left, off the release critical path. Excluded from PRs because R8 is slow (see §2). |
| Production promotion | **Reuses the internal artifact, never rebuilds** | Play rejects a new binary on a reused `versionCode`; a rebuilt binary would be code nobody tested (see §4). |

---

## 2. GitHub Actions — verification (`.github/workflows/android.yml`)

### Triggers
```yaml
on:
  push:            { branches: [ "main", "dev" ] }
  pull_request:    { branches: [ "main", "dev" ] }
  workflow_dispatch:                       # run on demand
  schedule:        [ { cron: '0 3 * * *' } ]   # nightly, 03:00 UTC
concurrency:                               # cancel superseded runs for the same ref
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
```

### Jobs

**`build`** (every push/PR) — `./gradlew build test` (compiles all modules, runs host/unit tests) +
`./gradlew debugStabilityCheck` (Compose-stability gate against committed baselines). Has a
`timeout-minutes` guard.

**`release-verify`** (`if: github.event_name != 'pull_request'`) — runs `./gradlew :app:bundleRelease`,
the **full release variant with R8** (minification + resource shrinking + keep rules + baseline-profile
merge), and uploads the R8 `mapping.txt` as an artifact. No keystore in CI, so it falls back to debug
signing — R8 still runs fully.

**`instrumented-test`** (`if: github.event_name == 'pull_request'`) — API 34 emulator,
`connectedAndroidDeviceTest` excluding `@SkipOnCi` tests, publishes JUnit results.

### Why the release-verify job (and why nightly)

Normal CI runs on the **debug** variant. R8 only runs on **release**. So an entire class of failures —
missing keep rules after a dependency bump, stripped reflective calls, an over-aggressive
`shrinkResources` — is invisible to PR CI and would first surface when you push a release tag, on
Bitrise, at the worst possible moment.

`release-verify` exercises that path earlier:
- **push to `main`** — catches breakage at merge time.
- **nightly schedule** — catches breakage that drifts in *without a commit* (a floating/transitive
  dependency, a runner-image change). Green today ≠ green tomorrow if an input moved.
- **manual dispatch** — run before cutting a release.

It's **excluded from PRs on purpose**: R8 is slow (~2 min locally, longer cold on CI) and would tax
every PR push. PR feedback stays on the fast debug path; the expensive release check runs on
lower-frequency, higher-stakes events.

> Note: GitHub only runs `schedule` triggers on the **default branch** (`dev`), and auto-disables
> scheduled workflows after ~60 days of repo inactivity.

---

## 3. Bitrise — releases (`bitrise.yml`)

Two things frame the design:

1. Bitrise reads `bitrise.yml` from the repo, but **secrets, the keystore, and the Play
   service-account JSON live in the Bitrise UI** (see §5), referenced here only by env-var name.
   Nothing sensitive is committed.
2. It **reuses the existing Gradle signing logic** rather than replacing it. `app/build.gradle.kts`
   already signs from `RELEASE_*` env vars + a keystore at the repo root; Bitrise's job is only to
   *supply* those — so there's no Bitrise signing step fighting with Gradle.

### Trigger
```yaml
trigger_map:
  - tag: "v*.*.*"
    pipeline: release        # runs release-android + release-ios in parallel
```
The version derivation lives in the `derive-version` **step bundle**, used by both workflows, so
`VERSION_CODE` (Play `versionCode`) and `CURRENT_PROJECT_VERSION` (iOS `CFBundleVersion`) are always the
same number for the same tag.

### Workflow `release-android` (automatic, on tag)

| Step | Purpose |
|---|---|
| `activate-ssh-key` (run_if) | Only if `SSH_RSA_PRIVATE_KEY` is set (private repo); no-op otherwise. |
| `git-clone` | Checks out the tagged commit. |
| Script — **derive version** | From `$BITRISE_GIT_TAG`: `VERSION_NAME` = tag minus `v`; `VERSION_CODE` = `major*10000+minor*100+patch`. Published via `envman` for later steps. |
| Script — **run unit tests** | `./gradlew testDebugUnitTest testAndroidHostTest` (app + all KMP modules; `test` alone misses the KMP modules). A failure aborts the release before keystore/build/deploy. Exports JUnit XMLs to `$BITRISE_TEST_RESULT_DIR` (even on failure) so the failing test shows in the Test Reports tab. |
| Script — **place keystore** | Downloads the keystore from `$BITRISEIO_ANDROID_KEYSTORE_URL` (Code Signing tab) to `upload_keystore.jks` at the repo root, where `app/build.gradle.kts` expects it. |
| `android-build` | `./gradlew :app:bundleRelease`. Gradle reads `RELEASE_*`, `IGDB_*`, `ITAD_*`, `SENTRY_*`, `VERSION_*` from env. Outputs `$BITRISE_AAB_PATH` + `$BITRISE_MAPPING_PATH`. When `SENTRY_AUTH_TOKEN` is set, the Sentry Gradle plugin also uploads the R8 mapping to Sentry (readable crash stacks there too). |
| `google-play-deploy` | Uploads the AAB to the **internal** track (`status: completed`), with the R8 mapping (readable Play crash stacks) and notes from `whatsnew/`. |
| `deploy-to-bitrise-io` | Archives the AAB + mapping as Bitrise build artifacts (audit trail + source for promotion). |

### Workflow `release-ios` (automatic, on tag, macOS stack)

| Step | Purpose |
|---|---|
| `git-clone` + `derive-version` | Same as Android. |
| `set-java-version` (21) | The Xcode "Compile Kotlin Framework" phase runs Gradle (`embedAndSignAppleFrameworkForXcode`); it honours `JAVA_HOME` and only falls back to Android Studio's JDK locally. |
| Script — **write `Secrets.xcconfig`** | Generates `iosApp/Secrets.xcconfig` (gitignored; both Xcode configurations are based on it) from the same `IGDB_*` / `ITAD_*` / `SENTRY_DSN` secrets. `SENTRY_DSN_IOS` overrides the DSN if iOS gets its own Sentry project. Rewrites the DSN's `//` as `$(SLASH)$(SLASH)` since xcconfig treats `//` as a comment. |
| Script — **install sentry-cli** (run_if `SENTRY_AUTH_TOKEN`) | Puts `sentry-cli` on `PATH` so the existing "Upload dSYMs to Sentry" build phase actually uploads. `SENTRY_PROJECT_IOS` overrides `SENTRY_PROJECT` for that upload. |
| `xcode-archive@6` | Archives scheme `iosApp` (Release) and exports an **app-store** IPA. `automatic_code_signing: api-key` uses the project's Apple service connection + the uploaded distribution cert. `min_profile_validity: 30` forces Bitrise-managed signing (an App Store profile, so no device or development cert is needed). Version injected via `xcconfig_content` (`MARKETING_VERSION = $VERSION_NAME`, `CURRENT_PROJECT_VERSION = $VERSION_CODE`). |
| `deploy-to-itunesconnect-application-loader@2` | Uploads the IPA to App Store Connect → appears in **TestFlight** after processing. `app_id` (6818586859) switches v2 to `altool --upload-package`; v2 also fails the step when Xcode 26's `altool` reports an error but exits 0 (v1 passed silently). `ITSAppUsesNonExemptEncryption = false` in `Info.plist` skips the export-compliance prompt. |
| `deploy-to-bitrise-io` | Archives the IPA + dSYMs as build artifacts. |

There is no iOS `promote-production`: an uploaded build is promoted to the App Store by attaching it to
a version in App Store Connect and submitting for review — same binary, no rebuild, so the §4 contract
holds by construction. App Store Connect also rejects a re-upload of the same
`CFBundleShortVersionString` + `CFBundleVersion`, so re-running `release-ios` for an already-uploaded tag
fails at the upload step — expected.

The build steps are just plumbing that feeds env vars and a file into the same `:app:bundleRelease`
build verified locally — no Bitrise-specific signing/versioning magic.

### Workflow `promote-production` (manual)

Triggered by hand after internal QA. It uploads the **already-built** AAB to the `production` track at
a 10% staged rollout (`user_fraction: "0.1"`); bump to 100% in Play Console after watching vitals.

It deliberately **does not rebuild** — see §4. Set **`PROMOTE_BUILD_SLUG`** to the `release-android`
build you are promoting (the last path segment of its Bitrise build URL) and the fetch step does the
rest:

| Step | Purpose |
|---|---|
| `git-clone` | Lands on the default branch — a manually started build has no tag to check out. |
| Script — **fetch the AAB** | Reads the build over the Bitrise API (`$BITRISE_API_TOKEN`), refuses it unless `status_text` is `success`, downloads the archived `*.aab` into `promote-artifacts/`, checks out the tag that build was made from, and publishes `PROMOTE_AAB_PATH` via `envman`. |
| `google-play-deploy` | Uploads that exact AAB to `production` at 10%. |

Two guards worth knowing. The **success check** exists because a failed run can still have archived
artifacts, and those are exactly the ones that never passed internal QA. The **tag checkout** exists
because `whatsnews_dir` is read from the working copy: without it, Play would get whatever
`whatsnew/` says on the default branch today rather than the notes that shipped with this binary.

**No mapping is uploaded during promotion**, deliberately. Play already holds the R8 mapping bound to
that bundle's `versionCode` from the internal upload, so re-uploading is redundant — and it is a live
hazard, because `release-android` archives *two* mapping artifacts (`app-mapping.txt` and a
timestamped `app-mapping-<ts>.txt`). Only the timestamped one is the `$BITRISE_MAPPING_PATH` that was
actually deployed; both carry the same `pg_map_id`, so uploading the wrong one succeeds silently and
leaves production stack traces deobfuscating incorrectly. Why the build emits two has not been run to
ground — the Sentry Gradle plugin writing a modified copy is the leading guess, not a verified cause.

The dead-simple alternative is still the Play Console **"Promote release"** button, which does the
same thing without a token.

---

## 4. Why promotion reuses the artifact (the versionCode contract)

Production must receive the **same binary** that passed internal QA. Two hard constraints make this
non-negotiable:

- Play **rejects a different binary that reuses an existing `versionCode`** ("Version code N has
  already been used").
- A rebuild gets a *new* binary; if its `versionCode` differed it would be **code nobody tested**.

So promotion moves the exact internal artifact to production. This is also *why* `versionCode` is
**tag-derived and deterministic** (`v1.0.7` → `10007`) rather than build-number-based — it makes "the
same artifact" a coherent, reproducible concept.

---

## 5. Configuration reference

### Version (`app/build.gradle.kts`)
`versionCode`/`versionName` read `System.getenv("VERSION_CODE"/"VERSION_NAME")`, falling back to the
committed defaults (`9` / `"1.0.6"`) for local/dev builds. The override is read directly from env so
it's independent of the `local.properties`-vs-env signing branch. Bitrise sets them from the tag.

### Env vars / secrets

| Name | Used by | Where it lives |
|---|---|---|
| `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, `RELEASE_STORE_PASSWORD` | Gradle signing | Bitrise **Secrets** |
| `IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET`, `ITAD_API_KEY`, `ITAD_OAUTH_CLIENT_ID` | `buildConfigField` (runtime API access) | Bitrise **Secrets** |
| `SENTRY_DSN` | `buildConfigField` (runtime crash/telemetry ingest; release builds only) | Bitrise **Secrets** |
| `SENTRY_ORG`, `SENTRY_PROJECT`, `SENTRY_AUTH_TOKEN` | Sentry Gradle plugin — R8 mapping upload (build-time). Without the token, the build still produces the mapping but skips the upload. | Bitrise **Secrets** |
| `$BITRISEIO_ANDROID_KEYSTORE_URL` | Keystore download | Published by Bitrise **Code Signing** tab |
| `$BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL` | Play upload auth | Published by a Bitrise **Generic File Storage** secret |
| `VERSION_NAME`, `VERSION_CODE` | Gradle version; iOS `MARKETING_VERSION` / `CURRENT_PROJECT_VERSION` | Computed by the `derive-version` step bundle |
| `BITRISE_API_TOKEN` | `promote-production` — reads the promoted build and its artifacts over the Bitrise API | Bitrise **Secrets** (personal access token, Bitrise → Profile → Security → API tokens) |
| `PROMOTE_BUILD_SLUG` | `promote-production` — which `release-android` build to promote | Set per-run when starting the workflow |
| `PROMOTE_AAB_PATH` | `google-play-deploy` in `promote-production` | Published by that workflow's fetch step |
| App Store Connect API key (Issuer ID, Key ID, `.p8`) | `xcode-archive` signing + TestFlight upload | Bitrise **Workspace → Apple service connection**, selected in the project's Integrations settings. Team key, **Admin** role. |
| `$BITRISE_CERTIFICATE_URL`, `$BITRISE_CERTIFICATE_PASSPHRASE` | `xcode-archive` — Apple Distribution certificate | Published by Bitrise **Code Signing** tab (`.p12` upload) |
| `SENTRY_DSN_IOS`, `SENTRY_PROJECT_IOS` (optional) | iOS overrides of `SENTRY_DSN` / `SENTRY_PROJECT` | Bitrise **Secrets** — only if iOS uses a separate Sentry project |

Locally, the same `RELEASE_*` / `IGDB_*` / `ITAD_*` values, plus `sentryDsn`, come from `local.properties`
(gitignored), and the keystore from `upload_keystore.jks` at the repo root (gitignored). Nothing sensitive
is in git. The `SENTRY_ORG/PROJECT/AUTH_TOKEN` mapping-upload vars are CI-only — local release builds just
generate the mapping and skip the upload.

---

## 6. One-time setup

### Bitrise (UI)
- **Code Signing** tab: upload `upload_keystore.jks` → publishes `$BITRISEIO_ANDROID_KEYSTORE_URL`.
- **Secrets**: the `RELEASE_*`, `IGDB_*`, `ITAD_*`, and `SENTRY_*` values from the table above. The
  `SENTRY_AUTH_TOKEN` needs `project:releases` (org/project-write) scope; create it at Sentry → Settings
  → Auth Tokens. Add `BITRISE_API_TOKEN` too (Bitrise → Profile → Security → API tokens) — only
  `promote-production` reads it, so it can wait until the first production promotion.
- **Generic File Storage**: the Play service-account JSON → `$BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`.
- **Stacks**: set per workflow in `bitrise.yml` — Linux + Android for `release-android` /
  `promote-production`, macOS + Xcode for `release-ios`.
- Connect the GitHub repo. Add the `activate-ssh-key` step's `SSH_RSA_PRIVATE_KEY` only if private.
- Import `bitrise.yml`; the workflow editor validates step `@version` pins — fix any it flags.
- No Gradle build-cache steps are used: the `restore/save-gradle-cache` steps require Bitrise's paid
  Build Cache add-on, and a tag-only release build is too infrequent to benefit. Kept free.

### Apple (App Store Connect + Developer portal)
- Team ID `36N7FDX928` is committed as `DEVELOPMENT_TEAM` in `iosApp.xcodeproj` (not a secret).
- Register App ID `pm.bam.gamedeals.ios` (Identifiers) and create the app record in App Store Connect.
- **Team** API key, **Admin** role (Users and Access → Integrations → App Store Connect API → Team Keys)
  → add to Bitrise as the Apple service connection. The `.p8` downloads once — keep it in a password
  manager.
- **Apple Distribution certificate** → export as `.p12` → Bitrise Code Signing tab. Can be made without a
  Mac: `openssl` CSR → upload at Certificates → **+** → Apple Distribution → download `.cer` →
  combine with the key into a `.p12`. Expires after a year; re-upload when renewed.
- The scheme `iosApp` is shared (`xcshareddata/xcschemes/`) — CI can't build a user-only scheme.

### Play Console + Google Cloud
- Create the app in Play Console; accept agreements; complete the required listing/content forms.
- Enable **Play App Signing** (Google holds the app signing key; `upload_keystore.jks` is the *upload*
  key — keep it backed up off-machine; an upload key can be reset via Play if lost, but avoid it).
- Create a **GCP service account**, enable the **Google Play Android Developer API**, and grant the
  account release rights (internal + production) in Play Console → Users & permissions. Download its
  JSON for the Bitrise file secret.
- **First upload caveat**: the very first AAB on a track sometimes must be uploaded manually before
  API uploads are accepted. If `google-play-deploy` errors on the first run, do one manual internal
  upload, then re-run the tag.

---

## 7. Cutting a release (runbook)

1. Land changes on `dev`/`main`; confirm GHA is green (including `release-verify`).
2. Update `whatsnew/whatsnew-en-US` with real notes.
3. Tag and push: `git tag v1.0.7 && git push origin v1.0.7`.
4. Bitrise pipeline `release` runs automatically → signed AAB on the Play **internal** track and a
   build in **TestFlight** (after Apple's processing, typically 5–30 min).
5. QA from internal (install; sanity-check signing + that IGDB/ITAD keys work at runtime).
6. Promote to production, either:
   - Play Console → release → **Promote release** (Internal → Production), set rollout %; or
   - Bitrise `promote-production` (manual) with `PROMOTE_BUILD_SLUG` set to the `release-android`
     build being promoted — ships at 10% staged.
7. Bump the rollout to 100% in Play Console after monitoring vitals.
8. iOS: in App Store Connect create the version, attach the TestFlight build, submit for review.

---

## 8. Validation status

- ✅ `VERSION_NAME=1.0.7 VERSION_CODE=10007 ./gradlew :app:bundleRelease` → merged manifest
  `versionCode=10007` / `versionName=1.0.7`; R8 + baseline profile + signing all green (~1m52s).
- ✅ `bitrise.yml` and `android.yml` parse as valid YAML.
- ✅ `bitrise validate` passes (pipeline, step bundle, `release-ios`).
- ✅ `release-ios` verified with v1.3.0 (2026-10-02, Bitrise build #19): archive signed with a
  Bitrise-created App Store profile, then uploaded to App Store Connect (TestFlight). Signing must be
  Bitrise-managed (`min_profile_validity > 0`). Otherwise `xcode-archive` hands the project's Xcode
  automatic signing to xcodebuild, which needs a development profile, and that fails because the team
  has no registered devices.
- ⏳ The Bitrise step wiring and the Play upload can only be confirmed once the UI setup (§6) is done
  and a release runs (try a throwaway `v0.0.1-rc1` tag first). The *build* it runs is the same
  `:app:bundleRelease` verified above.

---

## See also
- `docs/r8-mapping.md` — R8 mapping / retrace workflows.
- `docs/release-hardening-audit.md` — the pre-1.0 hardening that preceded this pipeline.
