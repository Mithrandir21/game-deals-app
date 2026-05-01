# Modern Patterns & Tooling Agent — Review Checklist & Rubric

You are evaluating whether the project leverages the modern Android ecosystem effectively.
This isn't trend-chasing — it's about identifying where outdated tooling or patterns
create real friction: slower builds, worse developer experience, harder testing, or
maintenance burden.

**Out of scope for you:**
- Module graph and DI setup → Architecture
- ViewModel responsibility, state hierarchy shape → Layering & Separation
- Test infrastructure → Testing
- Baseline Profiles, R8 tuning, Macrobenchmark → Performance
- Encrypted storage, network security config → Security & Privacy

---

## 1. UI Framework — Compose Adoption & Quality

**What to look for:**
- Project status: fully Compose, hybrid Compose+Views, or legacy Views.
- **Kotlin 2.0+ Compose Compiler plugin** (`org.jetbrains.kotlin.plugin.compose`).
  Pre-2.0 projects used the `androidx.compose.compiler:compiler` artifact; modern
  projects use the Kotlin-bundled compiler plugin.
- Compose BOM in use for version alignment.
- **Stateless Composables with hoisted state.** Functions take state and emit events.
- **Anti-patterns**: `CoroutineScope`/`GlobalScope` in composition, `mutableStateOf`
  outside `remember`, side effects in composition (use `LaunchedEffect`).
- `collectAsStateWithLifecycle` (lifecycle-aware) used as the default, not
  `collectAsState` (which keeps collecting in the background).
- **Edge-to-edge.** `enableEdgeToEdge()` is required for SDK 35+ targeting (Android 15
  enforces it).
- **Predictive back gesture.** `PredictiveBackHandler` (Compose 1.7+) wired up.
- Previews exist for screens — and ideally `@PreviewParameter` for state variants.

**How to investigate:**
```bash
echo "@Composable functions:"
grep -rln "@Composable" --include="*.kt" . 2>/dev/null | wc -l
echo "XML layout files:"
find . -name "*.xml" -path "*/layout/*" 2>/dev/null | wc -l

# Compose Compiler plugin
grep -rn "org.jetbrains.kotlin.plugin.compose\|kotlin.plugin.compose\|compose-compiler" \
  --include="*.kts" --include="*.toml" --include="*.gradle" . | head -5

grep -rn "compose-bom\|composeBom\|compose\.bom\|androidx-compose-bom" \
  --include="*.kts" --include="*.toml" . | head -5

# Lifecycle-aware Flow collection
echo "collectAsStateWithLifecycle usages:"
grep -rn "collectAsStateWithLifecycle" --include="*.kt" . 2>/dev/null | wc -l
echo "collectAsState (non-lifecycle) usages:"
grep -rn "collectAsState\b" --include="*.kt" . 2>/dev/null | grep -v "WithLifecycle" | wc -l

# Edge-to-edge
grep -rn "enableEdgeToEdge\|WindowCompat.setDecorFitsSystemWindows" --include="*.kt" . 2>/dev/null | head -5

# Predictive back
grep -rn "PredictiveBackHandler\|enableOnBackInvokedCallback" --include="*.kt" --include="*.xml" . 2>/dev/null | head -5

# Anti-patterns: scopes inside Composables
for f in $(grep -rl "@Composable" --include="*.kt" . 2>/dev/null | head -30); do
  if grep -q "GlobalScope\|CoroutineScope(" "$f"; then
    echo "SCOPE IN COMPOSE: $f"
  fi
done

# Previews
grep -rn "@Preview\|@PreviewParameter" --include="*.kt" . 2>/dev/null | wc -l
```

**Grading:**
- STRONG: Fully Compose (or actively migrating with a clear boundary). Compose Compiler
  on Kotlin 2.0+ plugin, BOM in use, stateless Composables, `collectAsStateWithLifecycle`
  default, `enableEdgeToEdge` wired up, predictive back handled, previews common.
- ADEQUATE: Compose used for new screens, no major anti-patterns, but some lifecycle
  collection is unsafe or edge-to-edge isn't wired.
- WEAK: Compose used poorly — `mutableStateOf` outside `remember`, God-Composables (300+
  lines), no previews, `collectAsState` everywhere.
- LEGACY (not automatically WEAK): Entire UI is XML + ViewBinding. Call this out and
  recommend a migration path; whether it's a real problem depends on team and roadmap.

---

## 2. Coroutines, Flow, and Reactive Patterns

**What to look for:**
- Structured concurrency: coroutines launched in `viewModelScope`, `lifecycleScope`,
  or scoped `CoroutineScope`s. **No `GlobalScope`** outside narrow infrastructure cases.
