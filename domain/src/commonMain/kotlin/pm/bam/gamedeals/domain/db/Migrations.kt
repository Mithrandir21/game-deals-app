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

/**
 * v9 → v10 — richer deal badges (deal-badge work). Adds three `Deal` columns: `isNewHistoricalLow`
 * (ITAD flag `"N"`, the new-low badge), `isStoreLow` (ITAD flag `"S"`, the store-low badge) and
 * `hasVoucher` (the "with voucher" scissors badge). Each is a non-destructive `ADD COLUMN`, so the
 * deal cache is preserved (existing rows take the `DEFAULT 0` and pick the signals up on their next
 * TTL refetch). The `NOT NULL DEFAULT 0` matches the `@ColumnInfo(defaultValue = "0")` on the entity
 * so the post-migration schema's identity matches the compiled v10 database (validated on open).
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Deal` ADD COLUMN `isNewHistoricalLow` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `Deal` ADD COLUMN `isStoreLow` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `Deal` ADD COLUMN `hasVoucher` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v10 → v11 — TTL-gate the under-cached tables (ITAD caching strategy, Phase 1, #262). Adds an
 * `expires` column to `Game`, `Release` and `Giveaway` so each is gated behind the same per-row TTL
 * `CachedResource` pattern as `Deal`/`Store`, instead of refetching on every `observe*` subscribe.
 * Each is a non-destructive `ADD COLUMN`, so the caches are preserved: existing rows take the
 * `DEFAULT 0` (already-expired) and refetch once on next access. The `NOT NULL DEFAULT 0` matches the
 * `@ColumnInfo(defaultValue = "0")` on each entity so the post-migration schema's identity matches
 * the compiled v11 database (validated on open).
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Game` ADD COLUMN `expires` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `Release` ADD COLUMN `expires` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE `Giveaway` ADD COLUMN `expires` INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v11 → v12 — region as a cache dimension (ITAD caching strategy, D5 / Phase 2, #263). Adds a
 * `country` column to `Deal` so cached deals are keyed by `(storeID, country)` and a region switch
 * reads the new region's rows instead of clearing the whole cache (#212). A non-destructive
 * `ADD COLUMN` keeping the `dealID` primary key: existing rows are **backfilled to the default
 * region** (`'US'` = `DEFAULT_COUNTRY`) rather than treated-as-expired, so the common (default-region)
 * user's cache is preserved and there is no first-launch refetch storm. A non-default-region user's
 * rows are filtered out by the region predicate and harmlessly overwritten (same `dealID`) on the
 * next per-store refetch. The `NOT NULL DEFAULT 'US'` matches the `@ColumnInfo(defaultValue = "US")`
 * on the entity so the post-migration schema identity matches the compiled v12 database.
 *
 * Only `Deal` is keyed: `Store` is fetched globally (region-invariant) and not region-cleared today,
 * so it is intentionally left unkeyed (revisit if shops become per-country).
 */
private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `Deal` ADD COLUMN `country` TEXT NOT NULL DEFAULT 'US'")
    }
}

/**
 * v12 → v13 — transact-tier detail caches (ITAD caching strategy, Phase 3, #264). Creates two new
 * region-keyed cache tables, `DealDetailsCache` and `GameDetailsCache`, each holding a serialized
 * details blob keyed by `(id, country)` with an `expires` TTL stamp. New tables only — nothing
 * existing is touched. The `CREATE TABLE` DDL is copied verbatim from the generated schema
 * `domain/schemas/.../13.json` so the post-migration identity hash matches the compiled v13 database.
 */
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `DealDetailsCache` (`dealId` TEXT NOT NULL, `country` TEXT NOT NULL, `json` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`dealId`, `country`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `GameDetailsCache` (`gameId` TEXT NOT NULL, `country` TEXT NOT NULL, `json` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`gameId`, `country`))")
    }
}

/**
 * v13 → v14 — price-history cache (ITAD caching strategy, Phase 4, #265). Creates the region-keyed
 * `PriceHistoryCache` table, holding a serialized price-history series blob keyed by `(gameId, country)`
 * with a `fetchedAt` stamp (for the 30-day retention sweep) and an `expires` TTL stamp. New table only —
 * nothing existing is touched. The `CREATE TABLE` DDL is copied verbatim from the generated schema
 * `domain/schemas/.../14.json` so the post-migration identity hash matches the compiled v14 database.
 */
private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `PriceHistoryCache` (`gameId` TEXT NOT NULL, `country` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`gameId`, `country`))")
    }
}

