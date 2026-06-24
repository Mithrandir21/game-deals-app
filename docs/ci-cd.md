# CI/CD

How this project is verified and released, and **why** it's set up this way.

- **Verification** → GitHub Actions (`.github/workflows/android.yml`)
- **Releases** → Bitrise (`bitrise.yml`), Android-only for now

```
 PR / push to dev,main ─► GitHub Actions ─► build + unit tests + Compose-stability + (R8 verify)
 push tag v*.*.*       ─► Bitrise        ─► signed AAB ─► Play internal ─► (manual) production
```

The two systems never overlap: GHA's `push` trigger is branch-filtered, so version tags only ever
run on Bitrise.

---

## 1. Decisions & rationale

| Decision | Choice | Why |
|---|---|---|
| Release CI | **Bitrise for releases only**; GHA stays verification | GHA already verifies well; no reason to migrate it. Bitrise's real payoff is iOS (managed signing, macOS minutes), so standing it up now means the release infra already lives on the iOS-friendly platform when iOS ships. |
| Platforms | **Android only** (iOS deferred) | Fastest path to the first Play Store submission. iOS is a live KMP target but has no release lane yet. |
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
    workflow: release-android
```

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

The build steps are just plumbing that feeds env vars and a file into the same `:app:bundleRelease`
build verified locally — no Bitrise-specific signing/versioning magic.

### Workflow `promote-production` (manual)

Triggered by hand after internal QA. It uploads the **already-built** AAB to the `production` track at
a 10% staged rollout (`user_fraction: "0.1"`); bump to 100% in Play Console after watching vitals.

It deliberately **does not rebuild** — see §4. Set `PROMOTE_AAB_PATH` / `PROMOTE_MAPPING_PATH` to the
artifacts archived by the matching `release-android` run. The dead-simple alternative is the Play
Console **"Promote release"** button, which does the same thing.

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
| `VERSION_NAME`, `VERSION_CODE` | Gradle version | Computed in the `release-android` derive-version step |

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
  → Auth Tokens.
- **Generic File Storage**: the Play service-account JSON → `$BITRISEIO_SERVICE_ACCOUNT_JSON_KEY_URL`.
- **Stack**: Linux + Android (no macOS lane while iOS is deferred → lower cost).
- Connect the GitHub repo. Add the `activate-ssh-key` step's `SSH_RSA_PRIVATE_KEY` only if private.
- Import `bitrise.yml`; the workflow editor validates step `@version` pins — fix any it flags.
- No Gradle build-cache steps are used: the `restore/save-gradle-cache` steps require Bitrise's paid
  Build Cache add-on, and a tag-only release build is too infrequent to benefit. Kept free.

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
4. Bitrise `release-android` runs automatically → signed AAB on the Play **internal** track.
5. QA from internal (install; sanity-check signing + that IGDB/ITAD keys work at runtime).
6. Promote to production, either:
   - Play Console → release → **Promote release** (Internal → Production), set rollout %; or
   - Bitrise `promote-production` (manual) with `PROMOTE_AAB_PATH` / `PROMOTE_MAPPING_PATH` set to the
     archived artifacts — ships at 10% staged.
7. Bump the rollout to 100% in Play Console after monitoring vitals.

---

## 8. Validation status

- ✅ `VERSION_NAME=1.0.7 VERSION_CODE=10007 ./gradlew :app:bundleRelease` → merged manifest
  `versionCode=10007` / `versionName=1.0.7`; R8 + baseline profile + signing all green (~1m52s).
- ✅ `bitrise.yml` and `android.yml` parse as valid YAML.
- ⏳ The Bitrise step wiring and the Play upload can only be confirmed once the UI setup (§6) is done
  and a release runs (try a throwaway `v0.0.1-rc1` tag first). The *build* it runs is the same
  `:app:bundleRelease` verified above.

---

## See also
- `docs/r8-mapping.md` — R8 mapping / retrace workflows.
- `docs/release-hardening-audit.md` — the pre-1.0 hardening that preceded this pipeline.