- `StateFlow` for state, `SharedFlow` (replay=0) for events, `callbackFlow` for bridging.
- Cold Flows collected with lifecycle awareness: `repeatOnLifecycle` for non-Compose,
  `collectAsStateWithLifecycle` for Compose.
- **Dispatcher injection.** Production code injects a `CoroutineDispatcher` (or a
  `DispatcherProvider`) rather than hardcoding `Dispatchers.IO`. This is what makes
  ViewModels testable.
- RxJava status: present? Migration in progress, or the primary reactive framework?
- Sealed **interfaces** preferred over sealed classes for new state hierarchies (better
  with `when`, allow non-data subtypes more gracefully).

**How to investigate:**
```bash
echo "GlobalScope usages (red flag):"
grep -rn "GlobalScope" --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -10

echo "Hardcoded dispatchers in production code:"
grep -rn "Dispatchers\.\(IO\|Default\|Main\)" --include="*.kt" . 2>/dev/null \
  | grep -v "/test/" | grep -v "DispatcherProvider\|@Inject\|@Provides" | head -15

grep -rn "repeatOnLifecycle\|collectAsStateWithLifecycle" --include="*.kt" . 2>/dev/null | head -10

echo "Flow types:"
grep -rn "MutableStateFlow\|MutableSharedFlow" --include="*.kt" . 2>/dev/null | grep -v "/test/" | wc -l
echo "LiveData (legacy):"
grep -rn "MutableLiveData\|liveData\s*{" --include="*.kt" . 2>/dev/null | grep -v "/test/" | wc -l

grep -rn "rxjava\|rxandroid\|RxJava\|^import io.reactivex" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -10

echo "Sealed interface vs sealed class usage:"
grep -rn "^sealed interface" --include="*.kt" . 2>/dev/null | wc -l
grep -rn "^sealed class" --include="*.kt" . 2>/dev/null | wc -l
```

**Grading:**
- STRONG: Coroutines throughout, structured concurrency, lifecycle-aware Flow collection,
  injected `DispatcherProvider`, no `GlobalScope`, sealed interfaces preferred. RxJava
  absent or being actively retired.
- ADEQUATE: Coroutines used; some lifecycle collection is unsafe, or LiveData still
  used alongside Flow, or dispatchers are sometimes hardcoded.
- WEAK: `GlobalScope` usages, Flows collected unsafely, mix of RxJava and coroutines
  with no migration plan, dispatchers always hardcoded.
- MISSING: Still using AsyncTask/HandlerThread/raw Threads. Or RxJava-only.

---

## 3. Gradle, KSP, and Build Tooling

**What to look for:**
- **Version Catalog (`gradle/libs.versions.toml`).** Single source of truth for
  versions. No hardcoded versions in module `build.gradle.kts`.
- **KSP, not kapt.** kapt is in maintenance mode. As of Hilt 2.48+, Room, Moshi,
  Dagger Hilt, and most major processors have KSP support. Any kapt usage is now a
  build-speed regression.
- **KSP2** (the new XCFramework-style command-line implementation) where supported.
- **Convention plugins.** A `build-logic` (or `build-logic/convention`) module
  containing Gradle convention plugins, the NowInAndroid pattern.
- **Configuration cache** enabled in `gradle.properties`
  (`org.gradle.configuration-cache=true`).
- Recent **Gradle + AGP**. AGP 8.5+ is the current expectation; AGP 7.x is dated.
- **Kotlin 2.x** (K2 stable since 2.0). Projects on 1.9 should be migrating.

**How to investigate:**
```bash
if [ -f gradle/libs.versions.toml ]; then
  echo "Version catalog: $(wc -l < gradle/libs.versions.toml) lines"
  echo "Hardcoded version strings outside catalog:"
  grep -rn 'implementation\s*"[^"]*:[0-9]' --include="*.kts" --include="*.gradle" . 2>/dev/null \
    | grep -v "/build/" | grep -v "libs\." | head -10
else
  echo "NO version catalog"
fi

echo "kapt usages:"
grep -rln "kapt\b\|kotlin(\"kapt\")\|id(\"kotlin-kapt\")" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -10
echo "KSP usages:"
grep -rln "ksp\b\|com.google.devtools.ksp" --include="*.kts" --include="*.gradle" --include="*.toml" . 2>/dev/null | head -10

ls build-logic/convention/src 2>/dev/null && echo "convention plugins present"
ls buildSrc/src 2>/dev/null && echo "buildSrc present"

cat gradle.properties 2>/dev/null | grep -E "configuration-cache|caching|parallel" || echo "no perf flags found"

grep distributionUrl gradle/wrapper/gradle-wrapper.properties 2>/dev/null
grep -E "android.gradle|com.android.application|kotlin.*version" gradle/libs.versions.toml 2>/dev/null | head -10
```

