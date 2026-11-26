package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * ITAD sends the asset block in three shapes — an object, absent, or `[]` for a game with no artwork
 * (its backend serializes an empty associative array as a JSON array). The third one used to fail the
 * whole enclosing parse: Sentry KOTLIN-Q, where one artwork-less game blanked an entire game page.
 */
class LenientGameAssetsSerializerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun an_empty_assets_array_decodes_to_empty_artwork_instead_of_failing() {
        // The exact payload shape from KOTLIN-Q.
        val payload = """{"id":"$ID","title":"Some Game","mature":false,"assets":[],"earlyAccess":false}"""

        val info = json.decodeFromString<RemoteItadGameInfo>(payload)

        assertEquals("Some Game", info.title)
        assertNull(info.assets?.boxart)
        assertNull(info.assets.toGameArtwork().boxart)
    }

    @Test
    fun a_populated_assets_object_still_decodes_normally() {
        val payload = """{"id":"$ID","title":"Some Game","assets":{"boxart":"$BOXART","banner400":"$BANNER"}}"""

        val info = json.decodeFromString<RemoteItadGameInfo>(payload)

        assertEquals(BOXART, info.assets?.boxart)
        assertEquals(BANNER, info.assets?.banner400)
    }

    @Test
    fun an_omitted_assets_block_still_decodes_to_null() {
        val payload = """{"id":"$ID","title":"Some Game"}"""

        assertNull(json.decodeFromString<RemoteItadGameInfo>(payload).assets)
    }

    @Test
    fun an_empty_assets_array_is_tolerated_on_the_search_and_deals_models_too() {
        // `assets` rides on three DTOs; ITAD's empty-array quirk is a property of the field, not the
        // endpoint, so search/lookup and the deals feed are exposed to it just as much as /games/info.
        val search = json.decodeFromString<RemoteItadSearchGame>("""{"id":"$ID","title":"Some Game","assets":[]}""")
        assertNull(search.assets?.boxart)

        val deals = json.decodeFromString<RemoteItadDealsGame>(
            """{"id":"$ID","title":"Some Game","assets":[],"deal":{"shop":{"id":61,"name":"Steam"},"price":{"amount":1.0,"currency":"USD"},"url":"https://example.test/deal"}}""",
        )
        assertNull(deals.assets?.boxart)
    }

    private companion object {
        const val ID = "018d937f-3996-7111-949d-f39d1b27ae85"
        const val BOXART = "https://example.test/boxart.jpg"
        const val BANNER = "https://example.test/banner400.jpg"
    }
}
