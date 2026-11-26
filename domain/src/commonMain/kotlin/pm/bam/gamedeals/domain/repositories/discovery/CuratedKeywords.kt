package pm.bam.gamedeals.domain.repositories.discovery

/**
 * Hand-curated IGDB keyword slugs surfaced in the tag picker (epic #307). IGDB's full `/v4/keywords`
 * table is thousands of noisy, community-authored entries — unusable as a fixed picker — so we expose
 * only this small allow-list, which recovers the Steam-tag-style granularity (Roguelike, Metroidvania…)
 * the curated genre/theme/mode/perspective enums lack.
 *
 * Slugs are resolved to ids at runtime via `/v4/keywords where slug = (…)`; any slug IGDB doesn't
 * recognise is simply dropped (no crash), so an occasional miss here is harmless. Verify/extend these
 * against live IGDB data when tuning the picker.
 */
internal val CURATED_KEYWORD_SLUGS: List<String> = listOf(
    "roguelike",
    "rogue-like",
    "metroidvania",
    "souls-like",
    "bullet-hell",
    "deck-building",
    "dungeon-crawler",
    "tower-defense",
    "city-builder",
    "battle-royale",
    "hack-and-slash",
    "turn-based",
    "open-world",
    "sandbox",
    "crafting",
    "survival",
    "stealth",
    "horror",
    "co-op",
    "pixel-art",
)
