---
**Path scope:** `app/**`, `*/di/**`, `**/*Module.kt`
**Last surveyed:** 34b01013 on 2026-05-18
---

# Dependency Injection

Koin applied uniformly across the KMP module graph: each library declares one or more `module { … }` blocks in a `di/` package (`commonMain` for shared bindings, `androidMain` / `iosMain` for platform-specific ones). The Android `GameDealsApplication` and the iOS `MainViewController` are twin entry-points that call `startKoin { modules(…) }` with the appropriate per-platform module list. Feature ViewModels are constructor-injected via `viewModel { }` bindings and resolved in composables with `koinViewModel()`. Per-vendor isolation is handled with inline `named()` qualifiers rather than annotation types.

## Patterns

### Koin `module { single { … } }` per Layer

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** every library module that exposes injectable dependencies

**The pattern.**
Every library module that exposes injectable dependencies declares one or more Koin modules in a `di/` package. Each module is a top-level `val <layer>Module = module { … }` containing `single<T> { Impl(get(), get()) }` for singletons and `factory { … }` for per-call instances. Constructor wiring is explicit: dependencies are resolved by `get()`, or `get(named("…"))` for qualified deps. Modules are loaded at `startKoin { modules(...) }` time.

**Why this works for us.**
The per-layer split survives the Hilt → Koin migration unchanged — each library's bindings stay local to that library. No annotation processor or KSP is required for DI. Modules live in `commonMain` so the same bindings serve Android and iOS.

**Known trade-offs / when it strains.**
Bindings are resolved at runtime, not compile time — a missing dependency surfaces at first injection, not at build. Module ordering matters at `startKoin` time when one module's binding references another module's type.

**How to apply it.**
```kotlin
val commonModule = module {
  single<Serializer> { SerializerImpl(get()) }
  single { Json { ignoreUnknownKeys = true } }
  factory { Clock.System }
}

// at app startup
startKoin {
  modules(commonModule, domainModule, loggingModule, remoteModule)
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/di/AppModule.kt
- common/src/commonMain/kotlin/pm/bam/gamedeals/common/di/CommonModule.kt
- domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/di/DomainModule.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/di/LoggingModule.kt
- remote/src/commonMain/kotlin/pm/bam/gamedeals/remote/di/RemoteModule.kt
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/di/RemoteModule.kt

### ViewModel Injection with Koin `viewModel { }` + `koinViewModel()`

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all feature modules

**The pattern.**
Feature ViewModels are registered in their feature's Koin module via `viewModel { HomeViewModel(get(), get(), …) }`. Composable screens resolve the VM with `koinViewModel()` from `koin-compose-viewmodel`. Constructor injection shape is preserved — the VM's constructor signature is the contract.

**Why this works for us.**
KMP-friendly: no Android-specific `@HiltViewModel` annotation, so ViewModels can live in `commonMain` when their dependencies are platform-agnostic. Composable-first resolution keeps the wiring local to the screen.

**Known trade-offs / when it strains.**
Extra registration boilerplate per feature compared to Hilt's annotation-driven discovery — every new ViewModel requires a line in the feature's Koin module.

**How to apply it.**
```kotlin
val homeModule = module {
  viewModel { HomeViewModel(get(), get(), get()) }
}

@Composable
fun HomeScreen() {
  val vm: HomeViewModel = koinViewModel()
  // ...
}
```

**Seen in.**
- feature/home/src/commonMain/kotlin/pm/bam/gamedeals/feature/home/di/HomeModule.kt
- feature/store/src/commonMain/kotlin/pm/bam/gamedeals/feature/store/di/StoreModule.kt
- feature/game/src/commonMain/kotlin/pm/bam/gamedeals/feature/game/di/GameModule.kt
- feature/search/src/commonMain/kotlin/pm/bam/gamedeals/feature/search/di/SearchModule.kt
- feature/giveaways/src/commonMain/kotlin/pm/bam/gamedeals/feature/giveaways/di/GiveawaysModule.kt

### Per-Vendor Isolation via Koin `named()` Qualifiers

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** all remote network clients (CheapShark, GamerPower)

**The pattern.**
Each remote vendor exports a `named()` qualifier as a top-level `val` — e.g., `val CHEAPSHARK_QUALIFIER = named("cheapshark")`, `val GAMERPOWER_QUALIFIER = named("gamerpower")`. The `HttpClient` and other vendor-scoped singletons are bound under the qualifier; vendor-side consumers resolve via `get(CHEAPSHARK_QUALIFIER)`. Unqualified terminal consumers (API interfaces exposed outward) don't need to know.

**Why this works for us.**
Two `HttpClient` configurations coexist in the same Koin graph without binding collision. The qualifier is enforced explicitly per binding rather than via an annotation type, which keeps the dependency tree easier to grep.

**Known trade-offs / when it strains.**
Every intermediate vendor dep must pass the qualifier through — easy to forget. Mitigated by per-vendor module conventions (one network module per vendor, qualifier declared at the top of the file).

**How to apply it.**
```kotlin
val CHEAPSHARK_QUALIFIER = named("cheapshark")

