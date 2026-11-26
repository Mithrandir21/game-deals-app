package pm.bam.gamedeals.domain.repositories.salecalendar

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The iOS app carries its own copy of `store/site/sales.json` inside `SaleCalendar.swift`, since the widget extension
 * can't use the shared code. Android's copy ([BUNDLED_SALES_JSON]) is built from the file itself, so comparing the two
 * catches an iOS copy left behind. `python3 store/site/validate_sales.py` refreshes it.
 */
class BundledSalesCopiesTest {

    @Test
    fun the_ios_copy_matches_store_site_sales_json() {
        val swift = File(repoRoot(), "iosApp/iosApp/SaleCalendar.swift").readText()
        val iosCopy = Regex("static let bundledJSON = #\"\"\"\\n(.*?)\\n\"\"\"#", RegexOption.DOT_MATCHES_ALL)
            .find(swift)?.groupValues?.get(1)
            ?: error("No bundledJSON block in SaleCalendar.swift")

        assertEquals(
            "SaleCalendar.swift's bundledJSON differs from store/site/sales.json; run python3 store/site/validate_sales.py",
            Json.parseToJsonElement(BUNDLED_SALES_JSON),
            Json.parseToJsonElement(iosCopy),
        )
    }

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("Couldn't find the repository root from ${System.getProperty("user.dir")}")
}
