---
**Path scope:** `app/**`, `common/**`, `domain/**`, `feature/**`, `logging/**`, `remote/**`, `settings.gradle.kts`, `build-logic/**`, `*/build.gradle.kts`, `MODULES.md`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Architecture

This codebase practices structured multi-module architecture with layered separation (domain-first, remote adapters, feature isolation) and type-safe navigation, enabling independent feature development and clean dependency boundaries.

## Patterns

### Type-Safe Navigation with Serializable Destinations

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 feature modules + webview

**The pattern.**
Define all navigation destinations as `@Serializable` cases on a sealed `Destination` interface in `:common:navigation` (e.g., `Destination.Home`, `Destination.Game(gameId: Int)`), then reference them directly in `NavHost` and `navController.navigate()`. Argument types are verified at compile-time, not runtime. Feature modules export navigation functions (`fun NavGraphBuilder.homeScreen(...)`) that the app orchestrates in `NavGraph.kt`.

**Why this works for us.**
Compile-time safety eliminates string-based route typos and argument mismatches. Centralized `Destination` definitions prevent the circular-dependency trap: features never import app code, and app imports each feature's navigation function. The `Serializable` decorator handles serialization automatically for safe parcelization.

**Known trade-offs / when it strains.**
First-build verification can be slow on Android Studio sync. Manual updates to `Destination.kt` are required when adding new routes; the pattern trades some IDE support for explicit control. Feature navigation functions take callback parameters (e.g., `goToGame: (Int) -> Unit`) rather than direct nav calls, adding a layer of indirection.

**How to apply it.**
```kotlin
// :common:navigation
sealed interface Destination {
  @Serializable data object Home : Destination
  @Serializable data class Game(val gameId: Int) : Destination
}

// :feature:home navigation
fun NavGraphBuilder.homeScreen(goToGame: (Int) -> Unit) {
  composable<Destination.Home> {
    HomeScreen(onGameTapped = { goToGame(it) })
  }
}

// :app NavGraph
NavHost(navController, startDestination = Destination.Home) {
  homeScreen(goToGame = { navActions.navigateToGame(it) })
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/navigation/NavGraph.kt
- app/src/main/java/pm/bam/gamedeals/navigation/Navigation.kt
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/navigation/Destination.kt
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/navigation/HomeNavigation.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/navigation/GameNavigation.kt

### Port/Adapter Pattern for Remote Data Sources

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** domain sources (CheapsharkSource, GamerPowerSource) + their remote implementations

**The pattern.**
Data-source interfaces live in `:domain` (e.g., `CheapsharkSource`) and return only domain models. Implementations live in `:remote:*` submodules (`:remote:cheapshark`, `:remote:gamerpower`) where they depend on transport DTOs, Retrofit APIs, and mappers. `:domain` never imports anything from `:remote:*`. Hilt wires implementations at the app level (`:app` imports both, allowing Hilt's `@Provides` to bind interface to impl).

**Why this works for us.**
Domain logic stays agnostic of HTTP, serialization, or remote-specific errors. Remote modules are isolated and swappable. Tests mock the domain interface rather than fighting with Retrofit. Transitive Hilt dependencies are auto-wired because `:app` is the root.

**Known trade-offs / when it strains.**
Every new remote method requires two declarations (interface in `:domain`, impl in `:remote:*`). Mappers add boilerplate. Sharing logic between two remote sources is non-trivial — it would need a `:remote:shared` module or duplication.

**How to apply it.**
```kotlin
// :domain
interface CheapsharkSource {
  suspend fun fetchDeals(params: SearchParameters?): List<Deal>
}

// :remote:cheapshark
class CheapsharkSourceImpl(
  private val dealsApi: DealsApi,
  private val remoteExceptionTransformer: RemoteExceptionTransformer
) : CheapsharkSource {
  override suspend fun fetchDeals(...) = dealsApi.getDeals(...).map { it.toDeal(...) }
}

