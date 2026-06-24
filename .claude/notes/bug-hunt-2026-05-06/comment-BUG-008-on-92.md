The android-bug-hunting-dispatcher surfaced this issue again on 2026-05-06 (branch `feature/kmp-migration`, HEAD `1a183e5`). Possible regression — the antipattern is present on `origin/dev` (`9783972`) at the post-KMP-migration file path:

> The previous fix recommendation cited `domain/src/main/java/pm/bam/gamedeals/domain/di/DomainModule.kt:55-57`. During the KMP migration that file was split: the converters/database `single`s now live in commonMain (`domain/src/commonMain/kotlin/pm/bam/gamedeals/domain/di/DomainModule.kt`), and the `RoomDatabase.Builder<DomainDatabase>` seed was moved to **`domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt`**. The `setQueryCallback` registration moved with it — but the debug-build gate proposed in this issue did not survive the move.
>
> Re-verified on `origin/dev:domain/src/androidMain/kotlin/pm/bam/gamedeals/domain/di/DomainAndroidModule.kt:19-22`:
>
> ```kotlin
> .setQueryCallback(
>     { sqlQuery, bindArgs -> verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" } },
>     Executors.newSingleThreadExecutor()
> )
> ```
>
> No surrounding `if (isDebuggable())` / build-type check. The fix from this issue is still applicable; the new path to gate is the one above.

Worth confirming whether to reopen this issue or file a fresh one labeled as a regression. Default action recorded by the github-sync skill: comment-only (no new issue, no auto-reopen). Maintainer's call.