**Grading:**
- STRONG: Catalog as single truth, KSP everywhere (no kapt), convention plugins, recent
  Gradle/AGP/Kotlin, configuration cache enabled.
- ADEQUATE: Catalog present but some leakage, 1–2 processors still on kapt, no convention
  plugins but build files are consistent.
- WEAK: No catalog, kapt throughout, old Gradle, no build optimization.
- MISSING: Build files are inconsistent, copy-pasted, deprecated plugins.

---

## 4. Networking & Serialization

**What to look for:**
- HTTP client: Retrofit + OkHttp (Android-only), Ktor Client (KMP-friendly), or
  something else.
- Serialization: **Kotlinx Serialization** is the modern default for both Android-only
  and KMP. Moshi with KSP codegen is acceptable. **Gson uses reflection and is the
  least modern choice** (and slower).
- API surface: do services return domain models, or do response DTOs leak into
  ViewModels? (Cross-reference with Layering & Separation.)
- Interceptors: logging, auth, error mapping — applied consistently.

**How to investigate:**
```bash
grep -rn "Retrofit\|OkHttp\|HttpClient\|io.ktor" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -10

echo "Kotlinx Serialization:"
grep -rln "kotlinx.serialization\|@Serializable" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
echo "Moshi:"
grep -rln "com.squareup.moshi\|@JsonClass\|@Json" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
echo "Gson:"
grep -rln "com.google.code.gson\|@SerializedName" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5

find . -name "*Interceptor*.kt" -path "*/src/main/*" 2>/dev/null | head -5
```

**Grading:**
- STRONG: Retrofit or Ktor + Kotlinx Serialization (or Moshi KSP), proper interceptor
  chain, response models mapped to domain at the boundary.
- ADEQUATE: Retrofit + Moshi (reflection) or older Gson with minor leaking. Interceptors
  present.
- WEAK: Gson throughout, no interceptor chain, raw Response objects in ViewModels.
- MISSING: HttpURLConnection or no abstraction layer.

---

## 5. Local Storage

**What to look for:**
- **Room with KSP** (or **Room KMP** for KMP projects), **SQLDelight** (KMP-first),
  or raw SQLite.
- **DataStore** (Proto preferred over Preferences for typed schemas) — not
  `SharedPreferences`. SharedPreferences is in maintenance and has known bugs
  (sync I/O on commit, race conditions).
- DAOs return `Flow` for reactive queries, `suspend` for one-shot reads.
- Schema migrations: tested via `MigrationTestHelper` (Room) or equivalent.

**How to investigate:**
```bash
grep -rln "@Database\|@Dao\|@Entity" --include="*.kt" . 2>/dev/null | head -10
find . -name "*.sq" 2>/dev/null | head -5

echo "DataStore usages:"
grep -rn "DataStore\|preferencesDataStore\|dataStoreFile" --include="*.kt" . 2>/dev/null | head -10
echo "SharedPreferences usages:"
grep -rn "SharedPreferences\|getSharedPreferences\|getDefaultSharedPreferences" --include="*.kt" . 2>/dev/null | head -10

# DAO return shapes
for dao in $(find . -name "*Dao*.kt" -path "*/src/main/*" 2>/dev/null | head -3); do
  echo "=== $dao ==="
  grep -E "fun |suspend fun |Flow<" "$dao" | head -10
done

# Migrations
grep -rn "Migration(\|MigrationTestHelper\|fallbackToDestructiveMigration" --include="*.kt" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: Room (KSP) or SQLDelight, Flow-returning DAOs, DataStore for preferences,
  migrations exist and are tested.
- ADEQUATE: Room present but DAOs return raw entities, or SharedPreferences still used
  alongside DataStore.
- WEAK: SharedPreferences for everything, Room with kapt, no migration strategy.
- MISSING: No structured local storage, or raw SQLite with string-concatenated queries.

---

## 6. Image Loading

**What to look for:**
- **Coil 3** (KMP-capable, Compose-native, Kotlin-first) — current default.
- **Glide** is fine if used consistently.
- **Picasso** is dated.
- Centralized `ImageLoader` configuration: caching, placeholders, error drawables.

**How to investigate:**
```bash
grep -rn "io.coil-kt\.coil3\|io.coil-kt\b\|com.github.bumptech.glide\|com.squareup.picasso" \
  --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "AsyncImage\|rememberAsyncImagePainter\|ImageLoader\|GlideImage\|coil3" \
  --include="*.kt" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: Coil 3 with centralized `ImageLoader`, configured cache.
- ADEQUATE: Glide used consistently and configured.
- WEAK: Mixed libraries, no central configuration.
- N/A: App doesn't load remote images.

---

## 7. Accessibility

