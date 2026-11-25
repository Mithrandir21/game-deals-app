package pm.bam.gamedeals.common.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    // ---------------------------------------------------------------- wishlist links

    private val id2 = "018d937f-1ae9-734c-ba47-bd357cf07edd"

    // Golden values, checked against Python's base64.urlsafe_b64encode(uuid.bytes). The Worker test uses the same.
    private val encoded = "AY2TfwfCcVW24vX5s8W3pQ"
    private val encoded2 = "AY2Tfxrpc0y6R701fPB-3Q"

    private fun manyIds(count: Int) = (0 until count).map { n -> "018d937f-0000-7000-8000-${n.toString().padStart(12, '0')}" }

    @Test
    fun `builds a wishlist link with each id as 22 url-safe base64 characters in the order picked`() {
        assertEquals("https://gamedeals.bam.pm/w/$encoded$encoded2", ShareLinks.wishlist(listOf(id, id2)))
        assertEquals("https://gamedeals.bam.pm/w/$encoded2$encoded", ShareLinks.wishlist(listOf(id2, id)))
        assertEquals("https://gamedeals.bam.pm/w/AAAAAAAAAAAAAAAAAAAAAA", ShareLinks.wishlist(listOf("00000000-0000-0000-0000-000000000000")))
        assertEquals("https://gamedeals.bam.pm/w/_____________________w", ShareLinks.wishlist(listOf("ffffffff-ffff-ffff-ffff-ffffffffffff")))
    }

    @Test
    fun `a wishlist link drops repeats and lower-cases ids`() =
        assertEquals("https://gamedeals.bam.pm/w/$encoded$encoded2", ShareLinks.wishlist(listOf(id.uppercase(), id2, id)))

    @Test
    fun `no wishlist link for an empty list or more than ten games or a bad id`() {
        assertNull(ShareLinks.wishlist(emptyList()))
        assertNull(ShareLinks.wishlist(manyIds(11)))
        assertNull(ShareLinks.wishlist(listOf(id, "half-life-2")))
    }

    @Test
    fun `ten games make a link of about 250 characters`() {
        val link = ShareLinks.wishlist(manyIds(ShareLinks.MAX_WISHLIST_GAMES))!!
        assertEquals(27 + 10 * 22, link.length)
    }

    @Test
    fun `a built wishlist link parses back to its games in order`() {
        val ids = manyIds(10)
        assertEquals(SharedLink.Wishlist(ids), ShareLinks.parse(ShareLinks.wishlist(ids)!!))
        assertEquals(SharedLink.Wishlist(listOf(id2, id)), ShareLinks.parse("https://gamedeals.bam.pm/w/$encoded2$encoded/?from=chat#x"))
    }

    @Test
    fun `parsing a wishlist link drops repeated games`() =
        assertEquals(SharedLink.Wishlist(listOf(id, id2)), ShareLinks.parse("https://gamedeals.bam.pm/w/$encoded$encoded2$encoded"))

    @Test
    fun `ignores broken wishlist links`() {
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/"))
        // Cut short, or one character too many.
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/${encoded.dropLast(1)}"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/${encoded}A"))
        // Base64 is case-sensitive: an upper-cased link is a different game, never the same one.
        assertNotEquals(SharedLink.Wishlist(listOf(id)), ShareLinks.parse("https://gamedeals.bam.pm/w/${encoded.uppercase()}"))
        // Characters outside the url-safe alphabet.
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/AY2TfwfCcVW24vX5s8W3p+"))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/AY2TfwfCcVW24vX5s8W3p="))
        // The last character may only carry two bits: "pR" would smuggle extra bits into the id.
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/AY2TfwfCcVW24vX5s8W3pR"))
        // Eleven games.
        val eleven = ShareLinks.wishlist(manyIds(10))!! + encoded
        assertNull(ShareLinks.parse(eleven))
        assertNull(ShareLinks.parse("https://gamedeals.bam.pm/w/$encoded/more"))
    }
}
