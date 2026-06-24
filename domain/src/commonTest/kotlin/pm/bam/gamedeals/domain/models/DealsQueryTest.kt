package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers [DealsQuery.sortApiValue] — the field [DealsSortField.token] with a leading `-` only when the
 * direction is [DealsSortDirection.Descending]. The exact tokens (and the per-field default directions)
 * were recovered from ITAD's deals page and verified live against `POST /deals/v2`, so this guards the
 * mapping against accidental drift (e.g. double `-`, wrong default direction, or a renamed token).
 */
class DealsQueryTest {

    @Test
    fun descending_prefixes_a_single_dash_ascending_uses_the_bare_token() {
        assertEquals("-hot", DealsQuery(sortField = DealsSortField.Hottest, sortDirection = DealsSortDirection.Descending).sortApiValue)
        assertEquals("hot", DealsQuery(sortField = DealsSortField.Hottest, sortDirection = DealsSortDirection.Ascending).sortApiValue)
        // Hyphenated tokens must keep their internal hyphens and only gain the leading direction dash.
        assertEquals("-metacritic-user", DealsQuery(sortField = DealsSortField.MetacriticUser, sortDirection = DealsSortDirection.Descending).sortApiValue)
        assertEquals("metacritic-user", DealsQuery(sortField = DealsSortField.MetacriticUser, sortDirection = DealsSortDirection.Ascending).sortApiValue)
    }

    @Test
    fun default_query_sorts_by_hottest_descending() {
        assertEquals(DealsSortField.Hottest, DealsQuery().sortField)
        assertEquals(DealsSortDirection.Descending, DealsQuery().sortDirection)
        assertEquals("-hot", DealsQuery().sortApiValue)
    }

    @Test
    fun every_field_default_direction_matches_the_website() {
        // Only these three fields default to ascending on the website; the rest default to descending.
        val ascendingByDefault = setOf(DealsSortField.Price, DealsSortField.Expiry, DealsSortField.Popular)
        DealsSortField.entries.forEach { field ->
            val expected = if (field in ascendingByDefault) DealsSortDirection.Ascending else DealsSortDirection.Descending
            assertEquals(expected, field.defaultDirection, "Unexpected default direction for $field")
        }
    }

    @Test
    fun field_tokens_match_the_verified_itad_set() {
        assertEquals(
            listOf(
                "hot", "time", "cut", "price", "expiry", "release-date", "rank", "waitlisted",
                "collected", "steam-players", "steam-reviews", "steam-reviews-count",
                "opencritic", "metacritic", "metacritic-user",
            ),
            DealsSortField.entries.map { it.token },
        )
    }
}
