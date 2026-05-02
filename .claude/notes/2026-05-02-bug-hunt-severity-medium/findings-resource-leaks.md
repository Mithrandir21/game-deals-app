# Findings — Resource Leaks

Hunter: `android-bug-hunting-resource-leaks`
Date: 2026-05-02
Branch HEAD: `wave/2026-05-02-bug-hunt/issue-75-77-giveaways-refresh-outcome`

## Scope summary

Searched for: `Cursor`, `rawQuery`, `compileStatement`, `beginTransaction`, raw SQLite, `InputStream`/`OutputStream`/`Reader`/`Writer`, `RandomAccessFile`/`FileChannel`, `ZipFile`/`JarFile`, `Socket`/`ServerSocket`/`DatagramSocket`, OkHttp `Response`/`execute()`, `TypedArray.obtainStyledAttributes`, `MediaPlayer`/`MediaRecorder`/`MediaCodec`, `Camera`/`ImageReader`/`ImageProxy`, `ContentProviderClient`/`ContentObserver`, `ParcelFileDescriptor`/`AssetFileDescriptor`, `BroadcastReceiver`/`registerReceiver`, `PowerManager`/`WakeLock`, `SensorManager`, `LocationManager`, `Surface`/`SurfaceTexture`, `HttpURLConnection`, `coil` `Disposable`/`ImageRequest.execute`, Compose `DisposableEffect`/`onDispose`/`onRelease`.

The codebase is unusually clean for resource leaks. Networking is Retrofit-suspending `ApiResponse<T>` via Sandwich (closes responses internally), all DB access goes through Room DAOs (no raw `Cursor`s, no `compileStatement`, no manual `beginTransaction` — `DealsMediator` uses `withTransaction` correctly), Coil drives all image loading via `AsyncImage` (auto-disposes), and there is no production stream / file / socket / native-resource code. The `WebView` (`feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt`) was recently fixed to call `destroy()` on `onRelease` (PR #30 / #67) and is now correct.

One concrete finding below — a thread-pooled callback Executor handed to Room with no shutdown path. Bounded (singleton-scoped) but unnecessary outside debug builds.

---

### BUG-001: Room `setQueryCallback` Executor never shut down and runs in release builds

| Field | Value |
|---|---|
| **Severity** | Low |
| **Category** | Resource leak (thread / native handle), unconditional in release builds |
| **Location** | `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:55-57` |
| **Effort** | Trivial |
| **Confidence** | Medium |

**Description.** `DatabaseModule.provideDatabase` registers a SQL query callback with a `Executors.newSingleThreadExecutor()` instantiated inline. Two issues: (1) the executor is owned by no one — the worker thread lives for the process lifetime; Room does not stop user-supplied callback executors when the DB is closed; (2) the callback is wired unconditionally, not gated on `BuildConfig.DEBUG` or `RemoteBuildType`, so the lambda + `verbose(logger)` dispatch runs in release builds for every query.

**Impact.** Bounded, single-process. One always-on worker thread + one log-message allocation per query in release builds. Not user-visible; matters for sustained background battery / memory accounting and for keeping the production process clean.

**Evidence.**
```kotlin
// domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:43-58
@Provides
@Singleton
fun provideDatabase(
    @ApplicationContext context: Context,
    logger: Logger,
    @Domain storeImagesConverter: StoreImagesConverter,
    @Domain giveawayPlatformsConverter: GiveawayPlatformsConverter,
    @Domain localDatetimeConverter: LocalDatetimeConverter
): DomainDatabase =
    Room.databaseBuilder(context, DomainDatabase::class.java, "${DomainDatabase::class.java.simpleName}.db")
        .fallbackToDestructiveMigration()
        .addTypeConverter(storeImagesConverter)
        .addTypeConverter(giveawayPlatformsConverter)
        .addTypeConverter(localDatetimeConverter)
        .setQueryCallback({ sqlQuery, bindArgs ->
            verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" }
        }, Executors.newSingleThreadExecutor())
        .build()
```

**Recommended fix.** Gate the callback on a debug build flag (matching the pattern already used for OkHttp's `HttpLoggingInterceptor` in `remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/di/RemoteNetworkModule.kt:33-37`):

```kotlin
val builder = Room.databaseBuilder(...)
    .fallbackToDestructiveMigration()
    .addTypeConverter(...)
when (remoteBuildUtil.buildType) {
    RemoteBuildType.DEBUG -> builder.setQueryCallback(
        { sqlQuery, bindArgs ->
            verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" }
        },
        Executors.newSingleThreadExecutor()
    )
    else -> Unit
}
return builder.build()
```

**Confidence rationale.** Medium because (1) the executor is genuinely never released and the leak source is unambiguous; (2) impact is small — one thread per process, app-lifetime, hence Low severity by the rubric. Marked Medium rather than High because the unconditional callback may be intentional (breadcrumb collection) and the maintainer might prefer to keep it on in release.

---

## Notes for the dispatcher

- `WebView.destroy()` is now in place (`feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt:120-127`); not flagged.
- `GiveawaysViewModel.reloadGiveaways` / `loadGiveaway`, `GameViewModel.reloadGameDetails` launch a new `viewModelScope` collector on each call without cancelling the prior one — coroutine hotspots, not closeable-resource leaks. Out of scope here; worth ensuring the coroutine hunter caught them.
- No `Cursor`, raw SQLite, file-stream, socket, `ParcelFileDescriptor`, `MediaPlayer`, `Camera`, `BroadcastReceiver`, `ContentObserver`, or `TypedArray` usage in production code. Test code (`app/src/androidTest/.../FixtureMockDispatcher.kt`, `HomeToStoreToDealJourneyTest.kt`) opens streams but tests are out of scope.
