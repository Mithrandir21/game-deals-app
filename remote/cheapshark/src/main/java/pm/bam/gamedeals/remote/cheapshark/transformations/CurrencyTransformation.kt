package pm.bam.gamedeals.remote.cheapshark.transformations

fun interface CurrencyTransformation {
    fun valueToDenominated(value: Double): String
}
