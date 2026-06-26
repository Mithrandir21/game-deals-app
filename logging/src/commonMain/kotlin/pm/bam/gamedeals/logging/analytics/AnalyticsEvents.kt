package pm.bam.gamedeals.logging.analytics

/**
 * Canonical event names. Centralised so the taxonomy stays consistent and greppable; extend here as new
 * instrumentation is added. Screen views are emitted separately via [Analytics.screen] (driven by the nav
 * graph), so they don't need an entry here.
 *
 * Events are emitted only for consenting users (PostHog starts opted out — see `configurePostHog`), which is
 * what makes the identifying properties below (game/store/bundle ids, raw search text) acceptable. Property
 * keys are snake_case; see each call site for the map it sends.
 */
object AnalyticsEvents {
    /** Process start / app launch. Emitted once per cold start, after analytics is initialised. */
    const val APP_OPENED = "app_opened"

    /** A search was submitted. Carries the raw `query` text and a summary of any active deals filter. */
    const val SEARCH_PERFORMED = "search_performed"

    /** A tag-based discovery search was run. Carries the selected IGDB tag ids + counts. */
    const val DISCOVER_SEARCH_PERFORMED = "discover_search_performed"

    // --- Library (ITAD account) actions. Each carries `game_id`. ---
    const val WAITLIST_ADDED = "waitlist_added"
    const val WAITLIST_REMOVED = "waitlist_removed"
    const val COLLECTION_ADDED = "collection_added"
    const val COLLECTION_REMOVED = "collection_removed"
    const val IGNORED_ADDED = "ignored_added"
    const val IGNORED_REMOVED = "ignored_removed"
    const val NOTE_SAVED = "note_saved"
    const val NOTE_DELETED = "note_deleted"

    /** A franchise/series follow was toggled. Carries `franchise_id`. */
    const val FRANCHISE_FOLLOWED = "franchise_followed"
    const val FRANCHISE_UNFOLLOWED = "franchise_unfollowed"

    /** ITAD OAuth sign-in finished (`result` = success/cancelled/error) / sign-out. */
    const val ACCOUNT_LOGIN = "account_login"
    const val ACCOUNT_LOGOUT = "account_logout"

    // --- Settings changes. ---
    /** Storefront region changed. Carries `country` (ISO code). */
    const val REGION_CHANGED = "region_changed"
    /** Adult-titles opt-in toggled. Carries `enabled`. */
    const val MATURE_OPT_IN_CHANGED = "mature_opt_in_changed"
    /** Deals-tab filter applied. Carries a summary (active_count + the set dimensions). */
    const val DEALS_FILTER_CHANGED = "deals_filter_changed"
    /** App theme preference changed. Carries `mode` (LIGHT/DARK/SYSTEM). */
    const val THEME_MODE_CHANGED = "theme_mode_changed"

    /** Recently-viewed history cleared (`scope` = all/one). */
    const val RECENTLY_VIEWED_CLEARED = "recently_viewed_cleared"

    // --- Click-throughs to external stores / claims. ---
    /** User opened a deal's store link. Carries `game_id`, `store_id`, `store_name`, `discount_pct`. */
    const val DEAL_STORE_OPENED = "deal_store_opened"
    /** User opened a giveaway's claim link. Carries `giveaway_id`, `platform`, `type`. */
    const val GIVEAWAY_OPENED = "giveaway_opened"
    /** User opened a bundle's store link. Carries `bundle_id`, `store_name`, `game_count`. */
    const val BUNDLE_STORE_OPENED = "bundle_store_opened"
    /** User opened a "For You" recommendation. Carries `game_id`, `source`. */
    const val RECOMMENDATION_OPENED = "recommendation_opened"

    // --- Notifications. ---
    /** A background (OS-tray) notification was posted. Carries `kind` + `count`. */
    const val NOTIFICATION_SHOWN = "notification_shown"
    /** A background notification was tapped. Carries `route`. */
    const val NOTIFICATION_OPENED = "notification_opened"
    /** In-app ITAD notifications marked read. */
    const val NOTIFICATION_MARKED_READ = "notification_marked_read"
    const val NOTIFICATIONS_MARKED_ALL_READ = "notifications_marked_all_read"
}
