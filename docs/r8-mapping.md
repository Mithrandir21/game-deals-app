# R8 mapping — using release-build mappings to deobfuscate stack traces

**Status:** active. R8 minification + resource shrinking are enabled on `:app`'s release variant (PR #174). The release build produces a mapping file that turns obfuscated names back into the original Kotlin classes/methods.

This doc is the operational reference for **getting from an obfuscated logcat/crash trace back to source code**.

---

## Where the mapping files live

Every `./gradlew :app:assembleRelease` (or `bundleRelease`) writes its R8 outputs into `app/build/outputs/mapping/release/`:

| File | Purpose |
|---|---|
| **`mapping.txt`** | The obfuscation map. Obfuscated class/method/field → original. **This is the file you feed to retrace tools.** |
| `seeds.txt` | What R8 kept (matched a `-keep` rule). |
| `usage.txt` | What R8 removed. Searchable when a runtime crash points to a stripped class — confirms whether you need a new keep rule. |
| `configuration.txt` | Final merged ProGuard config R8 actually applied (after combining `proguard-android-optimize.txt`, AGP defaults, and our `app/proguard-rules.pro`). Useful when debugging keep-rule conflicts. |
| `resources.txt` | Report of which resources R8 kept/removed. |
| `mapping.prt` | R8's compact internal partition map. Not used by retrace tools — ignore. |

Full absolute path on this machine:

```
/Users/bam/REPO/PRIVATE/game-deals-android-app/app/build/outputs/mapping/release/mapping.txt
```

---

## The critical pairing rule

R8 obfuscation is **not deterministic across builds**. A symbol that's `a.b.c` in one build may be `a.b.d` in the next. You must pair the obfuscated trace with **the exact `mapping.txt` produced by the same build that emitted the trace.**

Practical implication when you grab a release APK for testing:

```bash
# When you build a release APK to test, archive its mapping alongside the APK:
mkdir -p /tmp/release-$(date +%Y-%m-%d-%H%M)
cp app/build/outputs/apk/release/app-release.apk /tmp/release-$(date +%Y-%m-%d-%H%M)/
cp app/build/outputs/mapping/release/mapping.txt /tmp/release-$(date +%Y-%m-%d-%H%M)/
```

If you re-run `assembleRelease` before saving the mapping, the previous mapping is overwritten and any logcat trace you captured from the previous APK becomes unretraceable. Treat `mapping.txt` as a build artifact, not a local cache.

---

## Retracing a stack trace — three methods

### 1. Android Studio (easiest for interactive debugging)

**Live deobfuscation in the Logcat panel.**

1. Open the **Logcat** tool window.
2. Click the wrench/gear icon → **Configure Logcat**.
3. Set **ProGuard mapping file** to:
   `<repo>/app/build/outputs/mapping/release/mapping.txt`
4. Logcat now deobfuscates stack traces inline as they arrive.

**One-off paste.**

1. Menu → **Analyze** → **Analyze Stack Trace…**
2. Tick **Use ProGuard / R8 mapping file**.
3. Browse to `mapping.txt`.
4. Paste the obfuscated stack trace into the dialog.

Android Studio also opens the source location when you click a frame in the deobfuscated trace.

### 2. `retrace.sh` from the command line

The Android SDK ships `retrace.sh`. On this machine:

```
/Users/bam/Library/Android/sdk/tools/proguard/bin/retrace.sh
```

Pipe a live `adb logcat`:

```bash
adb logcat -d | \
  /Users/bam/Library/Android/sdk/tools/proguard/bin/retrace.sh \
  app/build/outputs/mapping/release/mapping.txt
```

Or feed a saved trace file:

```bash
/Users/bam/Library/Android/sdk/tools/proguard/bin/retrace.sh \
  app/build/outputs/mapping/release/mapping.txt \
  /path/to/obfuscated-trace.txt
```

If `retrace.sh` is not on `PATH`, install via SDK Manager → **SDK Tools** → tick **Android SDK Tools (Obsolete)**. R8 itself also bundles a retrace JAR under `~/.gradle/caches/transforms-*/`, but the SDK path above is the stable one.

### 3. Sentry auto-upload (future, when `:logging` Sentry wiring lands)

