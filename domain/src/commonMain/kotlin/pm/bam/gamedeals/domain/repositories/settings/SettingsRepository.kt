package pm.bam.gamedeals.domain.repositories.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.ThemeMode
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * Cross-app user preferences persisted via [Storage] (the same SharedPreferences/NSUserDefaults-backed
 * store [pm.bam.gamedeals.domain.repositories.region.RegionRepository] uses). Currently holds the mature
 * opt-in, which gates adult content on both the Deals tab (`/deals/v2`) and the Bundles tab — so the
 * choice is remembered across launches and shared between the two screens.
 *
 * Defaults to off (adult content excluded) when nothing is stored. The value is exposed reactively so the
 * lists re-filter/re-fetch the moment the toggle flips.
 */
interface SettingsRepository {
    /** Emits the current mature opt-in, seeded from storage on first collection. */
    fun observeMatureOptIn(): Flow<Boolean>
    suspend fun getMatureOptIn(): Boolean
    suspend fun setMatureOptIn(enabled: Boolean)

    /** Emits the current Deals-tab filter, seeded from storage on first collection (empty by default). */
    fun observeDealsFilter(): Flow<DealsFilter>
    suspend fun getDealsFilter(): DealsFilter
    suspend fun setDealsFilter(filter: DealsFilter)

    /**
     * Whether the new-user onboarding carousel has been completed (or skipped). Gates the first-run flow:
     * `false`/absent until the user finishes, skips, or starts sign-in. Once `true` it stays `true` — the
     * carousel can still be replayed from the Account hub without clearing the flag.
     */
    suspend fun getOnboardingCompleted(): Boolean
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * A stable, anonymous per-install id (random UUID), generated once on first access and persisted.
     * Used as the Sentry user id so crashes group by "users affected" without shipping any PII. Survives
     * across launches; resets only on reinstall / "clear data".
     */
    suspend fun getInstallId(): String

    /**
     * Analytics consent. **Off by default** — EU users must explicitly opt in (GDPR), so nothing is sent
     * until [setAnalyticsConsent] is called with `true`. No consent UI is shown while no analytics provider
     * is integrated. [setAnalyticsConsent] both persists the choice and flips the provider's opt-out
     * (and re-identifies on grant), so there's a single source of truth for the whole app.
     */
    fun observeAnalyticsConsent(): Flow<Boolean>
    suspend fun getAnalyticsConsent(): Boolean
    suspend fun setAnalyticsConsent(enabled: Boolean)

    /**
     * The app theme preference. Defaults to [ThemeMode.SYSTEM] (follow the OS) when nothing is stored.
     * Exposed reactively so the app root re-themes the moment the choice changes — no restart needed.
     */
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * When the user last dismissed the "a newer version is available" prompt, as epoch millis, or `null` if
     * they never have. Only consulted for the *dismissible* variant of that prompt: a blocking minimum-version
     * gate ignores this entirely, since it is not dismissible in the first place.
     *
     * Stored as millis rather than an `Instant` so it pairs with [pm.bam.gamedeals.common.time.Clock], the
     * project's injectable time source — which is what makes the "has it been 24h?" decision testable without
     * touching the wall clock.
     */
    fun observeUpdatePromptDismissedAt(): Flow<Long?>
    suspend fun setUpdatePromptDismissedAt(epochMillis: Long)

    /**
     * Forgets any recorded dismissal, so the nudge is eligible to show again immediately.
     *
     * Exists for the debug override in the Account hub: once the prompt has been dismissed it is silent for
     * 24h, which makes it impossible to look at twice in one sitting while developing against it. There is no
     * production caller — the 24h window is the intended behaviour for real users.
     */
    suspend fun clearUpdatePromptDismissedAt()
}

