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
 * @property key the provider-side flag key (PostHog flag key today).
 * @property default the value returned until/unless a provider supplies an override.
 */
enum class FeatureFlag(val key: String, val default: Boolean) {

    /** Gates the "Discover by Tag" entry point on the Deals screen. Staged rollout: hidden until enabled remotely. */
    DiscoverByTag(key = "discover_by_tag", default = false),
    
}
