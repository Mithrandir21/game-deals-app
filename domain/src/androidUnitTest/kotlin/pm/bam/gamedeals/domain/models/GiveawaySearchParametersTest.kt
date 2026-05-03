package pm.bam.gamedeals.domain.models

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression coverage for the `Properties.encodeToMap` / `decodeFromMap` round-trip on
 * [GiveawaySearchParameters]. The two `ImmutableList<Pair<…, Boolean>>` fields require a
 * custom serializer ([pm.bam.gamedeals.domain.utils.ImmutableListSerializer]) registered
 * via `@file:UseSerializers` on `Giveaway.kt`; without it the round-trip throws
 * `Serializer for subclass 'SmallPersistentVector' is not found in the polymorphic scope
 * of 'ImmutableList'`. The `parametersSaver` in `GiveawaysScreen` runs this round-trip
 * during composition, so the test fires at the unit level much faster than an
 * instrumented Compose test would.
 */
class GiveawaySearchParametersTest {

    @Test
    fun `default parameters round-trip via asMap and from`() {
        val original = GiveawaySearchParameters()

        val restored = GiveawaySearchParameters.from(original.asMap())

        assertEquals(original, restored)
    }

    @Test
    fun `customised parameters round-trip via asMap and from`() {
        val original = GiveawaySearchParameters(
            platforms = persistentListOf(
                GiveawayPlatform.PC to true,
                GiveawayPlatform.STEAM to false,
            ),
            types = persistentListOf(
                GiveawayType.GAME to true,
                GiveawayType.DLC to false,
            ),
            sortBy = GiveawaySortBy.VALUE,
        )

        val restored = GiveawaySearchParameters.from(original.asMap())

        assertEquals(original, restored)
    }
}
