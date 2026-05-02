package pm.bam.gamedeals.remote.gamerpower.mappers

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveawayType
import java.time.LocalDateTime

class GamerPowerMappersTest {

    private val parsedDate: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val datetimeParsing: DatetimeParsing = mockk {
        every { parseDatetime(any()) } returns parsedDate
    }
    private val ctx = GamerPowerMapperContext(datetimeParsing)

    @Test
    fun `worth N slash A maps both worth fields to null`() {
        val g = giveaway(worth = "N/A").toDomain(ctx)
        assertNull(g.worth)
        assertNull(g.worthDenominated)
    }

    @Test
    fun `dollar prefixed worth strips dollar sign and parses to double`() {
        val g = giveaway(worth = "$9.99").toDomain(ctx)
        assertEquals(9.99, g.worth!!, 0.0)
        assertEquals("$9.99", g.worthDenominated)
    }

    @Test
    fun `unparseable worth keeps denominated string and nulls numeric value`() {
        val g = giveaway(worth = "free").toDomain(ctx)
        assertNull(g.worth)
        assertEquals("free", g.worthDenominated)
    }

    @Test
    fun `published date routes through DatetimeParsing`() {
        val g = giveaway(publishedDate = "anything").toDomain(ctx)
        assertEquals(parsedDate, g.publishedDate)
    }

    @Test
    fun `RemoteGiveawayType toDomain covers every value`() {
        assertEquals(GiveawayType.GAME, RemoteGiveawayType.GAME.toDomain())
        assertEquals(GiveawayType.DLC, RemoteGiveawayType.DLC.toDomain())
        assertEquals(GiveawayType.BETA, RemoteGiveawayType.BETA.toDomain())
        assertEquals(GiveawayType.OTHER, RemoteGiveawayType.OTHER.toDomain())
    }

    @Test
    fun `single platform string maps to corresponding enum`() {
        val g = giveaway(platforms = "PC").toDomain(ctx)
        assertEquals(listOf(GiveawayPlatform.PC), g.platforms)
    }

    @Test
    fun `comma-separated platforms split into ordered list`() {
        val g = giveaway(platforms = "PC, Steam, Epic Games Store").toDomain(ctx)
        assertEquals(
            listOf(GiveawayPlatform.PC, GiveawayPlatform.STEAM, GiveawayPlatform.EPIC),
            g.platforms,
        )
    }

    @Test
    fun `unrecognised platform falls back to OTHER`() {
        val g = giveaway(platforms = "Atari").toDomain(ctx)
        assertEquals(listOf(GiveawayPlatform.OTHER), g.platforms)
    }

    @Test
    fun `multi word platform values are matched`() {
        val g = giveaway(platforms = "Playstation 4, Xbox Series X|S, Nintendo Switch").toDomain(ctx)
        assertEquals(
            listOf(GiveawayPlatform.PS4, GiveawayPlatform.XBOX_X, GiveawayPlatform.NINTENDO_SWITCH),
            g.platforms,
        )
    }

    private fun giveaway(
        worth: String = "$1.00",
        publishedDate: String = "2026-01-01 00:00:00",
        platforms: String = "PC",
    ) = RemoteGiveaway(
        id = 1,
        title = "Free Game",
        worth = worth,
        thumbnail = "thumb.png",
        image = "image.png",
        description = "desc",
        instructions = "do x",
        openGiveawayUrl = "https://example.com",
        publishedDate = publishedDate,
        type = RemoteGiveawayType.GAME,
        platforms = platforms,
        endDate = "2026-12-31 23:59:59",
        users = 100,
        status = "Active",
        gamerpowerUrl = "https://example.com",
        openGiveaway = "https://example.com/giveaway",
    )
}
