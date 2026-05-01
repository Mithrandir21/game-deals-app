package pm.bam.gamedeals.remote.cheapshark.transformations

import pm.bam.gamedeals.remote.cheapshark.di.CurrencyDenomination
import javax.inject.Inject
import kotlin.math.absoluteValue

internal class CurrencyTransformationImpl @Inject constructor(
    @CurrencyDenomination private val denomination: String
) : CurrencyTransformation {

    // Always show 2 decimal points for denominated values
    private val valueFormat = "%.2f"

    override fun valueToDenominated(value: Double): String = when {
        value < 0 -> "-$denomination${valueFormat.format(value.absoluteValue)}"
        else -> "$denomination${valueFormat.format(value)}"
    }
}