// Koin binding in :remote:cheapshark/di
val cheapsharkRemoteModule = module {
  single<CheapsharkSource> { CheapsharkSourceImpl(get(), get()) }
}
```

**Seen in.**
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/source/CheapsharkSource.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/source/GamerPowerSource.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

**Related lessons.** L-2026-04-30-01

### Feature Module with Layered Build-Logic Convention

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 core feature modules (home, game, search, store, deal, giveaways)

**The pattern.**
Each feature module applies a shared KMP convention plugin (`alias(libs.plugins.gamedeals.kmp.feature)`) that bakes in the Kotlin Multiplatform library + Compose Multiplatform + KSP setup, plus universal feature-layer dependencies (Koin, Material3, Paging, Coil). Sources are split across `src/commonMain/kotlin/` (shared UI + ViewModels), `src/androidMain/kotlin/` (Android-only adapters), and `src/iosMain/kotlin/` (iOS-only adapters) — the convention plugin wires all three. Module-specific deps stay in each feature's `build.gradle.kts`. Features depend on `:domain`, `:common`, `:common:ui`, `:logging`, and the test harness `:testing`. Cross-feature deps (e.g., `:feature:game` → `:feature:deal`) are declared explicitly when needed.

**Why this works for us.**
A single convention plugin gives every feature module a correct KMP + Compose Multiplatform configuration without each `build.gradle.kts` re-declaring source sets, targets, and compiler args. Shared test stacks mean features are testable in isolation on both Android and iOS. The Paging + Coil defaults match real usage. New features start with everything wired correctly for both platforms.

**Known trade-offs / when it strains.**
Conventions are inflexible: `:feature:webview` deliberately skips the convention because it's Android-only and has no Koin providers, no Paging, and no image loading. Adding a new optional library requires either a convention bump or a new convention plugin. KSP runs for every module even if only some need it. The cost of going wide on KMP is paid up front in build configuration even for features that are mostly common code.

**How to apply it.**
```kotlin
// gradle/libs.versions.toml
[plugins]
gamedeals-kmp-feature = { id = "pm.bam.gamedeals.kmp.feature" }

// feature/home/build.gradle.kts
plugins {
  alias(libs.plugins.gamedeals.kmp.feature)
}
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(project(":domain"))
      implementation(project(":common:ui"))
    }
  }
}
android { namespace = "pm.bam.gamedeals.feature.home" }
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/KotlinMultiplatformFeatureConventionPlugin.kt
- feature/home/build.gradle.kts
- feature/game/build.gradle.kts
- feature/search/build.gradle.kts
- feature/store/build.gradle.kts

**When to deviate.**
`:feature:webview` skips the feature convention because it's Android-only and forcing the KMP convention would add unused iOS source sets, Koin, Paging, and Coil deps. Edge-case modules that diverge sharply from the default profile should opt out and apply only the conventions they need.

### Single-Activity Compose Root with DI Boundary

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** MainActivity + GameDealsApplication (Android) + MainViewController (iOS)

**The pattern.**
On Android, a single `MainActivity` extends plain `ComponentActivity` and its `onCreate` calls `setContent { GameDealsTheme { NavGraph() } }`, delegating all UI to Compose. The `Application` class (`GameDealsApplication`) bootstraps DI by calling `startKoin { modules(...) }` once at process start; feature, domain, and remote modules each export a Koin module that the app aggregates. On iOS, `iosApp/src/iosMain/kotlin/.../MainViewController.kt` mirrors this: it calls a shared `bootstrapKoin()` helper and returns a `ComposeUIViewController` that hosts the same `NavGraph()`. Both platforms therefore share one composition root and one DI graph, differing only in how the host UIKit/Android lifecycle is bridged.

**Why this works for us.**
Single composition root avoids Fragment boilerplate on Android and per-screen `UIViewController`s on iOS — Compose Multiplatform handles all navigation on both sides. `startKoin` is explicit, debuggable, and trivial to mirror on iOS (no `@HiltAndroidApp` magic to replicate). Because the DI graph is declared as plain Kotlin modules in `commonMain`, the same wiring is reused verbatim from `MainViewController`.

