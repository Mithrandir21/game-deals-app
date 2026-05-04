package pm.bam.gamedeals.remote.cheapshark.transformations

import kotlin.test.Test
import kotlin.test.assertEquals

class CurrencyTransformationImplTest {

    private val denomination: String = "@"

    private val impl = CurrencyTransformationImpl(denomination)

    @Test
    fun nan_value() {
        val value = Double.NaN
        val result = impl.valueToDenominated(value)

        assertEquals("@NaN", result)
    }

    @Test
    fun zero_value() {
        val value = 0.0
        val result = impl.valueToDenominated(value)

        assertEquals("@0.00", result)
    }

    @Test
    fun positive_value() {
        val value = 1.99
        val result = impl.valueToDenominated(value)

        assertEquals("@1.99", result)
    }

    @Test
    fun negative_value() {
        val value = -1.99
        val result = impl.valueToDenominated(value)

        assertEquals("-@1.99", result)
    }
}
