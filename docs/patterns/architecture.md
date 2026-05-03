---
**Path scope:** `app/**`, `base/**`, `common/**`, `domain/**`, `feature/**`, `logging/**`, `remote/**`, `settings.gradle.kts`, `build-logic/**`, `*/build.gradle.kts`, `MODULES.md`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Architecture

This codebase practices structured multi-module architecture with layered separation (domain-first, remote adapters, feature isolation) and type-safe navigation, enabling independent feature development and clean dependency boundaries.

## Patterns

### Type-Safe Navigation with Serializable Destinations

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
- common/src/main/java/pm/bam/gamedeals/common/navigation/Destination.kt
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/navigation/HomeNavigation.kt
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/navigation/GameNavigation.kt

### Port/Adapter Pattern for Remote Data Sources

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
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
class CheapsharkSourceImpl @Inject constructor(
  private val dealsApi: DealsApi,
  private val remoteExceptionTransformer: RemoteExceptionTransformer
) : CheapsharkSource {
  override suspend fun fetchDeals(...) = dealsApi.getDeals(...).map { it.toDeal(...) }
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/source/CheapsharkSource.kt
- domain/src/main/java/pm/bam/gamedeals/domain/source/GamerPowerSource.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/CheapsharkSourceImpl.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/GamerPowerSourceImpl.kt

### Feature Module with Layered Build-Logic Convention

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all 6 core feature modules (home, game, search, store, deal, giveaways)

**The pattern.**
Each feature module applies a shared convention plugin (`alias(libs.plugins.gamedeals.android.feature)`) that bakes in library + Compose + KSP, plus universal feature-layer dependencies (Hilt, Material3, Paging, Coil). Module-specific deps stay in each feature's `build.gradle.kts`. Features depend on `:domain`, `:common`, `:common:ui`, `:logging`, and the test harness `:testing`. Cross-feature deps (e.g., `:feature:game` → `:feature:deal`) are declared explicitly when needed.

**Why this works for us.**
Convention plugin eliminates boilerplate and enforces consistency. Shared test stacks mean features are testable in isolation. The Paging + Coil defaults match real usage. New features start with everything wired correctly.

**Known trade-offs / when it strains.**
Conventions are inflexible: `:feature:webview` deliberately skips the convention because it has no Hilt providers, no Paging, and no image loading. Adding a new optional library requires either a convention bump or a new convention plugin. KSP runs for every module even if only some need it.

**How to apply it.**
```kotlin
// gradle/libs.versions.toml
[plugins]
gamedeals-android-feature = { id = "pm.bam.gamedeals.android.feature" }

// feature/home/build.gradle.kts
plugins {
  alias(libs.plugins.gamedeals.android.feature)
}
android { namespace = "pm.bam.gamedeals.feature.home" }
dependencies {
  implementation(project(":domain"))
  implementation(project(":common:ui"))
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidFeatureConventionPlugin.kt
- feature/home/build.gradle.kts
- feature/game/build.gradle.kts
- feature/search/build.gradle.kts
- feature/store/build.gradle.kts

**When to deviate.**
`:feature:webview` skips the feature convention because forcing it would add unused Hilt/Paging/Coil deps. Edge-case modules that diverge sharply from the default profile should opt out and apply only the conventions they need.

### Single-Activity Compose Root with DI Boundary

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** MainActivity + GameDealsApplication + AppModule

**The pattern.**
A single `MainActivity` extends `LoggingBaseActivity` (in `:base`), annotated `@AndroidEntryPoint`. `onCreate` calls `setContent { GameDealsTheme { NavGraph() } }`, delegating all UI to Compose. The Application class (`GameDealsApplication`) is `@HiltAndroidApp`, marking the Hilt singleton container root. App-level wiring lives in `:app:di:AppModule` (Firebase, Clock, Coil); domain and remote modules register their own `@Module`s, discovered transitively via Hilt.

**Why this works for us.**
Single-activity avoids Fragment boilerplate and multi-lifecycle complexity. Compose handles all navigation. Hilt's `@HiltAndroidApp` generates the Dagger graph at build time; all modules' DI contributions are discovered and linked automatically.

**Known trade-offs / when it strains.**
Configuration changes trigger Activity recreation, demanding state preservation via `SavedStateHandle`. All UI must fit in one NavHost; breaking into multiple activities would require rearchitecting navigation and Hilt scoping.

**How to apply it.**
```kotlin
@HiltAndroidApp
class GameDealsApplication : Application(), ImageLoaderFactory {
  @Inject lateinit var imageLoader: ImageLoader
  override fun newImageLoader() = imageLoader
}

@AndroidEntryPoint
class MainActivity : LoggingBaseActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { GameDealsTheme { NavGraph() } }
  }
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt
- app/src/main/java/pm/bam/gamedeals/MainActivity.kt
- app/src/main/java/pm/bam/gamedeals/di/AppModule.kt
- base/src/main/java/pm/bam/gamedeals/base/LoggingBaseActivity.kt

### `:common` and `:common:ui` Split

**Status:** established
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** `:common` and `:common:ui`

**The pattern.**
`:common` holds domain-agnostic utilities (serializers, Clock interface, Flow extensions, Hilt qualifiers, immutable-collection wrappers). `:common:ui` holds Compose design-system code (theme, Material3 styling, spacing, PreviewData, shared components like `DealBottomSheet` and `DealDetailsController`). Both are libraries; `:common` has minimal Compose deps, while `:common:ui` carries the full Compose + Coil + Material3 surface. Every feature depends on both; `:domain` depends only on `:common`.

**Why this works for us.**
Separating data utilities (`:common`) from UI utilities (`:common:ui`) lets `:domain` stay free of Compose and Coil. Shared UI components (deal bottom sheet) live in one place. The `Clock` abstraction in `:common` keeps domain free of `System.currentTimeMillis()`. Hilt qualifiers in `:common` are visible to all modules.

**Known trade-offs / when it strains.**
Distinguishing what goes in `:common` vs `:common:ui` requires judgment. Putting too much in `:common:ui` could break domain decoupling if domain ever needed it. Adding new shared logic mid-project sometimes requires a new `:common:*` submodule.

**How to apply it.**
```kotlin
// common/build.gradle.kts
dependencies {
  api(libs.kotlinx.coroutines)
  implementation(libs.hilt.android)
}

// common/ui/build.gradle.kts
dependencies {
  implementation(project(":common"))
  implementation(project(":domain"))
  implementation(libs.androidx.compose.material3)
}

// domain/build.gradle.kts
dependencies {
  implementation(project(":common"))   // not :common:ui — domain stays Compose-free
}
```

**Seen in.**
- common/build.gradle.kts
- common/ui/build.gradle.kts
- common/src/main/java/pm/bam/gamedeals/common/navigation/Destination.kt
- common/ui/src/main/java/pm/bam/gamedeals/common/ui/deal/DealBottomSheet.kt
- common/ui/src/main/java/pm/bam/gamedeals/common/ui/theme/Theme.kt

### Hilt ViewModel with Immutable State and Coroutine-Driven Events

**Status:** established
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

## What we don't do

- **Circular inter-feature dependencies.** Features depend on `:domain` and `:common`, never on each other directly. The only exceptions (`:feature:game` → `:feature:deal`, `:feature:deal` → `:feature:webview`) are declared explicitly. **Why we avoid it:** circular imports block parallel builds and obscure the dependency graph; Hilt module discovery becomes fragile.