When the iOS/Android Sentry KMP wiring becomes the real crash sink (candidate #1 from the post-Kotlin-2.3 unblocks survey), the `sentry-android-gradle-plugin` will upload `mapping.txt` per release build with a stable identifier (UUID embedded in the APK manifest). Sentry then auto-deobfuscates every crash event in its dashboard. No manual retrace.

Until then, retracing is manual.

---

## When a trace reveals a stripped class

Symptom: a release-build crash points at something like `java.lang.ClassNotFoundException` for a class that exists in source, or `kotlinx.serialization.SerializationException: Class 'X' is not registered for polymorphic serialization`, or a reflection call returns null where it shouldn't.

Workflow:

1. **Confirm via `usage.txt`.** Search for the original class name (not the obfuscated one):

   ```bash
   grep -F 'pm.bam.gamedeals.domain.models.GameDetails' \
     app/build/outputs/mapping/release/usage.txt
   ```

   If the class (or specific members) appear in `usage.txt`, R8 stripped them. That's the smoking gun.

2. **Check whether `seeds.txt` already keeps it.** If yes, the keep rule exists but didn't match (e.g., scoped to `class` but the member was a constructor; or scoped to `members` but the class itself got renamed). Refine the rule.

3. **Add a keep rule to `app/proguard-rules.pro`.** Examples:

   ```proguard
   # Keep a specific class and all of its members
   -keep class pm.bam.gamedeals.domain.models.GameDetails { *; }

   # Keep a class but allow R8 to remove unused members (smaller APK)
   -keep class pm.bam.gamedeals.domain.models.GameDetails

   # Keep all @Serializable classes by package
   -keep,includedescriptorclasses class pm.bam.gamedeals.**$$serializer { *; }
   -keepclassmembers class pm.bam.gamedeals.** {
       *** Companion;
   }
   ```

   Then rebuild and reproduce. The keep rule's effect is visible in the new `seeds.txt`.

4. **Add a comment on the rule.** A single-line `# Why: reflection from Koin module registration` is plenty. A rule with no rationale is the kind of thing that gets removed in a future cleanup and breaks production a release later.

5. **Watch the APK size.** Each keep rule blocks shrinking. After fixing the regression, check `ls -lh app/build/outputs/apk/release/app-release.apk` and confirm the size didn't balloon. The R8 baseline is around 6.9 MB on this project; a keep rule that re-adds megabytes is over-broad.

---

## Smoke-testing a release build locally

Release signing in this repo uses `upload_keystore.jks`. CI has the keystore as a secret; local machines that don't have it can't sign a real release APK. For local R8 smoke-testing only, swap the release signing config to the debug key in `app/build.gradle.kts`:

```kotlin
// TEMP-LOCAL-DO-NOT-COMMIT: force debug-signed release APK for local R8 smoke testing
// when upload_keystore.jks is not on this machine. Remove before commit/push.
releaseSigningKey = "debug"
```

The marker `TEMP-LOCAL-DO-NOT-COMMIT` makes the local-only swap obvious in `git status` and `git diff`. Revert before pushing:

```bash
git checkout -- app/build.gradle.kts
```

Then build and install:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:assembleRelease
./gradlew :app:installRelease
```

Issue #170 (`:remote` hardcoded `RemoteBuildType.DEBUG`) is unrelated to R8 — release-only behaviour gated on build type won't change until that issue lands its fix. R8 itself works correctly.

---

## CI mapping artifact (future)

The current `.github/workflows/android.yml` does not upload `mapping.txt` from CI release builds. When CI starts producing signed release APKs for distribution, add an artifact upload step:

```yaml
- name: Upload R8 mapping
  if: success()
  uses: actions/upload-artifact@v4
  with:
    name: r8-mapping-${{ github.sha }}
    path: app/build/outputs/mapping/release/
    retention-days: 90
```

The 90-day retention covers the typical window between a release shipping and an obfuscated crash report arriving from the field.

---

## References

- R8 user guide: <https://r8.googlesource.com/r8/+/refs/heads/main/README.md>
- AGP shrink, obfuscate, optimize: <https://developer.android.com/build/shrink-code>
- Kotlin serialization R8 / ProGuard rules: <https://github.com/Kotlin/kotlinx.serialization#android>
- Coil 3 ProGuard rules (already shipped consumer rules via the AAR): <https://coil-kt.github.io/coil/proguard/>
- Sentry-KMP mapping upload (when wired): <https://docs.sentry.io/platforms/kotlin/guides/kotlin-multiplatform/configuration/integrations/proguard/>
