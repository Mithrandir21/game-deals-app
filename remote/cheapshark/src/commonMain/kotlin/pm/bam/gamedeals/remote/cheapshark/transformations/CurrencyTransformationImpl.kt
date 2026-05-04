package pm.bam.gamedeals.remote.cheapshark.transformations

import kotlin.math.absoluteValue
import kotlin.math.round

internal class CurrencyTransformationImpl(
    private val denomination: String
) : CurrencyTransformation {

    override fun valueToDenominated(value: Double): String {
        if (value.isNaN()) return "${denomination}NaN"
        val sign = if (value < 0) "-" else ""
        return "$sign$denomination${value.absoluteValue.toTwoDecimalString()}"
    }

    private fun Double.toTwoDecimalString(): String {
        val cents = round(this * 100).toLong()
        val whole = cents / 100
        val frac = (cents % 100).toString().padStart(2, '0')
        return "$whole.$frac"
    }
}