val cheapsharkRemoteModule = module {
  single(CHEAPSHARK_QUALIFIER) {
    HttpClient { /* CheapShark config */ }
  }
  single {
    DealsApi(client = get(CHEAPSHARK_QUALIFIER))
  }
}
```

**Seen in.**
- remote/cheapshark/src/commonMain/kotlin/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt
- remote/gamerpower/src/commonMain/kotlin/pm/bam/gamedeals/remote/gamerpower/di/RemoteNetworkModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestNetworkOverridesModule.kt

### Application Bootstrap with `startKoin { modules(…) }` (Android + iOS twin)

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** `GameDealsApplication` (Android), `MainViewController` (iOS), `TestGameDealsApplication` (androidTest)

**The pattern.**
Android `GameDealsApplication.onCreate()` calls `startKoin { androidContext(this@GameDealsApplication); modules(commonModule, domainModule, …) }`. iOS `MainViewController.bootstrapKoin()` calls the equivalent — no `androidContext`, iOS-specific modules layered in instead. Tests use a `TestGameDealsApplication` that loads production modules followed by test override modules (last-load-wins; see `testing.md`).

**Why this works for us.**
Explicit single entry point per platform makes the wiring legible — the module list is right there in the bootstrap call. The iOS twin mirrors Android's startup shape so behaviour matches across platforms. The test variant is a clean override seam without per-test reloads.

**Known trade-offs / when it strains.**
The module list is hand-maintained; missing a module surfaces only when an unresolved binding throws at first injection.

**How to apply it.**
```kotlin
// Android
class GameDealsApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@GameDealsApplication)
      modules(commonModule, commonAndroidModule, domainModule, loggingAndroidModule, /* … */)
    }
  }
}

