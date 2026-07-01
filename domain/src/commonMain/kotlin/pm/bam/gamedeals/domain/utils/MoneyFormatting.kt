package pm.bam.gamedeals.domain.utils

import kotlin.math.round

/**
 * KMP-safe money formatter shared across the data and presentation layers. Prefixes a symbol for the
 * common prefix-style currencies the regional picker exposes (USD → "$9.99", EUR → "€9.99", GBP →
 * "£9.99", …); other currencies fall back to a trailing code ("9.99 PLN") so a wrong/misplaced symbol is
 * never rendered (#212, regional pricing).
 *
 * The ITAD money mapper (`ItadMoney.denominated()`) delegates here so per-field denominated strings and
 * any locally-computed sum (e.g. the bundle "overall value" totals) format identically.
 */
fun formatMoney(amount: Double, currency: String): String {
    val code = currency.uppercase()
    val number = if (code in ZERO_DECIMAL_CURRENCIES) {
        // No minor unit (e.g. JPY/KRW): render the whole-unit amount, never "¥900.00".
        round(amount).toLong().toString()
    } else {
        val cents = round(amount * 100).toLong()
        "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    }
    val symbol = CURRENCY_SYMBOLS[code]
    return if (symbol != null) "$symbol$number" else "$number $currency"
}

/** ISO 4217 currencies with no minor unit — formatted without decimals (JPY → "¥900", not "¥900.00"). */
private val ZERO_DECIMAL_CURRENCIES: Set<String> = setOf(
    "BIF", "CLP", "DJF", "GNF", "ISK", "JPY", "KMF", "KRW",
    "PYG", "RWF", "UGX", "VND", "VUV", "XAF", "XOF", "XPF",
)

/** Prefix-style currency symbols only (currencies whose symbol conventionally trails are left as a code). */
private val CURRENCY_SYMBOLS: Map<String, String> = mapOf(
    "USD" to "$",
    "CAD" to "CA$",
    "AUD" to "A$",
    "NZD" to "NZ$",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "BRL" to "R$",
    "MXN" to "MX$",
    "INR" to "₹",
    "CNY" to "¥",
)
