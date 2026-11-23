package pm.bam.gamedeals.common.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShareLinksTest {

    private val id = "018d937f-07c2-7155-b6e2-f5f9b3c5b7a5"

    @Test
    fun `builds a game link from an ITAD id`() =
        assertEquals("https://gamedeals.bam.pm/g/$id", ShareLinks.game(id))

    @Test
    fun `lower-cases the id so a game has one link`() =
        assertEquals("https://gamedeals.bam.pm/g/$id", ShareLinks.game(id.uppercase()))

    @Test
    fun `refuses to build a link for something that is not an ITAD id`() {
        assertNull(ShareLinks.game(""))
        assertNull(ShareLinks.game("half-life-2"))
        assertNull(ShareLinks.game("$id/extra"))
    }

    @Test
    fun `a built link parses back to its game`() =
        assertEquals(SharedLink.Game(id), ShareLinks.parse(ShareLinks.game(id)!!))

    @Test
    fun `parses despite a trailing slash or query or fragment or upper-case host or surrounding spaces`() {
        assertEquals(SharedLink.Game(id), ShareLinks.parse("https://gamedeals.bam.pm/g/$id/"))
        assertEquals(SharedLink.Game(id), ShareLinks.parse("https://gamedeals.bam.pm/g/$id?ref=x#top"))
        assertEquals(SharedLink.Game(id), ShareLinks.parse("HTTPS://GameDeals.BAM.pm/g/${id.uppercase()}"))
        assertEquals(SharedLink.Game(id), ShareLinks.parse("  https://gamedeals.bam.pm/g/$id \n"))
    }

    @Test
    fun `ignores links that are not ours`() {
        assertNull(ShareLinks.parse("http://gamedeals.bam.pm/g/$id"))
        assertNull(ShareLinks.parse("https://evil.example/g/$id"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm.evil.example/g/$id"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/events/winter-sale-2026.json"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/g/"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/g/not-an-id"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/g/$id/more"))
        assertNull(ShareLinks.parse("pm.bam.gamedeals://game/$id"))
        assertNull(ShareLinks.parse(""))
    }
}