**What to look for:**
- Compose: `Modifier.semantics`, `contentDescription`, semantic merging where
  appropriate, role declarations (`Role.Button`, `Role.Checkbox`).
- Touch target sizes meet 48dp minimum (`Modifier.minimumInteractiveComponentSize()`
  is on by default, but custom buttons may opt out).
- Color contrast and text scaling (does the UI break with `fontScale = 2.0f`?).
- Test attribute: `Modifier.testTag` should be used sparingly — semantics is the better
  test surface and also serves accessibility.
- View-based projects: `android:contentDescription` on `ImageView`, `labelFor` on
  inputs, focus order.

**How to investigate:**
```bash
echo "Compose semantics usage:"
grep -rn "Modifier\.semantics\|contentDescription\s*=\|Role\." --include="*.kt" . 2>/dev/null | wc -l
echo "@Composable functions (denominator):"
grep -rn "@Composable" --include="*.kt" . 2>/dev/null | wc -l

# View-based content descriptions
grep -rn "android:contentDescription\|labelFor" --include="*.xml" . 2>/dev/null | wc -l
find . -name "*.xml" -path "*/layout/*" 2>/dev/null | wc -l

# Accessibility-aware previews
grep -rn "@Preview.*fontScale\|@Preview.*locale" --include="*.kt" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Semantic descriptions on interactive elements, accessibility-aware previews,
  tests use semantics not testTags.
- ADEQUATE: Some content descriptions, but inconsistent. testTags overused.
- WEAK: Almost no `contentDescription`, no consideration of TalkBack, no font-scale
  testing.
- MISSING: Zero accessibility consideration.

---

## 8. Release Health & Distribution

**What to look for:**
- **App Bundle (.aab)** vs APK build outputs.
- **R8 enabled** for release with `isMinifyEnabled = true`. (Deeper R8 tuning is a
  Performance concern; here we just confirm it's on.)
- **Play Integrity API** (or other anti-tamper) for sensitive apps.
- **Signing config** that doesn't commit keys (handled by Security agent in detail).
- **App Startup library** for initializer ordering — vs ad-hoc `ContentProvider` hacks
  or `Application.onCreate` overload.
- **Crash reporter** wired up (covered by Architecture agent for the abstraction;
  here just confirm a release reporter exists).

**How to investigate:**
```bash
grep -rn "isMinifyEnabled\s*=\s*true\|minifyEnabled true" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
grep -rn "isShrinkResources\|shrinkResources" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
grep -rn "androidx.startup\|InitializationProvider\|Initializer<" --include="*.kt" --include="*.kts" --include="*.toml" --include="*.xml" . 2>/dev/null | head -5
grep -rn "PlayIntegrity\|integrityManager" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
# Bundle vs APK
grep -rn "bundle\s*{" --include="*.kts" --include="*.gradle" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: AAB output, R8 + resource shrinking on for release, App Startup library used,
  Play Integrity where appropriate.
- ADEQUATE: R8 enabled, but no App Startup, no Play Integrity.
- WEAK: R8 disabled or minify off in release, ad-hoc init in `Application`.
- MISSING: APK only, no minification.

---

## 9. Kotlin Multiplatform — Library Choices (if applicable)

**Skip if no `kotlin("multiplatform")` plugin present.** (Source-set discipline is
covered by Layering & Separation; here we focus on library choices.)

**What to look for:**
- Networking: **Ktor Client** (the KMP default).
- Serialization: **Kotlinx Serialization**.
- DB: **SQLDelight** or **Room KMP** (Room added KMP support; SQLDelight remains the
  more mature option).
- DI: **Koin** or **Kotlin Inject** (Hilt is Android-only).
- iOS interop: **SKIE**, **KMP-NativeCoroutines**, or raw Objective-C interop.
  SKIE is the modern preference for Swift-friendly suspend/Flow types.
- DateTime: `kotlinx-datetime`, not `java.time`.

**How to investigate:**
```bash
grep -rn "io.ktor\|ktor-client" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "app.cash.sqldelight\|androidx.room.*multiplatform" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "io.insert-koin\|me.tatarka.inject\|kotlin-inject" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "co.touchlab.skie\|com.rickclephas.kmp" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
grep -rn "kotlinx-datetime\|kotlinx.datetime" --include="*.kt" --include="*.kts" --include="*.toml" . 2>/dev/null | head -5
```

**Grading:**
- STRONG: Ktor + Kotlinx Serialization + SQLDelight/Room KMP + Koin or Kotlin Inject +
  SKIE for iOS interop + kotlinx-datetime.
- ADEQUATE: Most modern KMP libraries, but iOS interop is rough or DateTime mixes
  java.time/Foundation.
- WEAK: KMP set up but using mostly Android-only libraries, forcing platform-specific
  rewrites.
- N/A: Not a KMP project.