// iOS
fun bootstrapKoin() {
  startKoin {
    modules(commonModule, commonIosModule, domainModule, domainIosModule, /* … */)
  }
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/GameDealsApplication.kt
- iosApp/src/iosMain/kotlin/pm/bam/gamedeals/iosApp/MainViewController.kt
- app/src/androidTest/java/pm/bam/gamedeals/TestGameDealsApplication.kt

**Related lessons.** L-2026-05-04-05

### Platform-Specific Koin Modules per Layer

**Status:** established
**First documented:** 2026-05-18   **Last verified:** 2026-05-18 @ 34b01013
**Coverage:** common, logging, domain, remote (any layer with platform-specific bindings)

**The pattern.**
Layers that need platform-specific bindings (storage, logging, image-loading, theme) ship a `commonMain` module plus a per-platform companion (`commonIosModule`, `loggingAndroidModule`, etc.). Names follow `<Layer>{Android|Ios}Module`. The platform modules are layered into `startKoin` only on their respective platform; `commonMain` carries the platform-agnostic bindings.

**Why this works for us.**
Keeps platform-specific bindings out of `commonMain`, where Android/iOS types can't be referenced. The naming convention makes wiring obvious at the bootstrap call site — read down the module list and the platform split is visible.

**Known trade-offs / when it strains.**
More module files per layer (2 or 3 instead of 1). The convention has to be maintained per layer, and a new layer that needs platform bindings has to follow the same naming or grep stops working.

**How to apply it.**
```kotlin
// common/src/commonMain/.../CommonModule.kt
val commonModule = module {
  single<Serializer> { SerializerImpl(get()) }
}

// common/src/androidMain/.../CommonAndroidModule.kt
val commonAndroidModule = module {
  single<FileStorage> { AndroidFileStorage(androidContext()) }
}

// common/src/iosMain/.../CommonIosModule.kt
val commonIosModule = module {
  single<FileStorage> { IosFileStorage() }
}
```

**Seen in.**
- common/src/androidMain/kotlin/pm/bam/gamedeals/common/di/CommonAndroidModule.kt
- common/src/iosMain/kotlin/pm/bam/gamedeals/common/di/CommonIosModule.kt
- logging/src/androidMain/kotlin/pm/bam/gamedeals/logging/di/LoggingModule.kt
- logging/src/iosMain/kotlin/pm/bam/gamedeals/logging/di/LoggingIosModule.kt
- domain/src/iosMain/kotlin/pm/bam/gamedeals/domain/di/DomainIosModule.kt

**Related lessons.** L-2026-05-04-03

## What we don't do

- **No compile-time DI graph validation.** Koin resolves at runtime; missing bindings throw at first injection. Mitigated by smoke tests on app launch and the test override pattern in `testing.md`, but the failure mode is fundamentally runtime rather than build-time.
- **No `@Binds`-style interface-to-impl bindings.** Koin's `single<T> { Impl(...) }` is the universal form — the interface type is the binding key, the lambda is the factory. There is no separate "bind interface to impl" construct.
- **No per-test `loadKoinModules` / `unloadKoinModules` reloads.** Test modules are layered at app startup via `TestGameDealsApplication` (production modules first, then test overrides — last-load-wins). Covered in detail in `testing.md`.
- **No circular Koin module dependencies.** Unlike Hilt's transitive graph discovery, Koin needs explicit ordering at `startKoin(modules = …)`. If two modules each depend on a binding the other provides, the bootstrap call will fail; we keep the dependency direction flowing one way (common → domain → remote / feature).

## Decommissioned

### Singleton-Component Modules per Layer

**Status:** deprecated (Hilt removed 2026-05-17; superseded by "Koin `module { single { … } }` per Layer")
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** every library module that exposes injectable dependencies (8 modules)

**The pattern.**
Every library module that exposes injectable dependencies has a `di/` package with a single `@Module` class annotated `@InstallIn(SingletonComponent::class)`. The composition root is `:app`. Modules use `@Provides` for factories and stateless constructors — never `@Binds`.

**Why this works for us.**
`SingletonComponent`-only scoping keeps the lifecycle model simple — no `ViewModelComponent`, `ActivityComponent`, or `FragmentComponent` rules. Every upstream dependency is application-lifetime.

**Known trade-offs / when it strains.**
Feature modules don't declare DI modules; their dependencies are constructor-injected into `@HiltViewModel`. If a feature ever needed feature-scoped state (e.g., a feature-scoped cache), it would need its own DI module with a custom scope, breaking the pattern.

**How to apply it.**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
class CommonModule {
  @Provides
  @Singleton
  fun provideSerializer(json: Json): Serializer = SerializerImpl(json)
}
```

**Seen in.**
- app/src/main/java/pm/bam/gamedeals/di/AppModule.kt
- common/src/main/java/pm/bam/gamedeals/common/di/CommonModule.kt
- domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt
- logging/src/main/java/pm/bam/gamedeals/logging/di/LoggingModule.kt
- remote/src/main/java/pm/bam/gamedeals/remote/di/RemoteModule.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/di/RemoteModule.kt

### Per-Vendor `@Qualifier` for Third-Party Isolation

**Status:** deprecated (Hilt `@Qualifier` annotations gone; superseded by "Per-Vendor Isolation via Koin `named()` Qualifiers")
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all network clients (`@CheapShark`, `@GamerPower`); also `@Settings`, `@Domain`

**The pattern.**
Each remote HTTP client declares a module-scoped `@Qualifier` annotation (`@CheapShark`, `@GamerPower`). `OkHttpClient` and `Retrofit` instances are qualified with the client's annotation, then consumed by unqualified API interfaces. Upstream modules use `@Settings` and `@Domain` qualifiers for their own internal wiring.

**Why this works for us.**
Two competing Retrofit/OkHttp configurations coexist in the same `SingletonComponent` without collision. The qualifier enforces that the CheapShark stack only assembles CheapShark APIs, and GamerPower's only assembles GamerPower APIs.

**Known trade-offs / when it strains.**
Requires discipline: every intermediate dependency (OkHttp, Retrofit) must be qualified; only terminal consumers (API interfaces) can be unqualified. If a developer forgets the qualifier on a Retrofit binding, the wrong client could be injected silently. Mitigated by naming convention and review.

**How to apply it.**
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CheapShark

@Module
@InstallIn(SingletonComponent::class)
class RemoteNetworkModule {
  @Provides @Singleton @CheapShark
  fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder().build()

  @Provides @Singleton @CheapShark
  fun provideRetrofit(@CheapShark client: OkHttpClient): Retrofit =
    Retrofit.Builder().client(client).build()

  @Provides @Singleton
  fun provideDealsApi(@CheapShark retrofit: Retrofit): DealsApi =
    retrofit.create(DealsApi::class.java)
}
```

**Seen in.**
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt
- remote/gamerpower/src/main/java/pm/bam/gamedeals/remote/gamerpower/di/RemoteNetworkModule.kt
- common/src/main/java/pm/bam/gamedeals/common/di/CommonModule.kt
- domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt

### `@HiltViewModel` Constructor Injection (No Feature DI Modules)

**Status:** deprecated (superseded by "ViewModel Injection with Koin `viewModel { }` + `koinViewModel()`")
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all 6 feature modules

**The pattern.**
Feature modules never declare DI modules. All ViewModels are annotated `@HiltViewModel` and receive dependencies via constructor injection. Features purely consume the singleton graph assembled by upstream modules.

**Why this works for us.**
Features stay stateless and testable: thin presentation shells with no ownership of DI policy. Dependencies are explicit in the constructor signature. The convention plugin (`AndroidFeatureConventionPlugin`) wires Hilt + KSP universally, so all features follow the same pattern.

**Known trade-offs / when it strains.**
Feature-scoped dependencies are difficult: a feature with complex internal state (e.g., a wizard) would need its own DI module + custom scope. Long constructor lists (6+ params) signal a feature is doing too much.

**How to apply it.**
```kotlin
@HiltViewModel
internal class HomeViewModel @Inject constructor(
  private val storesRepository: StoresRepository,
  private val dealsRepository: DealsRepository,
  private val logger: Logger
) : ViewModel() { /* ... */ }
```

**Seen in.**
- feature/home/src/main/java/pm/bam/gamedeals/feature/home/ui/HomeViewModel.kt
- feature/deal/src/main/java/pm/bam/gamedeals/feature/deal/ui/DealDetailsViewModel.kt
- feature/game/src/main/java/pm/bam/gamedeals/feature/game/ui/GameViewModel.kt
- feature/search/src/main/java/pm/bam/gamedeals/feature/search/ui/SearchViewModel.kt
- feature/store/src/main/java/pm/bam/gamedeals/feature/store/ui/StoreViewModel.kt
- feature/giveaways/src/main/java/pm/bam/gamedeals/feature/giveaways/ui/GiveawaysViewModel.kt

### Module Composition via `@Module(includes = […])`

**Status:** deprecated (Hilt-specific construct; Koin module composition is implicit via `modules(...)` list at `startKoin` time. No replacement entry — composition is trivial in Koin)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** Domain, Remote (CheapShark) modules

**The pattern.**
Larger modules use `@Module(includes = [...])` to logically group `@Provides` methods into separate classes. `DomainModule` includes `InternalDomainModule` and `DatabaseModule`; `RemoteModule` (CheapShark) includes `RemoteNetworkModule`. Included modules are `internal` to signal they are implementation details.

**Why this works for us.**
Keeps a single large `@Module` file from becoming unmanageable. `internal` visibility marks the included modules as not part of the public DI surface. Modules can be split or rearranged without changing dependents.

**Known trade-offs / when it strains.**
`includes` creates an implicit dependency order — if a transitive dependency is missed, compilation fails with an opaque error message. IDE support is limited; unused includes aren't flagged.

**How to apply it.**
```kotlin
@Module(includes = [InternalDomainModule::class, DatabaseModule::class])
@InstallIn(SingletonComponent::class)
class DomainModule { /* public bindings */ }

@Module
@InstallIn(SingletonComponent::class)
internal class DatabaseModule {
  @Provides
  @Singleton
  fun provideDatabase(...): DomainDatabase = ...
}
```

**Seen in.**
- domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt
- remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteModule.kt

### Test Module Replacement in `androidTest`

**Status:** deprecated (Hilt test-module override removed; superseded by Koin last-load-wins pattern documented in `testing.md`)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** app androidTest (TestDatabaseModule, TestCheapSharkNetworkModule, TestGamerPowerNetworkModule)

**The pattern.**
Test modules in `app/src/androidTest/java/.../di/` override production bindings for instrumented tests. `TestDatabaseModule` provides an in-memory Room database; `TestCheapSharkNetworkModule` and `TestGamerPowerNetworkModule` provide `MockWebServer` instances. All are `@InstallIn(SingletonComponent::class)` with the same scope as production, so Hilt automatically replaces them during test compilation. A custom `HiltTestRunner` (extending `AndroidJUnitRunner`) instantiates `HiltTestApplication`.

**Why this works for us.**
Allows full end-to-end integration tests without hitting real APIs or persisting test data. `MockWebServer` is configured with `FixtureMockDispatcher` to return pre-recorded responses.

**Known trade-offs / when it strains.**
Test modules must redefine all the bindings they override; there's no partial override. If a binding is missing, the production binding sneaks in silently. Complex fixtures (MockWebServer, in-memory schemas) duplicate production setup.

**How to apply it.**
```kotlin
@Module
@InstallIn(SingletonComponent::class)
class TestDatabaseModule {
  @Provides
  @Singleton
  fun provideInMemoryDatabase(@ApplicationContext ctx: Context): DomainDatabase =
    Room.inMemoryDatabaseBuilder(ctx, DomainDatabase::class.java)
      .allowMainThreadQueries()
      .build()
}
```

**Seen in.**
- app/src/androidTest/java/pm/bam/gamedeals/di/TestDatabaseModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestCheapSharkNetworkModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/di/TestGamerPowerNetworkModule.kt
- app/src/androidTest/java/pm/bam/gamedeals/HiltTestRunner.kt

### Convention Plugin Auto-Wires Hilt to Feature Modules

**Status:** deprecated (superseded by `KotlinMultiplatformFeatureConventionPlugin` which auto-wires Koin — see `build.md`)
**First documented:** 2026-05-03   **Last verified:** 2026-05-03 @ 31a89bc
**Coverage:** all 6 core feature modules

**The pattern.**
`AndroidFeatureConventionPlugin` (in `build-logic/convention`) auto-applies `hilt-android` + `hilt-compiler` + `hilt-androidx-compiler` to every feature module, alongside Compose, Material3, Paging, and Coil. Feature modules don't explicitly declare Hilt in their `build.gradle.kts`; they apply the convention.

**Why this works for us.**
Eliminates boilerplate and ensures all features wire Hilt the same way. Reduces the chance of a feature accidentally forgetting Hilt or pinning an old Compose version.

**Known trade-offs / when it strains.**
Conventions hide dependencies — a developer reading a feature's `build.gradle.kts` may not see Hilt. Modules that deliberately don't use Hilt (e.g., `:feature:webview`) must opt out and apply only what they need. Updating the convention touches all features implicitly.

**How to apply it.**
```kotlin
class AndroidFeatureConventionPlugin : Plugin<Project> {
  override fun apply(target: Project): Unit = with(target) {
    pluginManager.apply("pm.bam.gamedeals.android.library")
    pluginManager.apply("pm.bam.gamedeals.android.library.compose")
    pluginManager.apply("pm.bam.gamedeals.android.ksp")

    val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies.apply {
      add("implementation", libs.findLibrary("hilt-android").get())
      add("ksp", libs.findLibrary("hilt-compiler").get())
      add("ksp", libs.findLibrary("hilt-androidx-compiler").get())
      // ... Compose, Paging, Coil, etc.
    }
  }
}
```

**Seen in.**
- build-logic/convention/src/main/kotlin/pm/bam/gamedeals/AndroidFeatureConventionPlugin.kt
