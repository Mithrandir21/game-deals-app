package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable

/**
 * A price region the user can pick (epic #205, Phase 3b — #212). ITAD prices only a handful of regions
 * directly and silently falls back to one of them for every other country, so the picker offers exactly
 * those regions instead of a list of countries whose prices it can't deliver.
 *
 * [code] is the ISO 3166-1 alpha-2 country code sent as ITAD's `country=`, and keys every region cache;
 * [id] identifies the choice itself, which differs from [code] only where one region stands for several
 * countries ([EUROPE], [REST_OF_WORLD]). [currency] is the ISO 4217 code ITAD prices the region in.
 */
@Immutable
data class Country(
    val code: String,
    val name: String,
    val currency: String,
    val id: String = code,
) {
    /** What the picker shows beside [name]: the currency, or for [REST_OF_WORLD] whose prices it uses. */
    val priceNote: String
        get() = if (id == REST_OF_WORLD.id) "US prices" else currency
}

/** The default region — ITAD prices were fixed to the US before regional pricing (#212). */
val DEFAULT_COUNTRY: Country = Country("US", "United States", "USD")

/** The euro region. ITAD gives France's prices to most of Europe, so it is priced as France. */
val EUROPE: Country = Country(code = "FR", name = "Europe", currency = "EUR", id = "EU")

/** Every country ITAD doesn't price directly and doesn't map elsewhere gets US prices in US dollars. */
val REST_OF_WORLD: Country = Country(code = "US", name = "Rest of world", currency = "USD", id = "WORLD")

/**
 * The regions offered in the picker: the 17 ITAD prices directly (the same set as the region switcher
 * on isthereanydeal.com, checked against the live API in October 2026), by name, then [REST_OF_WORLD].
 */
val SUPPORTED_COUNTRIES: List<Country> = listOf(
    Country("AR", "Argentina", "ARS"),
    Country("AU", "Australia", "AUD"),
    Country("BR", "Brazil", "BRL"),
    Country("CA", "Canada", "CAD"),
    Country("CN", "China", "CNY"),
    EUROPE,
    Country("IN", "India", "INR"),
    Country("ID", "Indonesia", "IDR"),
    Country("JP", "Japan", "JPY"),
    Country("NZ", "New Zealand", "NZD"),
    Country("PH", "Philippines", "PHP"),
    Country("PL", "Poland", "PLN"),
    Country("KR", "South Korea", "KRW"),
    Country("TW", "Taiwan", "TWD"),
    Country("TR", "Türkiye", "TRY"),
    Country("GB", "United Kingdom", "GBP"),
    DEFAULT_COUNTRY,
    REST_OF_WORLD,
)

/** Countries ITAD prices as France, seen on the live API in October 2026. */
private val EUROPE_MEMBERS: Set<String> = setOf(
    "AT", "BE", "BG", "CH", "CY", "CZ", "DE", "DK", "EE", "ES", "FI", "FR", "GR", "HR", "HU",
    "IE", "IT", "LT", "LU", "LV", "MT", "NL", "NO", "PT", "RO", "RS", "SE", "SI", "SK",
)

/** Countries ITAD prices as Argentina (its website names them as Argentina's fallback countries). */
private val ARGENTINA_MEMBERS: Set<String> = setOf("BO", "BZ", "EC", "GT", "GY", "HN", "PA", "PY", "SR", "SV", "VE")

/**
 * The region whose prices ITAD gives [countryCode] (an ISO country code, any case): its own region where
 * it has one, else [EUROPE] or Argentina for the countries ITAD maps there, else [REST_OF_WORLD]. Used to
 * pick a region from the device's country, and to carry over a country saved before the picker listed
 * regions, so nobody's prices change.
 */
fun regionForCountry(countryCode: String): Country {
    val code = countryCode.uppercase()
    return when (code) {
        in EUROPE_MEMBERS -> EUROPE
        in ARGENTINA_MEMBERS -> SUPPORTED_COUNTRIES.first { it.code == "AR" }
        else -> SUPPORTED_COUNTRIES.firstOrNull { it.id == code } ?: REST_OF_WORLD
    }
}
