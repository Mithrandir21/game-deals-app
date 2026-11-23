package pm.bam.gamedeals.common.ui.signals

import pm.bam.gamedeals.domain.models.GameSignals
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SignalsTextTest {

    private fun signals(score: Int?, source: String?, count: Int?) =
        GameSignals("g", 0, emptyList(), reviewScore = score, reviewSource = source, reviewCount = count)

    @Test
    fun steam_reviews_show_as_percent_positive_once_there_are_enough() {
        assertEquals(RowReview.SteamPositive(97), signals(97, "Steam", 250_000).rowReview())
        assertEquals(RowReview.SteamPositive(80), signals(80, "Steam", 50).rowReview())
        assertNull(signals(100, "Steam", 49).rowReview(), "a handful of reviews says little")
        assertEquals(RowReview.SteamPositive(90), signals(90, "Steam", null).rowReview())
    }

    @Test
    fun other_sources_show_by_name() =
        assertEquals(RowReview.Scored("Metascore", 88), signals(88, "Metascore", 40).rowReview())

    @Test
    fun no_score_or_source_shows_nothing() {
        assertNull(signals(null, "Steam", 100).rowReview())
        assertNull(signals(90, null, 100).rowReview())
    }

    @Test
    fun counts_are_made_short() {
        assertEquals("0", formatCompactCount(0))
        assertEquals("999", formatCompactCount(999))
        assertEquals("1k", formatCompactCount(1_000))
        assertEquals("1.2k", formatCompactCount(1_250))
        assertEquals("9.9k", formatCompactCount(9_999))
        assertEquals("45k", formatCompactCount(45_678))
        assertEquals("999k", formatCompactCount(999_999))
        assertEquals("1.2M", formatCompactCount(1_250_000))
        assertEquals("12M", formatCompactCount(12_500_000))
    }
}
