package pm.bam.gamedeals.remote.igdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Diagnostic harness — exercises the normalizer against realistic CheapShark store titles and
 * edge-case dash / hyphen / suffix arrangements. Each case asserts the *ideal* result; failures
 * surface limitations of the current implementation. Use the [diagnose] driver test to print a
 * pass/fail report when iterating on the normalizer.
 *
 * Categories covered:
 *  - C1 baseline (no decoration)
 *  - C2 trailing edition suffix with " - " delimiter
 *  - C3 trailing parenthetical decoration
 *  - C4 hyphenated GAME NAME with separate suffix
 *  - C5 hyphen-glued suffix (no spaces around inner hyphens)
 *  - C6 chained / multi-tier edition suffixes
 *  - C7 em-dash / en-dash delimiters
 *  - C8 subtitle that contains "edition" as a non-final word
 *  - C9 trailing year decoration
 *  - C10 mixed-case keywords
 *  - C11 colon-delimited subtitle with edition
 */
class TitleNormalizerEdgeCaseDiagnostics {

    private val cases: List<Case> = listOf(
        // C1 — baseline
        Case("C1.1", "Halo Infinite", "Halo Infinite"),
        Case("C1.2", "Hollow Knight", "Hollow Knight"),

        // C2 — trailing edition suffix
        Case("C2.1", "Suicide Squad: Kill the Justice League - Digital Deluxe Edition", "Suicide Squad: Kill the Justice League"),
        Case("C2.2", "Borderlands 3 - GOTY", "Borderlands 3"),
        Case("C2.3", "Death Stranding - Director's Cut", "Death Stranding"),
        Case("C2.4", "Mass Effect - Remastered", "Mass Effect"),
        Case("C2.5", "The Witcher 3: Wild Hunt - Game of the Year Edition", "The Witcher 3: Wild Hunt"),

        // C3 — trailing parenthetical
        Case("C3.1", "Half-Life 2 (Steam)", "Half-Life 2"),
        Case("C3.2", "Hades - Standard Edition (Digital)", "Hades"),

        // C4 — hyphenated GAME NAME with separate suffix
        Case("C4.1", "Spider-Man - Game of the Year Edition", "Spider-Man"),
        Case("C4.2", "Half-Life 2 - Definitive Edition", "Half-Life 2"),
        Case("C4.3", "X-COM - Ultimate Edition", "X-COM"),

        // C5 — hyphen-glued suffix (no surrounding space)
        Case("C5.1", "Game - GOTY-Edition", "Game"),
        Case("C5.2", "Game - Special-Edition", "Game"),

        // C6 — chained edition suffixes
        Case("C6.1", "Game - Definitive Edition - 2024 Remaster", "Game"),
        Case("C6.2", "Some Title - GOTY Edition - Steam Edition", "Some Title"),

        // C7 — em-dash / en-dash
        Case("C7.1", "Cyberpunk 2077 — Ultimate Edition", "Cyberpunk 2077"),
        Case("C7.2", "Cyberpunk 2077 – Ultimate Edition", "Cyberpunk 2077"),

        // C8 — subtitle that CONTAINS "edition" but isn't the final word
        Case("C8.1", "Game - The Edition Strikes Back", "Game - The Edition Strikes Back"),

        // C9 — trailing year in parens
        Case("C9.1", "Doom (2016)", "Doom"),
        Case("C9.2", "Resident Evil 2 (2019)", "Resident Evil 2"),

        // C10 — mixed-case keywords
        Case("C10.1", "Dark Souls - REMASTERED", "Dark Souls"),
        Case("C10.2", "Dark Souls - remastered", "Dark Souls"),

        // C11 — colon-delimited subtitle with edition word
        Case("C11.1", "Pokemon Yellow Version: Special Pikachu Edition", "Pokemon Yellow Version: Special Pikachu Edition"),
        Case("C11.2", "Skyrim: Special Edition", "Skyrim: Special Edition"),
    )

    /**
     * Driver: runs every [cases] entry against [normalizeTitleForLookup] and prints a single
     * report. Fails at the end if any case mismatched — but the per-case assertion failures
     * captured in the report show every weak spot at once instead of stopping at the first.
     */
    @Test
    fun diagnose() {
        val results = cases.map { case ->
            val actual = normalizeTitleForLookup(case.input)
            val pass = actual == case.expected
            CaseResult(case, actual, pass)
        }
        val report = buildString {
            appendLine()
            appendLine("Title normalizer diagnostic report")
            appendLine("===================================")
            appendLine()
            results.forEach { r ->
                val mark = if (r.pass) "✓" else "✗"
                appendLine("  $mark ${r.case.id}  in  : ${quote(r.case.input)}")
                appendLine("        out : ${quote(r.actual)}")
                if (!r.pass) appendLine("        WANT: ${quote(r.case.expected)}")
                appendLine()
            }
            val passed = results.count { it.pass }
            appendLine("Summary: $passed / ${results.size} cases match the ideal result.")
        }
        println(report)
        val failed = results.filterNot { it.pass }
        if (failed.isNotEmpty()) {
            fail("${failed.size} normalizer case(s) returned an unexpected result. See report above; failing IDs: ${failed.joinToString { it.case.id }}.")
        }
    }

    // Individual assertions — keep them too so a single broken case doesn't drown in the report.
    @Test fun c1_baseline_unchanged() = cases.byId("C1.1", "C1.2").assertEach()
    @Test fun c2_trailing_edition_suffix_stripped() = cases.byPrefix("C2.").assertEach()
    @Test fun c3_trailing_parenthetical_stripped() = cases.byPrefix("C3.").assertEach()
    @Test fun c4_hyphenated_game_name_preserved() = cases.byPrefix("C4.").assertEach()
    @Test fun c5_hyphen_glued_suffix_fully_stripped() = cases.byPrefix("C5.").assertEach()
    @Test fun c6_chained_suffixes_collapse() = cases.byPrefix("C6.").assertEach()
    @Test fun c7_em_dash_and_en_dash_delimiters() = cases.byPrefix("C7.").assertEach()
    @Test fun c8_subtitle_containing_edition_word_is_preserved() = cases.byPrefix("C8.").assertEach()
    @Test fun c9_trailing_year_decoration_is_stripped() = cases.byPrefix("C9.").assertEach()
    @Test fun c10_mixed_case_keywords() = cases.byPrefix("C10.").assertEach()
    @Test fun c11_colon_delimited_subtitle_with_edition_word_is_preserved() = cases.byPrefix("C11.").assertEach()

    private fun List<Case>.byId(vararg ids: String) = filter { it.id in ids }
    private fun List<Case>.byPrefix(prefix: String) = filter { it.id.startsWith(prefix) }
    private fun List<Case>.assertEach() = forEach { c -> assertEquals(c.expected, normalizeTitleForLookup(c.input), c.id) }
    private fun quote(s: String) = "\"$s\""

    private data class Case(val id: String, val input: String, val expected: String)
    private data class CaseResult(val case: Case, val actual: String, val pass: Boolean)
}
