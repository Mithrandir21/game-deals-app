# Performance Agent — Review Checklist & Rubric

You are evaluating runtime and build-time performance posture. Performance work is
asymmetric: the tools that catch regressions and the patterns that prevent them are
small in number but high-leverage. A project either has them or doesn't.

This isn't about chasing micro-optimizations — it's about asking whether the project
has the **infrastructure** to measure and the **patterns** to prevent the common
performance traps (slow startup, recomposition storms, R8 misconfiguration, leaked
contexts).

**Out of scope for you:**
- Compose adoption broadly, coroutine patterns → Modern Patterns
- Test infrastructure → Testing
- Encrypted storage, secrets → Security & Privacy

---

## 1. Startup Performance

**What to look for:**
- **Baseline Profiles** — `androidx.profileinstaller` dependency on the app, profile
  generated and shipped (`baseline-prof.txt` or its Compose-equivalent location). A
  generation module using **Macrobenchmark** with `BaselineProfileRule`.
- **Startup Profiles** (Compose 1.7+ / AGP 8.3+) — separate profile for cold-start
  composition.
- **App Startup library** (`androidx.startup`) for ordered, lazy initializer execution
  vs `Application.onCreate` sprawl or scattered `ContentProvider` init hacks.
- **Application.onCreate** content: synchronous init of analytics, crash reporters,
  image loaders should be async/lazy where possible. Heavy synchronous init here is
  the #1 cold-start regression source.

**How to investigate:**
```bash
echo "Baseline Profile artifacts:"
find . -name "baseline-prof.txt" -o -name "startup-prof.txt" 2>/dev/null | head -10
grep -rn "androidx.profileinstaller\|profileinstaller\|BaselineProfileRule" \
  --include="*.kts" --include="*.toml" --include="*.kt" . 2>/dev/null | head -10

echo "Macrobenchmark module:"
find . -type d -name "macrobenchmark*" -o -name "benchmark*" 2>/dev/null | head -5
grep -rn "androidx.benchmark.macro\|MacrobenchmarkRule" --include="*.kt" --include="*.toml" . 2>/dev/null | head -5

echo "App Startup library:"
grep -rn "androidx.startup\|InitializationProvider\|Initializer<" --include="*.kt" --include="*.kts" --include="*.toml" --include="*.xml" . 2>/dev/null | head -10

# Application.onCreate content
for app in $(find . -name "*Application.kt" -path "*/src/main/*" 2>/dev/null | head -5); do
  echo "=== $app ==="
  cat "$app"
done
```

**Grading:**
- STRONG: Baseline + Startup Profiles generated and shipped, Macrobenchmark module
  exists and runs in CI, App Startup library used, lean `Application.onCreate`.
- ADEQUATE: Baseline Profile generated but not Startup Profile, or the profile exists
  but isn't regenerated in CI. App init reasonable.
- WEAK: No Baseline Profile, heavy synchronous init in `Application.onCreate`, no
  startup measurement.
- MISSING: No performance infrastructure for startup.

---

## 2. Compose Performance & Stability

**What to look for:**
- **Strong Skipping Mode** — default in Compose Compiler 1.5.4+ / Kotlin 2.0 plugin.
  Confirm not explicitly disabled.
- **Compose Compiler Reports** — at least intermittently generated to spot unstable
  classes and unnecessary recompositions. Look for the metrics report task or generated
  output.
- **Stability annotations** — `@Stable`, `@Immutable` on data classes that need them
  (entities passed to Composables, especially collection-bearing ones). Or use of
  `kotlinx.collections.immutable` (`PersistentList`) instead of `List`.
- **Recomposition hygiene** — no `Modifier` allocation inside Composables (e.g.,
  `Modifier.padding(...)` should be inside the Composable, but functions returning
  `Modifier` constructed at call site can break skipping).
- **`derivedStateOf`** for expensive state derivations.
- **`remember { ... }`** around expensive computations.

