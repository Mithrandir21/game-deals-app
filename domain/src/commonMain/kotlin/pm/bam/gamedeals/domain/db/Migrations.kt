package pm.bam.gamedeals.domain.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v5 → v6 — deal-source migration (epic #205, Phase 2a).
 *
 * Game ids change from `Int` to `String` (ITAD UUIDs) on `Deal`, `Game` and `FavouriteGame`, and
 * `Deal.url` / `Store.iconUrl` become stored columns. A primary-key type change is not
 * auto-migratable, but `Deal`, `Game` and `Store` are network-backed caches (re-fetched on next
 * launch) and existing favourites are intentionally reset on the provider switch — so the changed
 * tables are dropped and recreated at the v6 schema rather than data-migrated.
 *
 * The `CREATE TABLE` statements are copied verbatim from the generated schema
 * `domain/schemas/pm.bam.gamedeals.domain.db.DomainDatabase/6.json` so the post-migration identity
 * hash matches the compiled v6 database (Room validates this on open). `Release` and `Giveaway` are
 * unchanged between v5 and v6 and are left untouched.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `Deal`")
        connection.execSQL("DROP TABLE IF EXISTS `Game`")
        connection.execSQL("DROP TABLE IF EXISTS `Store`")
        connection.execSQL("DROP TABLE IF EXISTS `FavouriteGame`")

        connection.execSQL("CREATE TABLE IF NOT EXISTS `Deal` (`dealID` TEXT NOT NULL, `internalName` TEXT NOT NULL, `title` TEXT NOT NULL, `metacriticLink` TEXT, `storeID` INTEGER NOT NULL, `gameID` TEXT NOT NULL, `salePriceValue` REAL NOT NULL, `salePriceDenominated` TEXT NOT NULL, `normalPriceValue` REAL NOT NULL, `normalPriceDenominated` TEXT NOT NULL, `isOnSale` INTEGER NOT NULL, `savings` REAL NOT NULL, `metacriticScore` INTEGER NOT NULL, `steamRatingText` TEXT, `steamRatingPercent` INTEGER NOT NULL, `steamRatingCount` TEXT NOT NULL, `steamAppID` INTEGER, `releaseDate` INTEGER NOT NULL, `lastChange` INTEGER NOT NULL, `dealRating` REAL NOT NULL, `thumb` TEXT NOT NULL, `url` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`dealID`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Game` (`gameID` TEXT NOT NULL, `steamAppID` INTEGER, `cheapestValue` REAL NOT NULL, `cheapestDenominated` TEXT NOT NULL, `cheapestDealID` TEXT NOT NULL, `title` TEXT NOT NULL, `internalName` TEXT NOT NULL, `thumb` TEXT NOT NULL, PRIMARY KEY(`gameID`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Store` (`storeID` INTEGER NOT NULL, `storeName` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `images` TEXT NOT NULL, `iconUrl` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`storeID`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `FavouriteGame` (`gameID` TEXT NOT NULL, `title` TEXT NOT NULL, `thumb` TEXT NOT NULL, `dateAddedMs` INTEGER NOT NULL, PRIMARY KEY(`gameID`))")
    }
}

/**
 * v6 → v7 — ITAD go-live (epic #205, Phase 2b). ITAD doesn't provide the CheapShark-only `Deal`
 * fields (`internalName`, `metacriticScore`, `steamRatingPercent`, `steamRatingCount`, `releaseDate`,
 * `lastChange`, `dealRating`), so those columns become nullable. SQLite can't drop a NOT NULL
 * constraint in place; `Deal` is a network-backed cache, so it is dropped and recreated at the v7
 * schema (DDL copied verbatim from `domain/schemas/.../7.json`). Only `Deal` changed between v6 and v7.
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `Deal`")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `Deal` (`dealID` TEXT NOT NULL, `internalName` TEXT, `title` TEXT NOT NULL, `metacriticLink` TEXT, `storeID` INTEGER NOT NULL, `gameID` TEXT NOT NULL, `salePriceValue` REAL NOT NULL, `salePriceDenominated` TEXT NOT NULL, `normalPriceValue` REAL NOT NULL, `normalPriceDenominated` TEXT NOT NULL, `isOnSale` INTEGER NOT NULL, `savings` REAL NOT NULL, `metacriticScore` INTEGER, `steamRatingText` TEXT, `steamRatingPercent` INTEGER, `steamRatingCount` TEXT, `steamAppID` INTEGER, `releaseDate` INTEGER, `lastChange` INTEGER, `dealRating` REAL, `thumb` TEXT NOT NULL, `url` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`dealID`))")
    }
}

/**
 * v7 → v8 — Favourites → ITAD Waitlist (epic #219, Phase 3). The local `FavouriteGame` table is removed;
 * "saved games" now live on the user's ITAD waitlist (cloud, login-gated). Dropping a table is an
 * in-place migration — no recreate needed.
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `FavouriteGame`")
    }
}

/**
 * v8 → v9 — "lowest ever" badge (UI Improvements board, Phase E, #255). Adds the `Deal.isLowestEver`
 * column. A single nullable-safe `ADD COLUMN` is an in-place, non-destructive migration — the deal
 * cache is preserved (existing rows take the `DEFAULT 0` and pick the flag up on their next TTL
 * refetch). The `NOT NULL DEFAULT 0` matches the `@ColumnInfo(defaultValue = "0")` on the entity so
 * the post-migration schema's identity matches the compiled v9 database (validated on open).
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Deal` ADD COLUMN `isLowestEver` INTEGER NOT NULL DEFAULT 0")
    }
}

internal val DOMAIN_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)

internal val DOMAIN_AUTO_MIGRATIONS: Set<Pair<Int, Int>> = emptySet()
