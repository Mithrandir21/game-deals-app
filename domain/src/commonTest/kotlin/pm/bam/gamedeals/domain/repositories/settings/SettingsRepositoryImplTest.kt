package pm.bam.gamedeals.domain.repositories.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import pm.bam.gamedeals.common.storage.Storage
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
        // Nothing should have been pushed to PostHog before the user has chosen.
        assertNull(analytics.consent)
    }

    @Test
    fun granting_analytics_consent_persists_opts_in_and_identifies() = runTest {
        repository.setAnalyticsConsent(true)

        assertEquals(true, backing[ANALYTICS_CONSENT_KEY])
        assertTrue(repository.getAnalyticsConsent())
        assertTrue(repository.observeAnalyticsConsent().first())
        // Flipped PostHog on and tied it to the install id (Sentry↔PostHog correlation).
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
}
