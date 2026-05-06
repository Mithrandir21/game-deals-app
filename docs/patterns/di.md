---
**Path scope:** `app/**`, `*/di/**`, `**/*Module.kt`, `**/*Qualifier.kt`
**Last surveyed:** 31a89bc on 2026-05-03
---

# Dependency Injection

A cleanly modularized multi-module codebase with Hilt applied uniformly: `@HiltAndroidApp` in the Application, DI modules in each library (common, domain, logging, remote and its sub-modules), `@HiltViewModel` in every feature, and bespoke qualifiers for third-party isolation.

## Patterns

### Singleton-Component Modules per Layer

**Status:** established
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

**Status:** established
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

**Status:** established
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

**Status:** established
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

**Status:** established
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

**Status:** established
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

## What we don't do

- **No `@Binds` for interface-to-impl bindings.** All bindings use `@Provides` factories. **Why we avoid it:** every binding is then visibly present in the module body — no implicit graph from interface declarations. The trade-off is that we lose some compile-time safety (Hilt can't catch missing impls as quickly), but the failure mode is still an obvious compile error.
- **No `@Multibinds` / `@IntoMap` / `@IntoSet`.** No dynamic binding collections. **Why we avoid it:** there's no plugin architecture or multimap of implementations needed today.
- **No `ViewModelComponent` or `ActivityComponent` scoping.** Everything is `SingletonComponent`. **Why we avoid it:** simpler mental model; no current need for feature-scoped state.
- **No `@Lazy` or `Provider<T>` for late binding.** All dependencies are eagerly injected at construction. Retry / lazy logic lives in the repository or ViewModel.
