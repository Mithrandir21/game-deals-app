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
    val cents = round(amount * 100).toLong()
    val number = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    val symbol = CURRENCY_SYMBOLS[currency.uppercase()]
    return if (symbol != null) "$symbol$number" else "$number $currency"
}

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
)
