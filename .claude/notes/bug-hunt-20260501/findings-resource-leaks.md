# Resource Leak Findings — 2026-05-01

**No defects identified.**

## Summary by severity

- Critical: 0
- High: 0
- Medium: 0
- Low: 0

## Top findings

None — all relevant detectors came up clean.

## Methodology and detector results

I scanned `*/src/main/**/*.kt` across all 17 modules (`:app`, `:base`, `:common`, `:common:ui`, `:logging`, `:testing`, `:remote`, `:remote:gamerpower`, `:remote:cheapshark`, `:domain`, `:feature:store`, `:feature:deal`, `:feature:game`, `:feature:search`, `:feature:home`, `:feature:webview`, `:feature:giveaways`), excluding `.claude/worktrees/**` and `build/`.

| ID  | Detector                                                       | Hits | Notes |
|-----|----------------------------------------------------------------|------|-------|
| D1  | `Cursor` not in `use {}` / `try-finally`                       | 0    | No `query()` / `rawQuery()` / raw `Cursor` usage in production code; all DB access goes through Room DAOs returning `Flow`/`PagingSource`/`List`. |
| D2  | Streams / Readers / Writers not closed                         | 0    | No `FileInputStream`, `FileOutputStream`, `BufferedReader`, `BufferedWriter`, `InputStreamReader`, `OutputStreamWriter`, `FileReader`, `FileWriter`, `RandomAccessFile`, `FileChannel`, or `java.io.File` instances in `src/main`. |
| D3  | OkHttp `Response` body not closed                              | 0    | No `client.newCall(...).execute()`, `byteStream()`, `charStream()`, `.source()`, or raw `Response`/`ResponseBody` consumption. All HTTP traffic flows through Retrofit `suspend` functions returning Sandwich `ApiResponse<T>` (e.g. `remote/cheapshark/src/main/java/pm/bam/gamedeals/remote/cheapshark/api/DealsApi.kt`), which closes response bodies internally. The `Response` matches in `remote/**/api/*.kt` are `ApiResponse<...>` wrappers; the match in `ErrorResponse.kt` is a data-class name. |
| D4  | `TypedArray.recycle()` missing                                 | 0    | No `obtainStyledAttributes` calls. The `toTypedArray()` matches in `domain/repositories/**` are Kotlin's `Collection.toTypedArray()`, not Android `TypedArray`. |
| D5  | `ContentProviderClient` not released                           | 0    | No `acquireContentProviderClient` calls. |
| D6  | Bitmap not recycled                                            | 0    | No `BitmapFactory.decode*`, `Bitmap.createBitmap`, or `recycle()` calls. Image loading is delegated to Coil (configured via `GameDealsApplication: ImageLoaderFactory`). |
| D7  | `MediaPlayer`/`MediaRecorder`/`MediaCodec` not released        | 0    | None used. |
| D8  | `ImageProxy` / `ImageReader` not closed                        | 0    | No CameraX usage. |
| D9  | `SQLiteDatabase` / `SQLiteStatement` not closed                | 0    | No raw SQLite APIs; Room exclusively. |
| D10 | `ParcelFileDescriptor` / `AssetFileDescriptor` not closed      | 0    | None used. |
| D11 | `ZipFile` / `JarFile` not closed                               | 0    | None used. |
| D12 | `Socket` / `ServerSocket` / `DatagramSocket` not closed        | 0    | None used. |
| D13 | DB transaction without commit/rollback on every path           | 0    | The two transaction call sites — `domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/DealsRepository.kt:78` and `domain/src/main/java/pm/bam/gamedeals/domain/repositories/deals/paging/DealsMediator.kt:60` — both use the AndroidX Room `withTransaction { ... }` extension, which guarantees cleanup. No `beginTransaction()` calls. |
| D14 | `BroadcastReceiver` registered without unregister              | 0    | No `registerReceiver` / `registerNetworkCallback` / `addContentObserver` calls. |
| D15 | `Bitmap.Config.HARDWARE` misuse                                | 0    | Not used. |
| WebView | `WebView` not destroyed                                    | 0    | `feature/webview/src/main/java/pm/bam/gamedeals/feature/webview/ui/WebView.kt:120-127` calls `stopLoading()`, clears the client, loads `about:blank`, removes from parent, and calls `destroy()` in `AndroidView.onRelease`. No `WebMessage*` usage. |

## Confidence rationale

High. The codebase deliberately works at a high level of abstraction (Retrofit + Sandwich for HTTP, Room for persistence, Coil for images, SharedPreferences for settings, Compose `AndroidView` for the lone WebView). There are no raw `Closeable`/`AutoCloseable` usages in `src/main` (zero hits for `.close(`, `.use {`, `Closeable`, `AutoCloseable`, `Disposable`), so there is nothing for a `.use {}` audit to attach to.