**Known trade-offs / when it strains.**
Configuration changes still trigger Activity recreation on Android, demanding state preservation via `SavedStateHandle`. All UI must fit in one NavHost; splitting into multiple activities would require rearchitecting navigation and Koin scoping. The iOS host is hand-written — there's no analogue to `@AndroidEntryPoint`, so adding a new top-level DI binding requires touching both the Android and iOS bootstrap if either references it directly.

**How to apply it.**
```kotlin
// Android
class GameDealsApplication : Application(), SingletonImageLoader.Factory {
  override fun onCreate() {
    super.onCreate()
    startKoin { modules(appModule, domainModule, /* ... */) }
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { GameDealsTheme { NavGraph() } }
  }
}

// iOS — iosApp/src/iosMain/.../MainViewController.kt
fun MainViewController(): UIViewController {
  bootstrapKoin()
  return ComposeUIViewController { GameDealsTheme { NavGraph() } }
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt
- app/src/main/java/pm/bam/gamedeals/MainActivity.kt
- app/src/main/java/pm/bam/gamedeals/di/AppModule.kt
- iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt

### `:common` and `:common:ui` Split

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `:common` and `:common:ui`

**The pattern.**
`:common` holds domain-agnostic utilities (serializers, Clock interface, Flow extensions, Koin qualifiers, immutable-collection wrappers). `:common:ui` holds Compose design-system code (theme, Material3 styling, spacing, PreviewData, shared components like `DealBottomSheet` and `DealDetailsController`). Both are KMP libraries; `:common` has minimal Compose deps, while `:common:ui` carries the full Compose Multiplatform + Coil + Material3 surface. Every feature depends on both; `:domain` depends only on `:common`.

**Why this works for us.**
Separating data utilities (`:common`) from UI utilities (`:common:ui`) lets `:domain` stay free of Compose and Coil. Shared UI components (deal bottom sheet) live in one place and render identically on Android and iOS. The `Clock` abstraction in `:common` keeps domain free of `System.currentTimeMillis()`. Koin qualifiers in `:common` are visible to all modules.

**Known trade-offs / when it strains.**
Distinguishing what goes in `:common` vs `:common:ui` requires judgment. Putting too much in `:common:ui` could break domain decoupling if domain ever needed it. Adding new shared logic mid-project sometimes requires a new `:common:*` submodule.

**How to apply it.**
```kotlin
// common/build.gradle.kts
kotlin {
  sourceSets.commonMain.dependencies {
    api(libs.kotlinx.coroutines)
    implementation(libs.koin.core)
  }
}

// common/ui/build.gradle.kts
kotlin {
  sourceSets.commonMain.dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(compose.material3)
  }
}

