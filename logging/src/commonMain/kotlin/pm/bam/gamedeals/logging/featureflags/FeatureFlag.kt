package pm.bam.gamedeals.logging.featureflags

/**
 * The typed catalogue of feature flags (the app's remote on/off switches) — the single source of truth for every flag's remote [key] and its
 * in-code [default]. Consumers reference an entry (e.g. `FeatureFlag.DiscoverByTag`) rather than a raw string,
 * so keys can't drift and defaults live in one place. Adding a flag is one line here; the [FeatureFlags] seam
 * and its providers are flag-agnostic and need no change.
 *
 * The [default] is what callers get whenever no remote value is available: before the first load of the switch file
 * (`store/site/flags-routes.mjs`), when the file doesn't list the flag, or under [NoOpFeatureFlags]. After one load,
 * the last values fetched stay in use, offline included. Choose it to be the safe / shipped state for that flag.
 *
 * @property key the provider-side flag key.
 * @property default the value returned until/unless a provider supplies an override.
 */
enum class FeatureFlag(val key: String, val default: Boolean) {

    /** Gates the "Discover by Tag" entry point on the Deals screen. Defaults to on. */
    DiscoverByTag(key = "discover_by_tag", default = true),

    /**
     * Gates the minimum-supported-version prompt. Unlike every other flag here this one is *not* self-contained:
     * turning it on only arms the check, and the actual floor comes from the flag's JSON payload —
     * `{"minimum_version": "1.2.0", "blocking": true}` — read via [FeatureFlags.payload].
     *
     * Defaults to `false` so the prompt can never appear without a deliberate remote decision, and every
     * downstream failure (no payload, malformed payload, unparseable version) also resolves to "don't prompt".
     */
    ForceUpdate(key = "force_update", default = false),

    /**
     * Gates the "Year in deals" card on Home and its screen. Even when on, the card only shows in its own
     * December–January window and only to signed-in users. Defaults to on. The developer screen's sample switch shows
     * the card in debug builds outside its window.
     */
    YearInDeals(key = "year_in_deals", default = true),

    /**
     * Gates shared deal links: with it on, sharing a deal shows an image card first and the share text carries the
     * game's gamedeals.bam.pm link (which opens the app, or a preview page) instead of the store link. Off, sharing
     * sends the plain store link again. Opening links others shared always works. A release carrying it must not go
     * out before the Worker serving those links is live with its ITAD key, or shared links show a "prices aren't
     * loading" page.
     */
    DealLinks(key = "deal_links", default = true),

    /** Gates the Waitlist's "share your wishlist" button. Opening a wishlist link someone sent always works. */
    WishlistLinks(key = "wishlist_links", default = true),

    /** Gates the deal swipe card on Home. The swipe screen, once open, stays until the user leaves it. */
    DealSwipe(key = "deal_swipe", default = true),

    /**
     * Gates the sale countdown: off, the countdown widgets show a plain "Tap for today's deals" tile and the iOS
     * last-hours Live Activity doesn't start. The iOS widget extension reads this switch itself (`SaleCalendar.swift`),
     * so the key must stay in step there.
     */
    SaleCountdown(key = "sale_countdown", default = true),

    /**
     * Gates the per-game signals from the Worker (the Rare badge, review lines, the crowd section and the swipe card's
     * tags, developer and year). Off, the app makes no signals calls and those parts disappear quietly.
     */
    GameSignals(key = "game_signals", default = true),

    /**
     * Gates the background check for waitlisted games in new bundles and giveaways. Off, the check is skipped; turned
     * back on, it carries on from what it had already seen.
     */
    BundleWatch(key = "bundle_watch", default = true),

}
