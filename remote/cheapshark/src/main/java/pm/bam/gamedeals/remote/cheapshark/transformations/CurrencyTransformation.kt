package pm.bam.gamedeals.remote.cheapshark.transformations

/**
 * Transforms a numeric currency value into a denominated string (e.g. `"$9.99"`).
 *
 * Used by the CheapShark mappers to project remote DTO prices into domain
 * `*Denominated` fields. Exposed as a public functional interface so that
 * tests in other modules can construct a [pm.bam.gamedeals.remote.cheapshark.CheapsharkSourceFactory]
 * without depending on the internal Hilt-bound implementation.
 */
fun interface CurrencyTransformation {
    fun valueToDenominated(value: Double): String
}
