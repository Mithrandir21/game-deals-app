package pm.bam.gamedeals.domain.repositories.featureflags

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import java.io.File

/**
 * The switch file template the Worker's namespace starts from (`store/site/flags.json`) must list every switch at the
 * value the app ships with, so pasting it changes nothing; and the iOS widget's own reader must use the same key.
 */
class SwitchFileTemplateTest {

    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }
        ?: error("Couldn't find the repository root from ${System.getProperty("user.dir")}")

    @Test
    fun the_template_lists_every_switch_at_its_shipped_value() {
        val template = parseFlags(File(root, "store/site/flags.json").readText(), Json)

        assertEquals(FeatureFlag.entries.map { it.key }.sorted(), template.keys.sorted())
        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.key, flag.default, template.getValue(flag.key).enabled)
        }
    }

    @Test
    fun the_ios_widget_reads_the_same_sale_countdown_key() {
        val swift = File(root, "iosApp/iosApp/SaleCalendar.swift").readText()
        val key = Regex("""enum SaleCountdownSwitch \{.*?static let key = "([a-z_]+)"""", RegexOption.DOT_MATCHES_ALL)
            .find(swift)?.groupValues?.get(1)

        assertEquals(FeatureFlag.SaleCountdown.key, key)
    }
}