**How to investigate:**
```bash
echo "Compose Compiler reports configured:"
grep -rn "composeMetricsDestination\|composeReportsDestination\|reportsDestination\|metricsDestination" \
  --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5

echo "Stability annotations:"
grep -rn "@Stable\|@Immutable" --include="*.kt" . 2>/dev/null | head -10

echo "Immutable collections:"
grep -rn "kotlinx.collections.immutable\|PersistentList\|persistentListOf\|ImmutableList" \
  --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

echo "Strong skipping config:"
grep -rn "enableStrongSkippingMode\|strongSkippingMode\|featureFlag.*StrongSkipping" \
  --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5

echo "derivedStateOf / remember:"
grep -rn "derivedStateOf" --include="*.kt" . 2>/dev/null | wc -l
grep -rn "\bremember\s*{" --include="*.kt" . 2>/dev/null | wc -l

# Generated metrics output
find . -path "*/compose-metrics/*" -o -path "*/compose-reports/*" 2>/dev/null | head -10
```

**Grading:**
- STRONG: Strong Skipping enabled (default), reports generated periodically, stability
  annotations or immutable collections used where needed, `derivedStateOf` present.
- ADEQUATE: Strong Skipping default, but no metrics infrastructure and no proactive
  stability work.
- WEAK: Strong Skipping disabled, raw `List` parameters in hot Composables, recomposition
  not measured.
- N/A: View-based project (note this; still mark MISSING for the recompose checks).

---

## 3. Build Configuration — R8 / Shrinking

**What to look for:**
- `isMinifyEnabled = true` for release.
- `isShrinkResources = true` paired with minification.
- **R8 full mode** — default for AGP 8.x but verify it's not explicitly disabled
  (`android.enableR8.fullMode=false` in `gradle.properties` is a regression).
- Custom ProGuard/R8 rules (`proguard-rules.pro`) — present and reviewed, not just
  copy-pasted from library docs.
- **R8 mapping file** uploaded to Crashlytics/Sentry/Firebase for de-obfuscation.

**How to investigate:**
```bash
grep -rn "isMinifyEnabled\s*=\s*true\|minifyEnabled true" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
grep -rn "isShrinkResources\s*=\s*true\|shrinkResources true" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5

echo "R8 full mode flag:"
grep -rn "android.enableR8.fullMode" gradle.properties 2>/dev/null

find . -name "proguard-rules.pro" -o -name "proguard-*.pro" 2>/dev/null | head -10
for rules in $(find . -name "proguard-rules.pro" 2>/dev/null | head -3); do
  echo "=== $rules ==="
  wc -l "$rules"
done

echo "Mapping upload (Crashlytics/Sentry):"
grep -rn "uploadMappingFile\|mappingFileUploadEnabled\|sentry.*uploadMapping\|crashlytics" \
  --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: R8 minify + resource shrinking on, full mode default not disabled, custom
  rules reviewed, mapping file uploaded to crash reporter.
- ADEQUATE: Minify on but resource shrinking off, or full mode disabled without
  documented reason.
- WEAK: Minify off in release builds.
- MISSING: No `proguard-rules.pro`, no minification.

---

## 4. Macrobenchmark & Continuous Performance Measurement

**What to look for:**
- A separate Macrobenchmark module with `MacrobenchmarkRule`-driven tests for startup,
  scrolling, and key user journeys.
- `FrameTimingMetric` and `StartupTimingMetric` collected.
- Benchmarks run in CI on real or stable virtual devices (GMD), not just locally.
- Microbenchmark module (`androidx.benchmark`) for hot-path code where it matters
  (parsing, encoding, DB queries).

**How to investigate:**
```bash
echo "Macrobenchmark tests:"
find . -path "*macrobenchmark*" -name "*.kt" 2>/dev/null | head -10
grep -rn "MacrobenchmarkRule\|StartupTimingMetric\|FrameTimingMetric\|measureRepeated" \
  --include="*.kt" . 2>/dev/null | head -10

echo "Microbenchmark:"
grep -rn "androidx.benchmark\b\|@RunWith(AndroidJUnit4\|BenchmarkRule" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10