/**
 * v14 → v15 — bundles cache (ITAD caching strategy, Phase 5b, #266). Creates the `BundlesCache` table,
 * holding a region's whole `List<Bundle>` as a serialized blob keyed by `(country)` with a `fetchedAt`
 * stamp (for the retention sweep) and an `expires` TTL stamp. New table only — nothing existing is
 * touched. The `CREATE TABLE` DDL is copied verbatim from the generated schema `domain/schemas/.../15.json`.
 */
private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `BundlesCache` (`country` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`country`))")
    }
}

/**
 * v15 → v16 — stats rankings cache (ITAD caching strategy, Phase 5c, #266). Creates the
 * `StatsRankingsCache` table, holding one ranking's `List<RankedGame>` as a serialized blob keyed by
 * `(rankingType, country)` with a `fetchedAt` stamp (for the retention sweep) and an `expires` TTL stamp.
 * New table only — nothing existing is touched. The `CREATE TABLE` DDL is copied verbatim from the
 * generated schema `domain/schemas/.../16.json`.
 */
private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `StatsRankingsCache` (`rankingType` TEXT NOT NULL, `country` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`rankingType`, `country`))")
    }
}

/**
 * v16 → v17 — Steam-appID → ITAD-UUID identity mapping (ITAD caching strategy, Phase 6, #267). Creates
 * the `GameIdMapping` table, mapping `steamAppId` to a resolved game UUID with a long `expires` TTL.
 * Region-invariant (no `country`). New table only — nothing existing is touched. The `CREATE TABLE` DDL
 * is copied verbatim from the generated schema `domain/schemas/.../17.json`.
 */
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `GameIdMapping` (`steamAppId` INTEGER NOT NULL, `gameId` TEXT NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`steamAppId`))")
    }
}

/**
 * v17 → v18 — user-data persistence (ITAD caching strategy, Phase 7a, #268). Creates the login-scoped
 * `WaitlistGameId` and `CollectionGameId` id-set tables (one `gameId` PK each) backing the heart/collected
 * state. New tables only — nothing existing is touched. The `CREATE TABLE` DDL is copied verbatim from the
 * generated schema `domain/schemas/.../18.json`.
 */
private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `WaitlistGameId` (`gameId` TEXT NOT NULL, PRIMARY KEY(`gameId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `CollectionGameId` (`gameId` TEXT NOT NULL, PRIMARY KEY(`gameId`))")
    }
}

/**
 * v18 → v19 — ignore list (epic #272, P3 #279). Creates the login-scoped `IgnoredGameId` id-set table
 * (one `gameId` PK), backing the Deals/Search ignore filter (#280). New table only — nothing existing is
 * touched. The `CREATE TABLE` DDL is copied verbatim from the generated schema `domain/schemas/.../19.json`
 * (identical shape to `WaitlistGameId`/`CollectionGameId`) so the post-migration identity matches the v19 database.
 */
private val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `IgnoredGameId` (`gameId` TEXT NOT NULL, PRIMARY KEY(`gameId`))")
    }
}

/**
 * v19 → v20 — bundle detail redesign (Bundles redesign work). The `BundlesCache` table DDL is unchanged
 * (still a region-keyed JSON blob), but the serialized `Bundle` shape gained tier structure + per-game
 * pricing inputs (`tiers`, `details`, `publishEpochMs`, `isMature`, `priceValue`). New fields are
 * nullable/defaulted so a stale blob still decodes, but an old blob would render with no tiers until its
 * 12h TTL lapses — so the network-backed cache is dropped and recreated (a cache clear; the feed re-fetches
 * on next open), matching the recreate rationale used by MIGRATION_5_6/6_7. The `CREATE TABLE` DDL is
 * copied verbatim from `domain/schemas/.../20.json` so the post-migration identity matches the v20 database.
 */
private val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `BundlesCache`")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `BundlesCache` (`country` TEXT NOT NULL, `json` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expires` INTEGER NOT NULL, PRIMARY KEY(`country`))")
    }
}

/**
 * v20 → v21 — tag-discovery vocabulary cache (epic #307, Phase 3, #310). Creates the `IgdbTag` table,
 * caching the IGDB genre/theme/game-mode/player-perspective enums + the curated keyword allow-list
 * (keyed by `dimension` + `igdbId`, with a long `expires` TTL) so the tag picker opens instantly. New
 * table only — nothing existing is touched. The `CREATE TABLE` DDL is copied verbatim from the generated
 * schema `domain/schemas/.../21.json` so the post-migration identity matches the compiled v21 database.
 */
private val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `IgdbTag` (`dimension` TEXT NOT NULL, `igdbId` INTEGER NOT NULL, `name` TEXT NOT NULL, `slug` TEXT, `expires` INTEGER NOT NULL, PRIMARY KEY(`dimension`, `igdbId`))")
    }
}

internal val DOMAIN_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)

internal val DOMAIN_AUTO_MIGRATIONS: Set<Pair<Int, Int>> = emptySet()
