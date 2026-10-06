package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.FranchiseSaleGame
import kotlin.test.Test
import kotlin.test.assertEquals

/** Storage-backed round-trips for the two franchise background-poll stores (seen signatures + sale snapshot). */
class FranchiseStoresTest {

    // --- FollowedDealSeenStore ---

    @Test
    fun seen_store_defaults_to_empty() = runTest {
        assertEquals(emptySet(), FollowedDealSeenStoreImpl(FakeFranchiseStorage()).get())
    }

    @Test
    fun seen_store_replace_round_trips_and_overwrites() = runTest {
        val store = FollowedDealSeenStoreImpl(FakeFranchiseStorage())

        store.replace(setOf("itad-1@5.0", "itad-2@9.99"), "US")
        assertEquals(setOf("itad-1@5.0", "itad-2@9.99"), store.get())

        store.replace(setOf("itad-3@1.0"), "US") // replace, not append — pruned each poll
        assertEquals(setOf("itad-3@1.0"), store.get())
    }

    @Test
    fun seen_store_remembers_the_country_of_its_signatures() = runTest {
        val store = FollowedDealSeenStoreImpl(FakeFranchiseStorage())
        assertEquals(null, store.country())

        store.replace(setOf("itad-1@750.0"), "JP")

        assertEquals("JP", store.country())
    }

    @Test
    fun seen_store_falls_back_to_empty_on_a_failing_read() = runTest {
        // get() wraps the read in runCatching so a corrupt payload can't crash the background poll.
        assertEquals(emptySet(), FollowedDealSeenStoreImpl(FakeFranchiseStorage(throwOnGet = true)).get())
    }

    // --- FranchiseSaleSnapshotStore ---

    @Test
    fun snapshot_store_defaults_to_empty() = runTest {
        assertEquals(emptyList(), FranchiseSaleSnapshotStoreImpl(FakeFranchiseStorage()).get())
    }

    @Test
    fun snapshot_store_replace_round_trips_and_overwrites() = runTest {
        val store = FranchiseSaleSnapshotStoreImpl(FakeFranchiseStorage())
        val halo = saleGame(itadGameId = "itad-10", title = "Halo 5")
        val doom = saleGame(itadGameId = "itad-20", title = "Doom Eternal")

        store.replace(listOf(halo))
        assertEquals(listOf(halo), store.get())

        store.replace(listOf(doom)) // replaced wholesale each write
        assertEquals(listOf(doom), store.get())
    }

    @Test
    fun snapshot_store_falls_back_to_empty_on_a_failing_read() = runTest {
        assertEquals(emptyList(), FranchiseSaleSnapshotStoreImpl(FakeFranchiseStorage(throwOnGet = true)).get())
    }

    private fun saleGame(itadGameId: String, title: String) = FranchiseSaleGame(
        franchiseId = 1L,
        franchiseName = "Halo",
        igdbGameId = 10L,
        itadGameId = itadGameId,
        title = title,
        cutPercent = 75,
        priceValue = 7.49,
        priceDenominated = "\$7.49",
    )
}
