package pm.bam.gamedeals.shortcuts

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.w3c.dom.Element
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.common.links.ShareLinks
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** How MainActivity reads a launcher shortcut or widget tap back out of its intent ([toAppShortcut]). */
class AppShortcutTest {

    private fun intent(key: String?, gameId: String? = null): Intent = mockk {
        every { getStringExtra(EXTRA_APP_SHORTCUT) } returns key
        every { getStringExtra(EXTRA_GAME_ID) } returns gameId
    }

    @Test
    fun each_tab_shortcut_is_read_back_from_its_key() {
        listOf(AppShortcut.Giveaways, AppShortcut.Deals, AppShortcut.Waitlist)
            .forEach { assertEquals(it, intent(it.key).toAppShortcut()) }
    }

    @Test
    fun a_game_shortcut_is_read_back_with_its_game_id() {
        assertEquals(AppShortcut.Game("018d937f-0000-7000"), intent(AppShortcut.GAME_KEY, "018d937f-0000-7000").toAppShortcut())
    }

    @Test
    fun a_game_shortcut_without_a_game_id_is_ignored() {
        assertNull(intent(AppShortcut.GAME_KEY, gameId = null).toAppShortcut())
        assertNull(intent(AppShortcut.GAME_KEY, gameId = " ").toAppShortcut())
    }

    @Test
    fun a_normal_launch_or_an_unknown_value_is_not_a_shortcut() {
        assertNull(intent(key = null).toAppShortcut())
        assertNull(intent(key = "settings").toAppShortcut())
    }

    @Test
    fun the_launcher_shortcuts_file_uses_the_keys_main_activity_understands() {
        // Read as plain XML: a typo in shortcuts.xml would otherwise only show up as a shortcut that opens the home tab.
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/res/xml/shortcuts.xml"))
        val intents = document.getElementsByTagName("intent").elements()
        val extras = document.getElementsByTagName("extra").elements()

        assertEquals(List(3) { MainActivity::class.java.name }, intents.map { it.androidAttribute("targetClass") })
        assertEquals(List(3) { EXTRA_APP_SHORTCUT }, extras.map { it.androidAttribute("name") })
        assertEquals(
            listOf(AppShortcut.Giveaways.key, AppShortcut.Deals.key, AppShortcut.Waitlist.key),
            extras.map { it.androidAttribute("value") },
        )
    }

    @Test
    fun a_request_made_before_the_app_listens_is_still_delivered() = runTest {
        // A cold start: MainActivity hands over the shortcut before the nav graph starts collecting.
        AppShortcutBus.deliver(AppShortcut.Deals)

        assertEquals(AppShortcut.Deals, AppShortcutBus.requests.first())
    }

    private fun viewIntent(url: String?, action: String = Intent.ACTION_VIEW): Intent = mockk {
        every { this@mockk.action } returns action
        every { dataString } returns url
    }

    @Test
    fun a_shared_game_link_opens_that_game() {
        val id = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5"
        assertEquals(AppShortcut.Game(id), viewIntent("https://gamedeals.bam.pm/g/$id").toSharedLinkShortcut())
    }

    @Test
    fun a_shared_wishlist_link_opens_that_list() {
        val ids = listOf("018d937f-07c2-7155-b6e2-f5f9b3c5b7a5", "018d937f-1ae9-734c-ba47-bd357cf07edd")
        assertEquals(AppShortcut.SharedWishlist(ids), viewIntent(ShareLinks.wishlist(ids)).toSharedLinkShortcut())
    }

    @Test
    fun other_links_and_non_view_intents_are_not_shared_links() {
        val id = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5"
        assertNull(viewIntent("https://gamedeals.bam.pm/").toSharedLinkShortcut())
        assertNull(viewIntent("https://gamedeals.bam.pm/g/not-a-game").toSharedLinkShortcut())
        assertNull(viewIntent("https://gamedeals.bam.pm/w/not-a-list").toSharedLinkShortcut())
        assertNull(viewIntent("pm.bam.gamedeals://shortcut/game/$id").toSharedLinkShortcut())
        assertNull(viewIntent(null).toSharedLinkShortcut())
        assertNull(viewIntent("https://gamedeals.bam.pm/g/$id", action = Intent.ACTION_MAIN).toSharedLinkShortcut())
    }

    @Test
    fun the_manifest_claims_exactly_the_shared_link_paths_and_asks_android_to_verify_them() {
        // The paths must match ShareLinks and the Worker's app-links.mjs, or links fall back to the browser.
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val filters = document.getElementsByTagName("intent-filter").elements()
            .filter { filter -> filter.getElementsByTagName("data").elements().any { it.androidAttribute("host") == ShareLinks.HOST } }
        assertEquals(1, filters.size)
        val filter = filters.single()
        assertEquals("true", filter.androidAttribute("autoVerify"))
        assertEquals(MainActivity::class.java.simpleName, (filter.parentNode as Element).androidAttribute("name").substringAfterLast('.'))
        val data = filter.getElementsByTagName("data").elements()
        assertEquals(listOf("https"), data.mapNotNull { it.androidAttribute("scheme").ifEmpty { null } })
        assertEquals(listOf(ShareLinks.GAME_PATH, ShareLinks.WISHLIST_PATH), data.mapNotNull { it.androidAttribute("pathPrefix").ifEmpty { null } })
    }

    private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

    private fun Element.androidAttribute(name: String): String = getAttributeNS(ANDROID_NS, name)

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
