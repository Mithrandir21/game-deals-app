package pm.bam.gamedeals.logging.featureflags

import kotlin.test.Test
import kotlin.test.assertEquals

/** What a crash report says about the switches. */
class SwitchReportTest {

    @Test
    fun every_switch_is_listed_and_a_missing_value_reads_as_its_default() {
        val report = switchReport(mapOf(FeatureFlag.DealSwipe to false))

        assertEquals(FeatureFlag.entries.map { it.key }, report.context.keys.toList())
        assertEquals("off", report.context["deal_swipe"])
        assertEquals("on", report.context["year_in_deals"])
        assertEquals("off", report.context["force_update"], "force_update ships off")
    }

    @Test
    fun the_tag_names_the_switches_that_are_off_in_catalogue_order() {
        val values = FeatureFlag.entries.associateWith { true } + mapOf(FeatureFlag.BundleWatch to false, FeatureFlag.DealLinks to false)

        assertEquals("deal_links,bundle_watch", switchReport(values).off)
    }

    @Test
    fun the_tag_says_none_when_every_switch_is_on() {
        assertEquals("none", switchReport(FeatureFlag.entries.associateWith { true }).off)
    }
}