internal const val MATURE_OPT_IN_KEY = "mature_opt_in"
internal const val DEALS_FILTER_KEY = "deals_filter"
internal const val ONBOARDING_COMPLETED_KEY = "onboarding_completed"
internal const val INSTALL_ID_KEY = "install_id"
internal const val ANALYTICS_CONSENT_KEY = "analytics_consent"
internal const val THEME_MODE_KEY = "theme_mode"
internal const val UPDATE_PROMPT_DISMISSED_AT_KEY = "update_prompt_dismissed_at"

internal class SettingsRepositoryImpl(
    private val storage: Storage,
    private val analytics: Analytics,
) : SettingsRepository {

    // Reactive source of truth, lazily seeded from [storage] on first access (null = not yet loaded).
    private val matureOptIn = MutableStateFlow<Boolean?>(null)

    // Analytics-consent source of truth, lazily seeded from [storage] (null = not yet loaded; default off).
    private val analyticsConsent = MutableStateFlow<Boolean?>(null)

    // Deals filter source of truth, lazily seeded from [storage] (null = not yet loaded; absent = empty).
    private val dealsFilter = MutableStateFlow<DealsFilter?>(null)

    // Theme-mode source of truth, lazily seeded from [storage] (null = not yet loaded; default SYSTEM).
    private val themeMode = MutableStateFlow<ThemeMode?>(null)

    // Update-prompt dismissal, lazily seeded from [storage]. Needs a wrapper rather than the plain nullable
    // used above, because here "never dismissed" is itself a null and would be indistinguishable from
    // "not yet read from storage" — which would re-read on every collection.
    private val updatePromptDismissal = MutableStateFlow<DismissalSnapshot?>(null)

    override fun observeMatureOptIn(): Flow<Boolean> =
        matureOptIn
            .onStart { if (matureOptIn.value == null) matureOptIn.value = loadMatureFromStorage() }
            .filterNotNull()

    override suspend fun getMatureOptIn(): Boolean {
        if (matureOptIn.value == null) matureOptIn.value = loadMatureFromStorage()
        return matureOptIn.value ?: false
    }

    override suspend fun setMatureOptIn(enabled: Boolean) {
        storage.save(MATURE_OPT_IN_KEY, enabled)
        matureOptIn.value = enabled
        analytics.capture(AnalyticsEvents.MATURE_OPT_IN_CHANGED, mapOf("enabled" to enabled))
    }

    override fun observeDealsFilter(): Flow<DealsFilter> =
        dealsFilter
            .onStart { if (dealsFilter.value == null) dealsFilter.value = loadDealsFilterFromStorage() }
            .filterNotNull()

    override suspend fun getDealsFilter(): DealsFilter {
        if (dealsFilter.value == null) dealsFilter.value = loadDealsFilterFromStorage()
        return dealsFilter.value ?: DealsFilter()
    }

    override suspend fun setDealsFilter(filter: DealsFilter) {
        storage.save(DEALS_FILTER_KEY, filter)
        dealsFilter.value = filter
        analytics.capture(AnalyticsEvents.DEALS_FILTER_CHANGED, buildMap {
            put("active_count", filter.activeCount)
            filter.minCutPercent?.let { put("min_cut", it) }
            filter.maxPrice?.let { put("max_price", it) }
            put("drm_free", filter.drmFree)
            if (filter.types.isNotEmpty()) put("types", filter.types.map { it.name })
            filter.flag?.let { put("flag", it.name) }
            filter.minSteamPercent?.let { put("min_steam_pct", it) }
            filter.release?.let { put("release", it.name) }
        })
    }

    override suspend fun getOnboardingCompleted(): Boolean =
        runCatching { storage.getNullable<Boolean>(ONBOARDING_COMPLETED_KEY) }.getOrNull() ?: false

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        storage.save(ONBOARDING_COMPLETED_KEY, completed)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getInstallId(): String {
        runCatching { storage.getNullable<String>(INSTALL_ID_KEY) }.getOrNull()?.let { return it }
        val id = Uuid.random().toString()
        storage.save(INSTALL_ID_KEY, id)
        return id
    }

    override fun observeAnalyticsConsent(): Flow<Boolean> =
        analyticsConsent
            .onStart { if (analyticsConsent.value == null) analyticsConsent.value = loadAnalyticsConsentFromStorage() }
            .filterNotNull()

    override suspend fun getAnalyticsConsent(): Boolean {
        if (analyticsConsent.value == null) analyticsConsent.value = loadAnalyticsConsentFromStorage()
        return analyticsConsent.value ?: false
    }

    override suspend fun setAnalyticsConsent(enabled: Boolean) {
        storage.save(ANALYTICS_CONSENT_KEY, enabled)
        analyticsConsent.value = enabled
        // Single point that flips the analytics provider's opt-out. On grant, re-identify so the freshly
        // opted-in provider is tied to the same anonymous install id (Sentry↔analytics correlation). On revoke we just stop
        // sending — no reset(), so the local id/queue are kept.
        analytics.setConsent(enabled)
        if (enabled) analytics.identify(getInstallId())
    }

    override fun observeThemeMode(): Flow<ThemeMode> =
        themeMode
            .onStart { if (themeMode.value == null) themeMode.value = loadThemeModeFromStorage() }
            .filterNotNull()

    override suspend fun getThemeMode(): ThemeMode {
        if (themeMode.value == null) themeMode.value = loadThemeModeFromStorage()
        return themeMode.value ?: ThemeMode.SYSTEM
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        storage.save(THEME_MODE_KEY, mode.name)
        themeMode.value = mode
        analytics.capture(AnalyticsEvents.THEME_MODE_CHANGED, mapOf("mode" to mode.name))
    }

    private suspend fun loadThemeModeFromStorage(): ThemeMode =
        runCatching { storage.getNullable<String>(THEME_MODE_KEY)?.let { ThemeMode.valueOf(it) } }.getOrNull()
            ?: ThemeMode.SYSTEM

    private suspend fun loadMatureFromStorage(): Boolean =
        runCatching { storage.getNullable<Boolean>(MATURE_OPT_IN_KEY) }.getOrNull() ?: false

    private suspend fun loadAnalyticsConsentFromStorage(): Boolean =
        runCatching { storage.getNullable<Boolean>(ANALYTICS_CONSENT_KEY) }.getOrNull() ?: false

    private suspend fun loadDealsFilterFromStorage(): DealsFilter =
        runCatching { storage.getNullable<DealsFilter>(DEALS_FILTER_KEY) }.getOrNull() ?: DealsFilter()

    override fun observeUpdatePromptDismissedAt(): Flow<Long?> =
        updatePromptDismissal
            .onStart { if (updatePromptDismissal.value == null) updatePromptDismissal.value = loadUpdatePromptDismissalFromStorage() }
            .filterNotNull()
            .map { it.atEpochMillis }

    override suspend fun setUpdatePromptDismissedAt(epochMillis: Long) {
        storage.save(UPDATE_PROMPT_DISMISSED_AT_KEY, epochMillis)
        updatePromptDismissal.value = DismissalSnapshot(epochMillis)
    }

    override suspend fun clearUpdatePromptDismissedAt() {
        storage.remove(UPDATE_PROMPT_DISMISSED_AT_KEY)
        updatePromptDismissal.value = DismissalSnapshot(null)
    }

    private suspend fun loadUpdatePromptDismissalFromStorage(): DismissalSnapshot =
        DismissalSnapshot(runCatching { storage.getNullable<Long>(UPDATE_PROMPT_DISMISSED_AT_KEY) }.getOrNull())
}

/**
 * Distinguishes "not yet read from storage" (a null [SettingsRepositoryImpl.updatePromptDismissal]) from
 * "read, and the user has never dismissed the prompt" (a present snapshot whose [atEpochMillis] is null).
 * Without it the two collapse into the same null and storage would be re-read on every collection.
 */
private class DismissalSnapshot(val atEpochMillis: Long?)