// domain/build.gradle.kts
kotlin {
  sourceSets.commonMain.dependencies {
    implementation(project(":common"))   // not :common:ui — domain stays Compose-free
  }
}
```

**Seen in.**
- common/build.gradle.kts
- common/ui/build.gradle.kts
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/navigation/Destination.kt
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt
- common/ui/src/commonMain/kotlin/pm/bam/gamedeals/common/ui/theme/Theme.kt

### Koin ViewModel with Immutable State and Coroutine-Driven Events

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all 6 core feature ViewModels

**The pattern.**
Each feature ViewModel is a plain `ViewModel` subclass declared in `commonMain`, with repositories and a logger taken as constructor parameters. The feature's Koin module registers it via `viewModelOf(::HomeViewModel)` (or `viewModel { HomeViewModel(get(), get()) }`). Composables resolve the ViewModel using `koinViewModel<HomeViewModel>()` from `koin-compose-viewmodel`, which scopes the instance to the current `NavBackStackEntry`. UI state is held in an immutable `StateFlow<ScreenData>` backed by a private `MutableStateFlow`. One-shot events (navigation, snackbars) flow through a `SharedFlow` with `replay = 0` and `extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST`. The ViewModel launches into `viewModelScope`, composing flows with `flatMapLatest`, `combine`, `map`, and `catch`/`onError`.

**Why this works for us.**
Koin's `koinViewModel()` integrates with `androidx.lifecycle.ViewModel` on Android and gives us an equivalent scope on iOS via Compose Multiplatform — the same `commonMain` ViewModel runs on both platforms with no `@HiltViewModel` Android-only annotation. Constructor injection of plain classes makes ViewModels trivially testable: no Hilt test rule, no Robolectric, just `HomeViewModel(fakeRepo, fakeLogger)`. Immutable state prevents accidental mutations and aids debugging. `SharedFlow` for events decouples the ViewModel from lifecycle concerns. `viewModelScope` cancels work onCleared(), preventing leaks.

**Known trade-offs / when it strains.**
Koin resolves dependencies at runtime, so a missing binding shows up as a `NoBeanDefFoundException` at the first `koinViewModel()` call rather than at compile time the way Hilt would have. The `viewModelOf` / `viewModel { ... }` declaration in each feature's Koin module is one extra line of bookkeeping per VM. `SharedFlow` with `DROP_OLDEST` assumes the UI collects synchronously; rapid emissions during recomposition can be lost (mitigated by `replay = 0`). Immutable state with `ImmutableList` adds library noise.

**How to apply it.**
```kotlin
// commonMain — the ViewModel itself
internal class HomeViewModel(
  private val storesRepository: StoresRepository,
  private val logger: Logger
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeScreenData())
  val uiState: StateFlow<HomeScreenData> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<HomeUiEvent>(
    replay = 0, extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()
}

// commonMain — feature DI module
val homeModule = module {
  viewModelOf(::HomeViewModel)
}

// commonMain — Composable consumes it
@Composable
internal fun HomeScreen(vm: HomeViewModel = koinViewModel()) {
  val state by vm.uiState.collectAsStateWithLifecycle()
}
```

**Seen in.**
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt

## What we don't do

- **Circular inter-feature dependencies.** Features depend on `:domain` and `:common`, never on each other directly. The only exceptions (`:feature:game` → `:feature:deal`, `:feature:deal` → `:feature:webview`) are declared explicitly. **Why we avoid it:** circular imports block parallel builds and obscure the dependency graph; Koin module aggregation becomes fragile.

## Decommissioned

### Hilt ViewModel with Immutable State and Coroutine-Driven Events

**Status:** deprecated (Hilt removed; superseded by Koin viewModel pattern)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all 6 core feature ViewModels

**The pattern.**
Each feature ViewModel is `@HiltViewModel`, constructor-injected with repositories and a logger. UI state is held in an immutable `StateFlow<ScreenData>` backed by a private `MutableStateFlow`. One-shot events (navigation, snackbars) flow through a `SharedFlow` with `replay = 0` and `extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST`. The ViewModel launches into `viewModelScope`, composing flows with `flatMapLatest`, `combine`, `map`, and `catch`/`onError`.

**Why this works for us.**
`@HiltViewModel` is standard Jetpack; no custom scoping. Immutable state prevents accidental mutations and aids debugging. `SharedFlow` for events decouples the ViewModel from lifecycle concerns. `viewModelScope` cancels work onCleared(), preventing leaks.

**Known trade-offs / when it strains.**
`SharedFlow` with `DROP_OLDEST` assumes the UI collects synchronously; rapid emissions during recomposition can be lost (mitigated by `replay = 0`). Immutable state with `ImmutableList` adds library noise. Many independent state fields lead to `_field`/`field` pair churn — see ui-state for state hoisting hints.

**How to apply it.**
```kotlin
@HiltViewModel
internal class HomeViewModel @Inject constructor(
  private val storesRepository: StoresRepository,
  private val logger: Logger
) : ViewModel() {

  private val _uiState = MutableStateFlow(HomeScreenData())
  val uiState: StateFlow<HomeScreenData> = _uiState.asStateFlow()

  private val _events = MutableSharedFlow<HomeUiEvent>(
    replay = 0, extraBufferCapacity = 1,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()
}
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
