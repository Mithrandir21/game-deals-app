package pm.bam.gamedeals.domain.repositories.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.ThemeMode
import pm.bam.gamedeals.logging.analytics.Analytics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryImplTest {

    // Minimal in-memory [Storage]. Holds Any so both String (install id) and Boolean (consent) round-trip.
    private val backing = mutableMapOf<String, Any>()
    private val storage = object : Storage {
        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
            (backing[storageKey] as T?) ?: defaultValue

        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            backing[storageKey] = data
            return true
        }

        override suspend fun containsKey(storageKey: String): Boolean = backing.containsKey(storageKey)
        override suspend fun remove(storageKey: String): Boolean = backing.remove(storageKey) != null
    }

    // Records the consent flips / identity calls so the analytics-consent path can be asserted.
    private class FakeAnalytics : Analytics {
        var consent: Boolean? = null
        val identified = mutableListOf<String>()
        var resetCount = 0
        override fun screen(name: String, properties: Map<String, Any>) = Unit
        override fun capture(event: String, properties: Map<String, Any>) = Unit
        override fun identify(distinctId: String) { identified += distinctId }
        override fun reset() { resetCount++ }
        override fun setConsent(granted: Boolean) { consent = granted }
    }

    private val analytics = FakeAnalytics()
    private val repository = SettingsRepositoryImpl(storage, analytics)

    @Test
    fun install_id_is_generated_and_persisted_on_first_access() = runTest {
        val id = repository.getInstallId()

        assertTrue(id.isNotBlank())
        assertEquals(id, backing[INSTALL_ID_KEY])
    }

    @Test
    fun install_id_is_stable_across_calls() = runTest {
        val first = repository.getInstallId()
        val second = repository.getInstallId()

        assertEquals(first, second)
    }

    @Test
    fun a_previously_persisted_install_id_is_returned_unchanged() = runTest {
        backing[INSTALL_ID_KEY] = "existing-install-id"

        assertEquals("existing-install-id", repository.getInstallId())
    }

    @Test
    fun separate_installs_generate_distinct_ids() = runTest {
        val firstInstall = repository.getInstallId()

        // A fresh repository over empty storage models a reinstall / cleared data.
        backing.clear()
        val secondInstall = SettingsRepositoryImpl(storage, analytics).getInstallId()

        assertNotEquals(firstInstall, secondInstall)
    }

    @Test
    fun analytics_consent_defaults_to_off() = runTest {
        assertFalse(repository.getAnalyticsConsent())
        assertFalse(repository.observeAnalyticsConsent().first())
        // Nothing should have been pushed to the analytics provider before the user has chosen.
        assertNull(analytics.consent)
    }

    @Test
    fun granting_analytics_consent_persists_opts_in_and_identifies() = runTest {
        repository.setAnalyticsConsent(true)

        assertEquals(true, backing[ANALYTICS_CONSENT_KEY])
        assertTrue(repository.getAnalyticsConsent())
        assertTrue(repository.observeAnalyticsConsent().first())
        // Opted the provider in and tied it to the install id (Sentry↔analytics correlation).
        assertEquals(true, analytics.consent)
        assertEquals(listOf(backing[INSTALL_ID_KEY]), analytics.identified)
    }

    @Test
    fun revoking_analytics_consent_opts_out_without_reset() = runTest {
        repository.setAnalyticsConsent(true)
        repository.setAnalyticsConsent(false)

        assertEquals(false, backing[ANALYTICS_CONSENT_KEY])
        assertFalse(repository.getAnalyticsConsent())
        assertEquals(false, analytics.consent)
        // Revoke just stops sending — we keep the local id/queue, so reset() is never called.
        assertEquals(0, analytics.resetCount)
    }

    @Test
    fun theme_mode_defaults_to_system() = runTest {
        assertEquals(ThemeMode.SYSTEM, repository.getThemeMode())
        assertEquals(ThemeMode.SYSTEM, repository.observeThemeMode().first())
    }

    @Test
    fun setting_theme_mode_persists_as_name_and_emits() = runTest {
        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK.name, backing[THEME_MODE_KEY])
        assertEquals(ThemeMode.DARK, repository.getThemeMode())
        assertEquals(ThemeMode.DARK, repository.observeThemeMode().first())
    }

    @Test
    fun a_persisted_theme_mode_is_loaded_on_first_access() = runTest {
        backing[THEME_MODE_KEY] = ThemeMode.LIGHT.name

        assertEquals(ThemeMode.LIGHT, repository.getThemeMode())
    }

    @Test
    fun an_unrecognised_stored_theme_mode_falls_back_to_system() = runTest {
        backing[THEME_MODE_KEY] = "PLAID"

        assertEquals(ThemeMode.SYSTEM, repository.getThemeMode())
    }

    @Test
    fun update_prompt_dismissal_is_absent_until_recorded() = runTest {
        assertNull(repository.observeUpdatePromptDismissedAt().first())
    }

    @Test
    fun update_prompt_dismissal_round_trips() = runTest {
        repository.setUpdatePromptDismissedAt(1_700_000_000_000L)
        assertEquals(1_700_000_000_000L, repository.observeUpdatePromptDismissedAt().first())

        // Survives a fresh instance, i.e. it actually reached storage rather than only the in-memory flow.
        assertEquals(1_700_000_000_000L, SettingsRepositoryImpl(storage, analytics).observeUpdatePromptDismissedAt().first())
    }

    @Test
    fun clearing_the_dismissal_makes_the_prompt_eligible_again() = runTest {
        repository.setUpdatePromptDismissedAt(1_700_000_000_000L)
        repository.clearUpdatePromptDismissedAt()

        assertNull(repository.observeUpdatePromptDismissedAt().first())
        // And the key is gone from storage, not merely nulled in the flow.
        assertFalse(storage.containsKey(UPDATE_PROMPT_DISMISSED_AT_KEY))
        assertNull(SettingsRepositoryImpl(storage, analytics).observeUpdatePromptDismissedAt().first())
    }
}
