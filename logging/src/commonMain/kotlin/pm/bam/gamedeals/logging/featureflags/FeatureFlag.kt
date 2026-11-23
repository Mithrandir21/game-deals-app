package pm.bam.gamedeals.logging.featureflags

/**
 * The typed catalogue of feature flags — the single source of truth for every flag's remote [key] and its
 * in-code [default]. Consumers reference an entry (e.g. `FeatureFlag.DiscoverByTag`) rather than a raw string,
 * so keys can't drift and defaults live in one place. Adding a flag is one line here; the [FeatureFlags] seam
 * and its providers are flag-agnostic and need no change.
 *
 * The [default] is what callers get whenever no remote value is available — before the first network load, when
 * offline, or under a provider that doesn't do remote config ([NoOpFeatureFlags]). Choose it to be the safe /
 * shipped state for that flag.
 *
 * @property key the provider-side flag key.
 * @property default the value returned until/unless a provider supplies an override.
 */
enum class FeatureFlag(val key: String, val default: Boolean) {

    /**
     * Gates the "Discover by Tag" entry point on the Deals screen. Defaults to on: with no remote flag provider
     * integrated the feature ships to everyone, while a future provider can still turn it off remotely.
     */
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
     * December–January window and only to signed-in users. Defaults to on: with no remote flag provider
     * integrated, an off default would mean the card never shows. The developer screen's sample switch shows the
     * card in debug builds outside its window.
     */
    YearInDeals(key = "year_in_deals", default = true),

    /**
     * Gates shared deal links: with it on, sharing a deal shows an image card first and the share text carries the
     * game's gamedeals.bam.pm link (which opens the app, or a preview page) instead of the store link. It also gates
     * the Waitlist's "share your wishlist" link, which needs the same Worker. Opening links others shared always
     * works. Defaults to on, as no remote flag provider is integrated. A release carrying it must not go out before the Worker serving
     * those links is live with its ITAD key, or shared links show a "prices aren't loading" page.
     */
    DealLinks(key = "deal_links", default = true),

}
