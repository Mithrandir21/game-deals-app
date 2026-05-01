# Layering & Separation Agent — Review Checklist & Rubric

You are reviewing two intertwined dimensions: **vertical layering** (how data moves from
network/database through domain into the UI) and **separation of concerns** (whether each
class, module, and layer does one thing). They're merged because they share the same
evidence — a leaky ViewModel is both a separation problem and a layering problem.

**Out of scope for you:**
- Module graph and dependency direction → Architecture
- DI configuration → Architecture
- Compose state hoisting and coroutine patterns → Modern Patterns (you cover *what* state
  goes where; they cover *how* it's expressed)
- Test coverage → Testing

---

## 1. Layer Identification & Boundary Enforcement

**What to look for:**
- Distinct Data, Domain, and Presentation layers. Module-level separation is best,
  package-level separation is acceptable, no separation is worst.
- Is the dependency rule respected? Presentation → Domain ← Data, with Domain knowing
  nothing about the other two.
- The domain module's `build.gradle.kts` should have **zero Android dependencies**.
  Ideally a pure Kotlin (or KMP `commonMain`) module.
- Is `internal` visibility used to hide implementation details across module boundaries?

**How to investigate:**
```bash
# Module-level layering
find . -maxdepth 4 -name "build.gradle.kts" 2>/dev/null | grep -v "/build/" | sort
# Domain module deps
for f in $(find . -path "*domain*" -name "build.gradle.kts" 2>/dev/null); do
  echo "=== $f ==="
  grep -E "implementation|api\(|plugins" "$f" | head -15
done
# Domain importing data/UI/framework — a violation
for f in $(find . -path "*domain*" -name "*.kt" -path "*/src/*" 2>/dev/null | head -30); do
  if grep -q "import.*\.data\.\|import.*\.presentation\.\|import.*\.ui\.\|import retrofit\|import androidx.room\|import android\." "$f"; then
    echo "VIOLATION: $f"
    grep -n "import.*\.data\.\|import.*\.presentation\.\|import.*\.ui\.\|import retrofit\|import androidx.room\|import android\." "$f" | head -5
  fi
done
echo "internal modifier count:"
grep -rn "internal class\|internal fun\|internal val\|internal object" --include="*.kt" . 2>/dev/null | grep -v "/test/" | wc -l
```

**Grading:**
- STRONG: Module-level separation. Domain is pure Kotlin (or `commonMain` for KMP) with
  no Android. `internal` used for implementation details. Violations are mechanically
  impossible.
- ADEQUATE: Package-level separation within modules. Layers are identifiable but not
  enforced by the build system. Minor leakage at most.
- WEAK: Layers exist in package naming but classes freely import across them. Data layer
  types appear in UI code.
- MISSING: No layering. Flat package structure with no separation.

---

## 2. ViewModel Responsibility

**What to look for:**
- ViewModels own UI state and orchestrate use cases. They should NOT contain business
  logic, raw data transformation, direct repository calls in a layered project, or
  Android framework references (`Context`, `Activity`, `View`, resource loading).
- Size signal: a ViewModel with 300+ lines or 10+ public functions is a smell.
- Exposes `StateFlow`/`State` with a single screen state object, not a grab-bag of
  individual `LiveData`/`Flow` fields.

**How to investigate:**
```bash
# ViewModel sizes
for vm in $(find . -name "*ViewModel*.kt" -path "*/src/main/*" 2>/dev/null); do
  lines=$(wc -l < "$vm")
  echo "$lines $vm"
done | sort -rn | head -15

# Framework leakage. Note: androidx.lifecycle.* is legitimate and excluded here.
for vm in $(find . -name "*ViewModel*.kt" -path "*/src/main/*" 2>/dev/null); do
  leaks=$(grep -E "^import (android\.|androidx\.(activity|fragment|appcompat|core\.content|core\.app)|androidx\.compose\.(ui|runtime)\b)" "$vm" \
    | grep -v "androidx.lifecycle" | head -5)
  if [ -n "$leaks" ]; then
    echo "FRAMEWORK LEAK: $vm"
    echo "$leaks"
  fi
done

# StateFlow count per ViewModel — many fields = unconsolidated state
for vm in $(find . -name "*ViewModel*.kt" -path "*/src/main/*" 2>/dev/null | head -10); do
  count=$(grep -c "MutableStateFlow\|MutableLiveData\|mutableStateOf\|MutableSharedFlow" "$vm")
  if [ "$count" -gt 2 ]; then
    echo "$count fields: $vm"
  fi
done
```

**Grading:**
- STRONG: Thin orchestrators (typically <150 lines), expose immutable state, delegate to
  use cases, zero framework imports beyond `androidx.lifecycle`.
- ADEQUATE: Reasonable but some contain business logic that belongs in use cases, or
  reference `Context` for non-UI reasons (string resources, etc.).
- WEAK: God classes. Multiple ViewModels are 300+ lines, contain validation, data
  transformation, and direct API calls.
- MISSING: No ViewModels. Logic lives in Activities/Fragments/Composables.

---

## 3. Activity / Fragment / Screen Discipline

**What to look for:**
- In Compose projects: very few Activities (often just `MainActivity`), each a near-empty
  shell that sets content. `@Composable Screen()` functions receive state and emit events
  via callbacks — nothing more.
- In View-based projects: Fragments only bind views and observe the ViewModel.
- No business logic, network calls, or database access in UI components.
- `onActivityResult` / `onRequestPermissionsResult` should be replaced by Activity Result
  APIs or Compose permission helpers (Accompanist Permissions or the platform equivalent).

**How to investigate:**
```bash
for f in $(find . -name "*Activity.kt" -path "*/src/main/*" 2>/dev/null); do
  lines=$(wc -l < "$f"); echo "$lines $f"
done | sort -rn | head -10

for f in $(find . -name "*Fragment.kt" -path "*/src/main/*" 2>/dev/null); do
  lines=$(wc -l < "$f"); echo "$lines $f"
done | sort -rn | head -10

# Logic leaks in UI components
grep -rn "retrofit\|Dao\|Repository\|apiService\|database\|networkClient" --include="*.kt" \
  $(find . -name "*Activity.kt" -o -name "*Fragment.kt" 2>/dev/null | grep "/src/main/") 2>/dev/null | head -15

# Legacy result handling
grep -rn "onActivityResult\|onRequestPermissionsResult" --include="*.kt" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: Activities are launch scaffolding only. Fragments (if any) purely UI. Compose
  screens are stateless except for screen-local UI state.
- ADEQUATE: Mostly clean, some Fragments contain navigation logic or light data
  transformation that belongs in a ViewModel.
- WEAK: Activities/Fragments contain repository calls, data parsing, or business rules;
  multiple are 400+ lines.
- MISSING: All logic lives in Activities. The "Activity-as-God-class" anti-pattern.

---

## 4. Repository Pattern & Data Layer Internal Structure

**What to look for:**
- Repositories are the single source of truth for a data domain.
- They abstract the data source (remote vs local) from consumers.
- Return types: domain models (good) or raw DTOs/entities (bad, leaks the data layer).
- Caching logic lives inside the repository, not in ViewModels.
- Are repositories interfaces (in domain) with implementations (in data), or concrete
  classes? Interface-in-domain is the clean pattern; it inverts the dependency.
- Are remote and local data sources separate classes, or mashed together?
- Is there an explicit caching strategy (offline-first, cache-then-network, network-only)?

**How to investigate:**
```bash
find . -name "*Repository*.kt" -path "*/src/*" 2>/dev/null | head -15
grep -rn "interface.*Repository\|class.*RepositoryImpl\|class.*Repository\b" --include="*.kt" . 2>/dev/null | head -15

# Sample what they return
for repo in $(find . -name "*Repository*.kt" -path "*/src/main/*" 2>/dev/null | head -3); do
  echo "=== $repo ==="
  grep -E "fun |suspend fun |val |Flow<" "$repo" | head -15
done

# Data source split
find . -name "*RemoteDataSource*.kt" -o -name "*LocalDataSource*.kt" -o -name "*ApiDataSource*.kt" 2>/dev/null | grep -v "/test/" | head -10

# Error mapping at boundary
grep -rn "catch\|HttpException\|IOException\|SQLiteException" --include="*.kt" \
  $(find . -path "*/data/*" -name "*.kt" 2>/dev/null | head -20) 2>/dev/null | head -15
```

**Grading:**
- STRONG: Repository interfaces in domain, implementations in data. Separate Remote and
  Local data sources. Repository coordinates them with an explicit caching strategy.
  Errors mapped to domain types at the boundary. Returns domain models.
- ADEQUATE: Repositories handle remote+local in one class but cleanly. Some error mapping.
  Returns mostly domain types.
- WEAK: Thin wrappers around a single API service. No abstraction value. Returns DTOs.
  Errors thrown raw across the boundary.
- MISSING: No repository layer. ViewModels call Retrofit/DAO directly.

---

## 5. Domain Layer & Use Cases

**What to look for:**
- Use cases / interactors that encapsulate business logic.
- Are they real (orchestrate multiple repositories, contain rules) or pure pass-throughs
  (`UseCase.invoke() = repository.get()`)? Pass-throughs are ceremony without value.
- Domain models are framework-free: no `@Entity`, no `@Serializable`/`@SerializedName`,
  no `@Parcelize`, no `android.os.Parcelable`.
- For projects without a formal domain layer: is the business logic at least
  consolidated somewhere reachable, or scattered across ViewModels and repositories?

**How to investigate:**
```bash
find . -name "*UseCase*.kt" -o -name "*Interactor*.kt" 2>/dev/null | grep -v "/test/" | head -15

# Pure pass-through detection (1-line invoke methods)
for uc in $(find . -name "*UseCase*.kt" -path "*/src/main/*" 2>/dev/null | head -10); do
  lines=$(wc -l < "$uc")
  echo "$lines $uc"
done | sort -n | head -10

# Domain model purity
for model in $(find . -path "*/domain/*" -path "*/model/*" -name "*.kt" 2>/dev/null | head -10); do
  echo "=== $model ==="
  grep -E "^import |@Entity|@Serializable|@SerializedName|@Json|@Parcelize|@ColumnInfo" "$model" | head -10
done

# Multi-annotated "God models"
for f in $(find . -name "*.kt" -path "*/model/*" 2>/dev/null | head -30); do
  count=$(grep -cE "@Entity|@Serializable|@SerializedName|@Json|@Parcelize|@ColumnInfo" "$f" 2>/dev/null)
  if [ "${count:-0}" -gt 2 ]; then
    echo "MULTI-ANNOTATED ($count): $f"
  fi
done
```

**Grading:**
- STRONG: Pure-Kotlin domain module. Use cases encapsulate real business rules. Domain
  models are annotation-free.
- ADEQUATE: Domain layer exists but some pass-through use cases. Domain models occasionally
  carry `@Parcelize` or framework imports.
- WEAK: Use cases exist as ceremony, contain no logic. Domain models are annotated for
  Room/Moshi/Serialization.
- MISSING: No domain layer. Business rules live in ViewModels or repositories. Pragmatic
  note: for genuinely simple CRUD apps, omitting a domain layer is a defensible choice
  — flag this, but rate based on whether the resulting ViewModels and repositories
  remain disciplined.

---

## 6. Model Mapping Discipline

**What to look for:**
- Distinct model types per layer:
  - Data: DTOs (network), Entities (Room/SQLDelight)
  - Domain: plain Kotlin data classes / sealed types, no annotations
  - Presentation: UI state classes (`HomeUiState`, `PaymentUiState`)
- Explicit mappers at each boundary: DTO → Domain, Entity → Domain, Domain → UiState.
- Or is a single "God model" used everywhere with stacked framework annotations?
- Are mappers tested? (Cross-reference Testing agent.)

**How to investigate:**
```bash
find . -path "*/dto/*" -o -path "*/response/*" -o -path "*/api/model/*" 2>/dev/null | grep "\.kt$" | head -10
grep -rln "@Entity" --include="*.kt" . 2>/dev/null | head -10
find . -path "*/domain/model/*" -name "*.kt" 2>/dev/null | head -10
find . -name "*Mapper*.kt" -o -name "*Converter*.kt" 2>/dev/null | grep -v "/test/" | head -15
```

**Grading:**
- STRONG: Distinct models per layer with explicit, tested mappers. Domain models
  annotation-free.
- ADEQUATE: Layered models exist but mapping is inconsistent — some boundaries have
  mappers, others pass DTOs through. Mappers exist but aren't tested.
- WEAK: One or two "shared" models used across all layers, annotated for multiple
  frameworks. Mappers exist but most boundary crossings bypass them.
- MISSING: Single model class used from API response to UI binding.

---

## 7. UI State Management

**What to look for:**
- Single immutable state class per screen (`data class HomeUiState(...)`).
- Loading/error/success modeled explicitly — sealed interface (preferred over sealed
  class on Kotlin 1.5+) or fields on the state object.
- Reduction happens in the ViewModel via `.copy()` on a `MutableStateFlow`, not by
  mutating individual fields from multiple coroutines (race-condition risk).
- Side effects (navigation, toasts, snackbars) modeled separately as one-shot events
  via `Channel` or a dedicated `SharedFlow`, not on the state object (replay leaks
  cause double-navigation).

**How to investigate:**
```bash
grep -rn "data class.*State\|data class.*UiState\|sealed interface.*UiState\|sealed class.*UiState\|sealed interface.*Event\|sealed interface.*Effect" \
  --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -20

# State reduction patterns
grep -rn "\.copy(\|reduce(\|update {" --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -15

# Channel/SharedFlow for events
grep -rn "Channel<\|MutableSharedFlow.*replay\s*=\s*0" --include="*.kt" . 2>/dev/null | head -10
```

**Grading:**
- STRONG: Single immutable state per screen, reduced via `.update {}` / `.copy()`,
  side effects via `Channel`/`SharedFlow` with replay=0, sealed interfaces for variants.
- ADEQUATE: State mostly consolidated but some screens use multiple StateFlows. Side
  effects handled but inconsistently.
- WEAK: Mutable state fields updated individually from multiple coroutines. No explicit
  loading/error modeling. Side effects ad-hoc.
- MISSING: No state management pattern. Fragments mutate views directly.

---

## 8. Feature Boundary Discipline

**What to look for:**
- Each feature module owns its full vertical slice: UI + ViewModel + use cases +
  feature-specific data, with the feature's API surface narrow.
- Features don't reach into each other's internals. Cross-feature navigation goes
  through a shared navigation contract.
- Common/core modules are lean (models, interfaces, utilities) — not implementation
  dumps.

**How to investigate:**
```bash
for feature in $(find . -maxdepth 3 -type d -name "feature*" -o -name "feat-*" 2>/dev/null | head -8); do
  echo "=== $feature ==="
  find "$feature" -name "*.kt" -path "*/src/main/*" 2>/dev/null | head -8
done

# Cross-feature imports (smell)
for feature_dir in $(find . -maxdepth 3 -type d -name "feature*" 2>/dev/null | head -5); do
  feature_name=$(basename "$feature_dir")
  cross=$(grep -rn "import.*\.feature\." --include="*.kt" "$feature_dir" 2>/dev/null \
    | grep -v "$feature_name" | head -5)
  if [ -n "$cross" ]; then
    echo "=== $feature_name imports other features ==="
    echo "$cross"
  fi
done
```

**Grading:**
- STRONG: Self-contained vertical slices, cross-feature communication via shared
  interfaces or navigation abstractions, lean common module.
- ADEQUATE: Features mostly own their slice but share repositories or data sources
  that should be feature-specific.
- WEAK: Features depend on each other's internals. Changing one requires modifying another.
- MISSING / N/A: Single-module project — flag for the Architecture agent's modularization
  recommendation.

---

## 9. KMP Source-Set Discipline (if applicable)

**Skip if no `kotlin("multiplatform")` plugin present.**

**What to look for:**
- What's in `commonMain` vs `androidMain` vs `iosMain`? Common should hold business
  logic, models, and abstract interfaces. Platform sources should hold only the
  platform-specific bits.
- `expect`/`actual` usage: minimal, well-motivated, located at clear seams (Settings,
  HTTP engine, DB driver, dispatcher) — not sprawled across the domain.
- Are domain models in `commonMain` framework-free in the KMP sense (no Android,
  no Foundation, no Java stdlib)?

**How to investigate:**
```bash
echo "commonMain Kotlin files:"
find . -path "*/commonMain/*" -name "*.kt" 2>/dev/null | wc -l
echo "androidMain Kotlin files:"
find . -path "*/androidMain/*" -name "*.kt" 2>/dev/null | wc -l
echo "iosMain Kotlin files:"
find . -path "*/iosMain/*" -name "*.kt" 2>/dev/null | wc -l

grep -rn "^expect " --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -15
grep -rn "^actual " --include="*.kt" . 2>/dev/null | grep -v "/test/" | head -15
```

**Grading:**
- STRONG: Clean shared boundary, minimal `expect`/`actual` at clear seams, common code
  is platform-free.
- ADEQUATE: KMP is used but boundary is fuzzy — too much or too little is shared.
- WEAK: KMP set up but barely used, or shared module has platform leakage (Android
  imports in `commonMain`).
- N/A: Not a KMP project.
