package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable

/**
 * A storefront region for regional pricing (epic #205, Phase 3b — #212). [code] is the ISO 3166-1
 * alpha-2 country code passed to ITAD's `country=` parameter; [name] is the display label.
 */
@Immutable
data class Country(
    val code: String,
    val name: String,
)

/** The default region — ITAD prices were fixed to the US before regional pricing (#212). */
val DEFAULT_COUNTRY: Country = Country("US", "United States")

/**
 * The regions offered in the country picker — a broad subset of ITAD's supported `country` values,
 * sorted by display name. ITAD returns prices (and currency) for the chosen region.
 */
val SUPPORTED_COUNTRIES: List<Country> = listOf(
    Country("AR", "Argentina"),
    Country("AU", "Australia"),
    Country("AT", "Austria"),
    Country("BE", "Belgium"),
    Country("BR", "Brazil"),
    Country("CA", "Canada"),
    Country("CL", "Chile"),
    Country("CO", "Colombia"),
    Country("CZ", "Czechia"),
    Country("DK", "Denmark"),
    Country("FI", "Finland"),
    Country("FR", "France"),
    Country("DE", "Germany"),
    Country("GR", "Greece"),
    Country("HU", "Hungary"),
    Country("IN", "India"),
    Country("ID", "Indonesia"),
    Country("IE", "Ireland"),
    Country("IT", "Italy"),
    Country("JP", "Japan"),
    Country("MX", "Mexico"),
    Country("NL", "Netherlands"),
    Country("NZ", "New Zealand"),
    Country("NO", "Norway"),
    Country("PL", "Poland"),
    Country("PT", "Portugal"),
    Country("RO", "Romania"),
    Country("RU", "Russia"),
    Country("ZA", "South Africa"),
    Country("KR", "South Korea"),
    Country("ES", "Spain"),
    Country("SE", "Sweden"),
    Country("CH", "Switzerland"),
    Country("TW", "Taiwan"),
    Country("TH", "Thailand"),
    Country("TR", "Türkiye"),
    Country("UA", "Ukraine"),
    Country("GB", "United Kingdom"),
    DEFAULT_COUNTRY,
).sortedBy { it.name }
