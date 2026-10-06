package pm.bam.gamedeals.domain.utils

import kotlin.math.round

/**
 * KMP-safe money formatter shared across the data and presentation layers. Prefixes a symbol for the
 * common prefix-style currencies the regional picker exposes (USD → "$9.99", EUR → "€9.99", GBP →
 * "£9.99", …); other currencies fall back to a trailing code ("9.99 PLN") so a wrong/misplaced symbol is
 * never rendered (#212, regional pricing). Whole units are grouped in threes with commas ("$1,299.00",
 * "81,199 IDR"), to go with the "." decimal point the app uses everywhere.
 *
 * The ITAD money mapper (`ItadMoney.denominated()`) delegates here so per-field denominated strings and
 * any locally-computed sum (e.g. the bundle "overall value" totals) format identically.
 */
fun formatMoney(amount: Double, currency: String): String {
    val code = currency.uppercase()
    val number = if (code in ZERO_DECIMAL_CURRENCIES) {
        // No minor unit (e.g. JPY/KRW): render the whole-unit amount, never "¥900.00".
        groupThousands(round(amount).toLong())
    } else {
        val cents = round(amount * 100).toLong()
        "${groupThousands(cents / 100)}.${(cents % 100).toString().padStart(2, '0')}"
    }
    val symbol = CURRENCY_SYMBOLS[code]
    return if (symbol != null) "$symbol$number" else "$number $currency"
}

/** Groups a whole amount's digits in threes with commas: 81199 → "81,199". */
fun groupThousands(whole: Long): String {
    val digits = if (whole < 0) (-whole).toString() else whole.toString()
    val grouped = buildString {
        digits.forEachIndexed { i, digit ->
            if (i > 0 && (digits.length - i) % 3 == 0) append(',')
            append(digit)
        }
    }
    return if (whole < 0) "-$grouped" else grouped
}

/**
 * Currencies formatted without decimals (JPY → "¥900", not "¥900.00"): the ISO 4217 ones with no minor
 * unit, plus IDR, whose ISO cents nobody uses — stores price it in whole rupiah, and so does ITAD.
 */
private val ZERO_DECIMAL_CURRENCIES: Set<String> = setOf(
    "BIF", "CLP", "DJF", "GNF", "IDR", "ISK", "JPY", "KMF", "KRW",
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
