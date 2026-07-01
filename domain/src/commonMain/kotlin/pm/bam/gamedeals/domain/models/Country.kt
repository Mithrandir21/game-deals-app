package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable

/**
 * A geographic grouping for the region picker. ITAD has no region API — currency and prices are keyed off
 * the country [code] alone — so this is purely a display grouping (headers) mirroring ITAD's region page.
 */
@Immutable
enum class Region(val displayName: String) {
    AFRICA("Africa"),
    AMERICAS("Americas"),
    ASIA("Asia"),
    EUROPE("Europe"),
    OCEANIA("Oceania"),
}

/**
 * A storefront region for regional pricing (epic #205, Phase 3b — #212). [code] is the ISO 3166-1
 * alpha-2 country code passed to ITAD's `country=` parameter; [name] is the display label; [region] is
 * the continent group the picker files it under.
 */
@Immutable
data class Country(
    val code: String,
    val name: String,
    val region: Region,
)

/** The default region — ITAD prices were fixed to the US before regional pricing (#212). */
val DEFAULT_COUNTRY: Country = Country("US", "United States", Region.AMERICAS)

/**
 * The regions offered in the country picker — the set of ITAD-supported `country` values ITAD reliably
 * prices, sorted by display name. ITAD returns prices (and currency) for the chosen region; the picker
 * groups these by [Country.region] via [countriesByRegion].
 */
val SUPPORTED_COUNTRIES: List<Country> = listOf(
    // Africa
    Country("EG", "Egypt", Region.AFRICA),
    Country("KE", "Kenya", Region.AFRICA),
    Country("MA", "Morocco", Region.AFRICA),
    Country("NG", "Nigeria", Region.AFRICA),
    Country("ZA", "South Africa", Region.AFRICA),
    // Americas
    Country("AR", "Argentina", Region.AMERICAS),
    Country("BR", "Brazil", Region.AMERICAS),
    Country("CA", "Canada", Region.AMERICAS),
    Country("CL", "Chile", Region.AMERICAS),
    Country("CO", "Colombia", Region.AMERICAS),
    Country("CR", "Costa Rica", Region.AMERICAS),
    Country("EC", "Ecuador", Region.AMERICAS),
    Country("GT", "Guatemala", Region.AMERICAS),
    Country("MX", "Mexico", Region.AMERICAS),
    Country("PE", "Peru", Region.AMERICAS),
    Country("UY", "Uruguay", Region.AMERICAS),
    DEFAULT_COUNTRY,
    // Asia
    Country("AE", "United Arab Emirates", Region.ASIA),
    Country("CN", "China", Region.ASIA),
    Country("HK", "Hong Kong", Region.ASIA),
    Country("ID", "Indonesia", Region.ASIA),
    Country("IL", "Israel", Region.ASIA),
    Country("IN", "India", Region.ASIA),
    Country("JP", "Japan", Region.ASIA),
    Country("KR", "South Korea", Region.ASIA),
    Country("KW", "Kuwait", Region.ASIA),
    Country("KZ", "Kazakhstan", Region.ASIA),
    Country("MY", "Malaysia", Region.ASIA),
    Country("PH", "Philippines", Region.ASIA),
    Country("PK", "Pakistan", Region.ASIA),
    Country("QA", "Qatar", Region.ASIA),
    Country("SA", "Saudi Arabia", Region.ASIA),
    Country("SG", "Singapore", Region.ASIA),
    Country("TH", "Thailand", Region.ASIA),
    Country("TR", "Türkiye", Region.ASIA),
    Country("TW", "Taiwan", Region.ASIA),
    Country("VN", "Vietnam", Region.ASIA),
    // Europe
    Country("AT", "Austria", Region.EUROPE),
    Country("BE", "Belgium", Region.EUROPE),
    Country("BG", "Bulgaria", Region.EUROPE),
    Country("CH", "Switzerland", Region.EUROPE),
    Country("CY", "Cyprus", Region.EUROPE),
    Country("CZ", "Czechia", Region.EUROPE),
    Country("DE", "Germany", Region.EUROPE),
    Country("DK", "Denmark", Region.EUROPE),
    Country("EE", "Estonia", Region.EUROPE),
    Country("ES", "Spain", Region.EUROPE),
    Country("FI", "Finland", Region.EUROPE),
    Country("FR", "France", Region.EUROPE),
    Country("GB", "United Kingdom", Region.EUROPE),
    Country("GR", "Greece", Region.EUROPE),
    Country("HR", "Croatia", Region.EUROPE),
    Country("HU", "Hungary", Region.EUROPE),
    Country("IE", "Ireland", Region.EUROPE),
    Country("IS", "Iceland", Region.EUROPE),
    Country("IT", "Italy", Region.EUROPE),
    Country("LT", "Lithuania", Region.EUROPE),
    Country("LU", "Luxembourg", Region.EUROPE),
    Country("LV", "Latvia", Region.EUROPE),
    Country("MT", "Malta", Region.EUROPE),
    Country("NL", "Netherlands", Region.EUROPE),
    Country("NO", "Norway", Region.EUROPE),
    Country("PL", "Poland", Region.EUROPE),
    Country("PT", "Portugal", Region.EUROPE),
    Country("RO", "Romania", Region.EUROPE),
    Country("RS", "Serbia", Region.EUROPE),
    Country("RU", "Russia", Region.EUROPE),
    Country("SE", "Sweden", Region.EUROPE),
    Country("SI", "Slovenia", Region.EUROPE),
    Country("SK", "Slovakia", Region.EUROPE),
    Country("UA", "Ukraine", Region.EUROPE),
    // Oceania
    Country("AU", "Australia", Region.OCEANIA),
    Country("NZ", "New Zealand", Region.OCEANIA),
).sortedBy { it.name }

/**
 * Groups [countries] by [Region] for the picker's section headers: regions appear in [Region] declaration
 * order, each region's countries sorted by name, and regions with no matching country are omitted.
 */
fun countriesByRegion(countries: List<Country> = SUPPORTED_COUNTRIES): List<Pair<Region, List<Country>>> =
    Region.entries
        .map { region -> region to countries.filter { it.region == region }.sortedBy { it.name } }
        .filter { (_, entries) -> entries.isNotEmpty() }
