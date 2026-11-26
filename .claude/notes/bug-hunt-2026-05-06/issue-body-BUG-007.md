> Note: Closed #42 already made the `Storage` interface suspending; the only caller of this backend (`StorageImpl`) wraps in `withContext(ioDispatcher)`. This finding is the next layer down — the `SharedPreferencesBackend` impl itself still uses `commit()` and the off-thread contract is communicated only via an inline comment. The fix here is contract enforcement (a `@WorkerThread` annotation on `KeyValueBackend`), not a behavior change.

| Field | Value |
|---|---|
| Severity | Low |
| Category | Contract fragility / latent main-thread risk |
| Location | `common/src/androidMain/kotlin/pm/bam/gamedeals/common/storage/SharedPreferencesBackend.kt:11-18` |
| Effort | Trivial |
| Confidence | Low |

**Description.** `SharedPreferencesBackend.writeString`/`remove` use `prefs.edit()…commit()`. The inline comment correctly notes that callers run this off-thread inside `withContext(IO)`, and the only caller (`StorageImpl`) does. So this is not a Main-thread violation today. Fragility: `KeyValueBackend` is `internal`, but if any future caller bypasses `StorageImpl` and uses the backend directly from Main, `commit()` will silently block. There is no compile-time enforcement and no `@WorkerThread` annotation indicating the contract.

**Impact.** Latent. No current Main-thread caller. Future regression risk only.

**Evidence.**
```kotlin
override fun writeString(key: String, value: String): Boolean =
    prefs.edit().putString(key, value).commit()
override fun remove(key: String): Boolean = prefs.edit().remove(key).commit()
```

**Recommended fix.** Annotate `KeyValueBackend` (or the Android impl) with `@WorkerThread` so Lint flags any Main-thread caller. Alternatively, switch to `apply()` and drop the `Boolean` return contract — only the test hook appears to inspect it.

**Confidence rationale.** Low because no current caller is on Main — purely contract-not-enforced risk.
