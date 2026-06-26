package pm.bam.gamedeals.domain.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema migrations for [DomainDatabase].
 *
 * ## v1 is the post-release baseline (pre-1.0 reset)
 * The pre-1.0 history (schema v5–v21) only ever existed on developer devices — no production install
 * holds it — so those 17 migrations were dead code and were removed when the schema was collapsed to a
 * clean **v1** baseline before the first public release. The single checked-in schema
 * `domain/schemas/pm.bam.gamedeals.domain.db.DomainDatabase/1.json` is the contract from v1 forward.
 *
 * ## Policy from v1 forward (do not break this)
 * Every schema change MUST add a real `Migration(n, n+1)` here (registered in [DOMAIN_MIGRATIONS]) plus a
 * schema-diff test — once the app is in the wild, real user data must be migrated, never wiped.
 *
 * The build's only destructive fallback is `fallbackToDestructiveMigrationOnDowngrade` (see
 * `DomainModule`), which is a **developer convenience** for the version drop during this reset (a dev
 * device on the old v22 recreates cleanly). Never add a blanket `fallbackToDestructiveMigration()` to the
 * release build: it would silently destroy user data on any future broken migration.
 */
/**
 * v1 → v2 (#211): create the local `RecentlyViewedGame` history table. The DDL mirrors exactly what Room
 * generates for [pm.bam.gamedeals.domain.db.cache.RecentlyViewedGameEntry] (see schemas/.../2.json) so the
 * post-migration identity hash matches and Room doesn't wipe-and-recreate. Purely additive — no existing
 * data is touched.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `RecentlyViewedGame` " +
                "(`gameId` TEXT NOT NULL, `title` TEXT NOT NULL, `boxart` TEXT, " +
                "`viewedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`gameId`))"
        )
    }
}

internal val DOMAIN_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

/** Auto-migrations (`@AutoMigration` spec pairs) — none today; add alongside [DOMAIN_MIGRATIONS]. */
internal val DOMAIN_AUTO_MIGRATIONS: Set<Pair<Int, Int>> = emptySet()
