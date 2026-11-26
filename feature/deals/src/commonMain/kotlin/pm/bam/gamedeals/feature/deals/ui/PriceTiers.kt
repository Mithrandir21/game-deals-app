package pm.bam.gamedeals.feature.deals.ui

import pm.bam.gamedeals.domain.utils.formatMoney

/** The max-price filter's "Under …" steps, sized for US dollars. */
private val BASE_PRICE_TIERS = listOf(5, 10, 20, 50)

/**
 * How much each ITAD currency scales the base steps, rounded from what the same Steam games cost there in
 * October 2026 (a $14.99 game is ¥1480, ₹479, 16000 KRW). Unlisted currencies (USD, EUR, GBP, CAD, AUD,
 * NZD) price games close enough to dollars to use the base steps.
 */
private val PRICE_TIER_SCALE: Map<String, Int> = mapOf(
    "BRL" to 2,
    "PLN" to 4,
    "CNY" to 4,
    "TWD" to 30,
    "TRY" to 30,
    "PHP" to 40,
    "INR" to 40,
    "JPY" to 100,
    "KRW" to 1000,
    "ARS" to 1000,
    "IDR" to 10000,
)

/** The max-price filter steps for prices in [currency]; the base steps when it is unknown. */
internal fun priceTiers(currency: String?): List<Int> {
    val scale = currency?.let { PRICE_TIER_SCALE[it.uppercase()] } ?: 1
    return BASE_PRICE_TIERS.map { it * scale }
}

/** A filter step written as a price ("$5", "¥500", "50000 IDR"), or the bare number while the currency is unknown. */
internal fun priceTierLabel(tier: Int, currency: String?): String =
    if (currency.isNullOrBlank()) tier.toString() else formatMoney(tier.toDouble(), currency).replace(".00", "")
