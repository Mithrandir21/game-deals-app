package pm.bam.gamedeals.remote.cheapshark.transformations

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyTransformationImplTest {

    private val denomination: String = "@"

    private val impl = CurrencyTransformationImpl(denomination)

    @Test
    fun `NaN value`() {
        val value = Double.NaN
        val result = impl.valueToDenominated(value)

        assertEquals("@NaN", result)
    }

    @Test
    fun `zero value`() {
        val value = 0.0
        val result = impl.valueToDenominated(value)

        assertEquals("@0.00", result)
    }

    @Test
    fun `positive value`() {
        val value = 1.99
        val result = impl.valueToDenominated(value)

        assertEquals("@1.99", result)
    }

    @Test
    fun `negative value`() {
        val value = -1.99
        val result = impl.valueToDenominated(value)

        assertEquals("-@1.99", result)
    }
}
