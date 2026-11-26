package pm.bam.gamedeals.domain.repositories.bundlewatch

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.notifications.AlertKind
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.giveaway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BundleWatchCheckerTest {

    private val now = 1_800_000_000_000L
    private val hades = WaitlistEntry(gameId = "hades-id", title = "Hades")
    private val celeste = WaitlistEntry(gameId = "celeste-id", title = "Celeste")

    private fun bundle(id: Int, title: String, vararg gameIds: String, tierGameIds: List<String> = emptyList(), expiry: Long? = now + 1000) =
        Bundle(
            id = id, title = title, storeName = "Humble", url = "https://humble", expiryEpochMs = expiry, gameCount = gameIds.size,
            priceDenominated = "$11.99", games = persistentListOf(*gameIds.map { Bundle.BundleGame(it, it) }.toTypedArray()),
            tiers = persistentListOf(Bundle.Tier(priceDenominated = "$1", games = persistentListOf(*tierGameIds.map { Bundle.BundleGame(it, it) }.toTypedArray()))),
        )

    private class MemorySeen(var value: Set<String>? = null) : BundleWatchSeenStore {
        override suspend fun get() = value
        override suspend fun replace(signatures: Set<String>) { value = signatures }
        override suspend fun clear() { value = null }
    }

    private var waitlist = listOf(hades, celeste)
    private var bundles = listOf<Bundle>()
    private val giveaways = MutableStateFlow<List<Giveaway>>(emptyList())
    private val seen = MemorySeen(value = emptySet())

    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        everySuspend { getWaitlist() } calls { waitlist }
    }
    private val bundlesRepository: BundlesRepository = mock(MockMode.autoUnit) {
        everySuspend { getBundles() } calls { bundles }
    }
    private val giveawaysRepository: GiveawaysRepository = mock(MockMode.autoUnit) {
        every { observeGiveaways() } returns giveaways
    }

    private val checker = BundleWatchCheckerImpl(
        waitlistRepository, bundlesRepository, giveawaysRepository, seen, Clock { now }, EnglishBundleWatchCopy, TestingLoggingListener(),
    )

    @Test
    fun a_big_waitlist_against_many_bundles_loads_each_source_once_and_finds_every_match() = runTest {
        var waitlistLoads = 0
        var bundleLoads = 0
        everySuspend { waitlistRepository.getWaitlist() } calls { waitlistLoads++; waitlist }
        everySuspend { bundlesRepository.getBundles() } calls { bundleLoads++; bundles }
        waitlist = (1..1_000).map { WaitlistEntry(gameId = "w$it", title = "Waitlisted $it") }
        // 50 bundles of 20 games; every tenth game in each is waitlisted, plus a repeat across two bundles.
        bundles = (1..50).map { b ->
            bundle(b, "Bundle $b", *(1..20).map { g -> if (g % 10 == 0) "w${b * 10 + g}" else "other-$b-$g" }.toTypedArray())
        } + bundle(51, "Repeat", "w20")
        giveaways.value = (1..100).map { giveaway(id = it, title = "Waitlisted ${it * 7} (Steam) Giveaway") }

        val alerts = checker.collectNewAlerts()

        assertEquals(1, waitlistLoads)
        assertEquals(1, bundleLoads)
        // 50 bundles × 2 matches, the repeat in bundle 51, and giveaways for every multiple of 7 up to 700.
        assertEquals(101, alerts.count { it.kind == AlertKind.WAITLIST_BUNDLE })
        assertEquals(100, alerts.count { it.kind == AlertKind.WAITLIST_GIVEAWAY })
        assertEquals(alerts.size, alerts.map { it.notificationId }.toSet().size, "no duplicates")
        assertTrue(checker.collectNewAlerts().isEmpty(), "all remembered")
    }

    @Test
    fun a_waitlisted_game_in_a_new_bundle_alerts_once() = runTest {
        bundles = listOf(bundle(1, "Humble Choice", "other", "hades-id"))

        val alerts = checker.collectNewAlerts()
        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals("Hades is in Humble Choice, from \$11.99", alert.title)
        assertEquals("hades-id", alert.gameId)
        assertEquals(AlertKind.WAITLIST_BUNDLE, alert.kind)

        assertTrue(checker.collectNewAlerts().isEmpty(), "already alerted")
    }

    @Test
    fun games_in_any_tier_count_and_expired_bundles_do_not() = runTest {
        bundles = listOf(
            bundle(1, "Tiered", "other", tierGameIds = listOf("celeste-id")),
            bundle(2, "Ended", "hades-id", expiry = now - 1),
        )
        assertEquals(listOf("celeste-id"), checker.collectNewAlerts().map { it.gameId })
    }

    @Test
    fun a_giveaway_whose_title_is_exactly_a_waitlisted_game_alerts() = runTest {
        giveaways.value = listOf(
            giveaway(id = 7, title = "Hades (Epic Games) Giveaway"),
            giveaway(id = 8, title = "Hades II (Steam) Giveaway"),
            giveaway(id = 9, title = "Celeste Soundtrack (itch.io) Giveaway"),
        )
        val alert = checker.collectNewAlerts().single()
        assertEquals("Hades is free on Epic Games", alert.title)
        assertEquals("hades-id", alert.gameId)
        assertEquals(AlertKind.WAITLIST_GIVEAWAY, alert.kind)
    }

    @Test
    fun only_active_game_and_dlc_giveaways_count() = runTest {
        giveaways.value = listOf(
            giveaway(id = 1, title = "Hades (Epic Games) Giveaway", status = "Expired"),
            giveaway(id = 2, title = "Hades (Epic Games) Giveaway", type = GiveawayType.OTHER),
        )
        assertTrue(checker.collectNewAlerts().isEmpty())
    }

    @Test
    fun the_first_check_takes_note_without_alerting() = runTest {
        seen.value = null
        bundles = listOf(bundle(1, "Humble Choice", "hades-id"))
        assertTrue(checker.collectNewAlerts().isEmpty())
        assertEquals(setOf("b:1:hades-id"), seen.value)

        bundles = bundles + bundle(2, "Fanatical", "celeste-id")
        assertEquals(listOf("celeste-id"), checker.collectNewAlerts().map { it.gameId })
    }

    @Test
    fun signed_out_forgets_what_was_seen_so_the_next_sign_in_starts_by_taking_note() = runTest {
        seen.value = setOf("b:1:hades-id")
        waitlist = emptyList()
        assertTrue(checker.collectNewAlerts().isEmpty())
        assertNull(seen.value)
    }

    @Test
    fun a_bundle_that_ends_and_returns_alerts_again() = runTest {
        bundles = listOf(bundle(1, "Humble Choice", "hades-id"))
        checker.collectNewAlerts()
        bundles = emptyList()
        checker.collectNewAlerts()
        bundles = listOf(bundle(1, "Humble Choice", "hades-id"))
        assertEquals(1, checker.collectNewAlerts().size)
    }

    @Test
    fun a_failure_loading_one_source_still_checks_the_other_and_never_throws() = runTest {
        everySuspend { bundlesRepository.getBundles() } throws IllegalStateException("ITAD down")
        giveaways.value = listOf(giveaway(id = 7, title = "Celeste (GOG) Giveaway"))
        assertEquals(listOf("celeste-id"), checker.collectNewAlerts().map { it.gameId })

        everySuspend { waitlistRepository.getWaitlist() } throws IllegalStateException("offline")
        assertTrue(checker.collectNewAlerts().isEmpty())
    }

    @Test
    fun a_source_that_fails_once_does_not_re_alert_what_it_already_alerted() = runTest {
        bundles = listOf(bundle(1, "Humble Choice", "hades-id"))
        giveaways.value = listOf(giveaway(id = 7, title = "Celeste (GOG) Giveaway"))
        assertEquals(2, checker.collectNewAlerts().size)

        everySuspend { bundlesRepository.getBundles() } throws IllegalStateException("ITAD down")
        assertTrue(checker.collectNewAlerts().isEmpty())
        assertEquals(setOf("b:1:hades-id", "g:7:celeste-id"), seen.value)

        everySuspend { bundlesRepository.getBundles() } calls { bundles }
        assertTrue(checker.collectNewAlerts().isEmpty(), "the bundle was already alerted before the failure")
    }

    @Test
    fun giveaway_titles_lose_the_store_and_the_word_giveaway() {
        assertEquals(GiveawayTitle("hades", "Epic Games"), parseGiveawayTitle("Hades (Epic Games) Giveaway"))
        assertEquals(GiveawayTitle("halflife2episodeone", "Steam"), parseGiveawayTitle("Half-Life 2: Episode One (Steam) giveaway"))
        assertEquals(GiveawayTitle("celeste", null), parseGiveawayTitle("Celeste Giveaway"))
        assertEquals(GiveawayTitle("celeste", null), parseGiveawayTitle("  Celeste  "))
        assertEquals(matchKey("Half Life 2 Episode One"), parseGiveawayTitle("Half-Life 2: Episode One (Steam) Giveaway").key)
    }
}
