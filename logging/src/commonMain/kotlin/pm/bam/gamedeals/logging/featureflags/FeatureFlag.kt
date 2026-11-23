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
     * December–January window and only to signed-in users. Defaults to off like every staged feature: turn
     * `year_in_deals` on in PostHog when the release that carries it is out. The developer screen's sample switch
     * shows the card in debug builds regardless.
     */
    YearInDeals(key = "year_in_deals", default = false),

}