# CI integration for benchmarks
grep -rn "macrobenchmark\|benchmarkRelease" $(find . -path "*/.github/*" 2>/dev/null) 2>/dev/null | head -5
```

**Grading:**
- STRONG: Macrobenchmark module with startup + frame timing tests, run in CI on GMD,
  baselines tracked.
- ADEQUATE: Macrobenchmark tests exist but only run locally / on demand.
- WEAK: No benchmarks; performance measured by intuition.
- MISSING: No performance measurement.

---

## 5. Memory, Leaks, and Resource Management

**What to look for:**
- **LeakCanary** in debug builds.
- Bitmap handling: large images sized appropriately (Coil/Glide configured), no manual
  bitmap loading without `inSampleSize` or coil's `Size`.
- **Context leak risk** — long-lived references to `Activity` context (especially in
  ViewModels — already covered by Layering & Separation, but call out from a memory
  angle if found).
- WorkManager / coroutine scopes properly cancelled, no orphan jobs.
- Image loader memory cache configuration.

**How to investigate:**
```bash
grep -rn "leakcanary\|LeakCanary" --include="*.kts" --include="*.toml" --include="*.kt" . 2>/dev/null | head -10

# Manual bitmap handling
grep -rn "BitmapFactory.decode\|decodeResource\|decodeStream" --include="*.kt" . 2>/dev/null | head -5

# Image loader cache config
grep -rn "memoryCache\|diskCache\|ImageLoader.Builder\|ImageLoaderFactory" --include="*.kt" . 2>/dev/null | head -10

# WorkManager
grep -rn "androidx.work\|WorkManager\|CoroutineWorker" --include="*.kt" --include="*.toml" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: LeakCanary in debug, image loading centralized and configured, WorkManager for
  background tasks, no manual bitmap decoding.
- ADEQUATE: LeakCanary present, image loading sane but uncentralized.
- WEAK: No leak detection, manual bitmap handling, ad-hoc background work.
- MISSING: No memory hygiene tooling.

---

## 6. Jank, Frame Timing, and Tracing

**What to look for:**
- **JankStats** (`androidx.metrics:metrics-performance`) for production frame-time
  monitoring.
- Custom traces (`androidx.tracing.trace("name") { ... }`) on long-running operations
  for Perfetto / Studio Profiler readability.
- **Strict Mode** in debug builds for catching disk/network on main thread.

**How to investigate:**
```bash
grep -rn "JankStats\|androidx.metrics" --include="*.kt" --include="*.toml" . 2>/dev/null | head -5

echo "Custom traces:"
grep -rn "androidx.tracing.trace\|Trace.beginSection\|trace(\"" --include="*.kt" . 2>/dev/null | head -10

echo "StrictMode:"
grep -rn "StrictMode\|ThreadPolicy\|VmPolicy" --include="*.kt" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: JankStats wired up in production, custom traces on key operations, StrictMode
  in debug.
- ADEQUATE: StrictMode present, no JankStats or tracing.
- WEAK: No production frame monitoring, no tracing.
- MISSING: No frame-level performance awareness.

---

## 7. Background Work Discipline

**What to look for:**
- **WorkManager** for deferrable, guaranteed-to-run background tasks.
- **Foreground services** only when actually required (and migrated to the right
  type for SDK 34+ — `dataSync`, `mediaPlayback`, etc., declared in manifest).
- No `BroadcastReceiver` abuse for things WorkManager handles better.
- No `AlarmManager` for routine scheduling (it's for exact-time alarms only).
- Coroutine scopes for background work cancelled appropriately.

**How to investigate:**
```bash
grep -rn "WorkManager\|CoroutineWorker\|@HiltWorker\|Worker " --include="*.kt" . 2>/dev/null | head -10

# Foreground service types (SDK 34+)
grep -rn "foregroundServiceType\|ServiceCompat.startForeground" --include="*.kt" --include="*.xml" . 2>/dev/null | head -10

grep -rn "AlarmManager\|setExact\|setRepeating" --include="*.kt" . 2>/dev/null | head -10

grep -rn "BroadcastReceiver\|registerReceiver" --include="*.kt" --include="*.xml" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: WorkManager for deferred work, foreground services only when needed with
  proper types declared, AlarmManager only for exact alarms.
- ADEQUATE: WorkManager present but some legacy patterns lingering.
- WEAK: AlarmManager / BroadcastReceiver / raw services for routine scheduling.
- MISSING: No structured background work — fire-and-forget coroutines from UI.
