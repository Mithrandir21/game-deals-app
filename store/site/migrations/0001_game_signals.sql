-- One row per game: its public signals (sale history, reviews, players, ITAD counts) as JSON, refreshed from ITAD at
-- most once a day. Nothing about users is stored. Apply with:
--   npx wrangler@4 d1 migrations apply gamedeals-signals --remote
CREATE TABLE IF NOT EXISTS game_signals (
  id TEXT PRIMARY KEY,          -- ITAD game id (lower-case UUID)
  data TEXT,                    -- the signals JSON; NULL until the first refresh lands
  updated_at INTEGER,           -- epoch ms when data was fetched; NULL with data
  claimed_at INTEGER            -- epoch ms a refresh started; NULL when none is running
);
