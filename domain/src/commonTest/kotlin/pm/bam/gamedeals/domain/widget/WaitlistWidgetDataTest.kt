package pm.bam.gamedeals.domain.widget

import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WaitlistWidgetDataTest {

    private fun item(id: String, cut: Int?, price: String? = "$1") = WaitlistDisplayItem(
        gameId = id,
        title = id,
        bestPriceDenominated = price,
        bestRegularDenominated = "$10",
        discountPercent = cut,
        bestPriceValue = 1.0,
        historicalLowValue = 0.5,
    )

    private val snapshot = WaitlistDisplaySnapshot(
        items = listOf(item("a", 50), item("b", 0), item("removed", 90)),
        regionCode = "GB",
        refreshedAtEpochMs = 42,
    )

    @Test
    fun signed_out_shows_nothing() {
        val data = buildWaitlistWidgetData(signedIn = false, region = "GB", snapshot = snapshot, waitlistedIds = setOf("a"))
        assertFalse(data.signedIn)
        assertTrue(data.deals.isEmpty())
    }

    @Test
    fun no_snapshot_yet_is_not_loaded() {
        val data = buildWaitlistWidgetData(signedIn = true, region = "GB", snapshot = null, waitlistedIds = setOf("a", "b"))
        assertFalse(data.loaded)
        assertEquals(2, data.waitlistCount)
    }

    @Test
    fun drops_removed_games_and_keeps_only_discounts() {
        val data = buildWaitlistWidgetData(signedIn = true, region = "GB", snapshot = snapshot, waitlistedIds = setOf("a", "b"))
        assertEquals(listOf("a"), data.deals.map { it.gameId })
        assertEquals(2, data.waitlistCount)
        assertEquals(42, data.refreshedAtEpochMs)
    }

    @Test
    fun hides_prices_from_another_region() {
        val data = buildWaitlistWidgetData(signedIn = true, region = "US", snapshot = snapshot, waitlistedIds = setOf("a", "b"))
        assertTrue(data.loaded)
        assertTrue(data.deals.isEmpty())
    }

    @Test
    fun spots_games_waitlisted_after_the_snapshot_was_priced() {
        assertTrue(snapshot.isMissingAny(setOf("a", "new")))
        assertFalse(snapshot.isMissingAny(setOf("a", "b")))
    }
}
