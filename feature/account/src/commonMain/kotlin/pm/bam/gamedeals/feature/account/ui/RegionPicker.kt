package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.countriesByRegion
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_no_matches
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_search_hint
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_title

/**
 * Bottom-sheet region picker (#276): countries are grouped into continent sections (Africa, Americas, …)
 * with sticky headers, each row shown with its flag emoji (derived from the ISO code). A search field
 * filters by name/code. Selecting a country persists the region and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionPickerSheet(
    countries: ImmutableList<Country>,
    selectedCode: String?,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val grouped = remember(countries, query) {
        val filtered = countries.filter { it.matches(query) }
        countriesByRegion(filtered)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            text = stringResource(Res.string.account_region_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
                .semantics { heading() },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(Res.string.account_region_picker_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            if (grouped.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.account_region_picker_no_matches),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(
                            horizontal = GameDealsCustomTheme.spacing.large,
                            vertical = GameDealsCustomTheme.spacing.medium,
                        ),
                    )
                }
            }
            grouped.forEach { (region, entries) ->
                item(key = "header_${region.name}") { RegionHeader(region.displayName) }
                items(entries, key = { it.code }) { country ->
                    CountryRow(
                        country = country,
                        selected = country.code == selectedCode,
                        onClick = { onSelect(country) },
                    )
                }
            }
        }
    }
}

private fun Country.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return name.contains(q, ignoreCase = true) || code.contains(q, ignoreCase = true)
}

@Composable
private fun RegionHeader(title: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
                .semantics { heading() },
        )
    }
}

@Composable
private fun CountryRow(country: Country, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        // Click + selection state are owned by the row's `selectable`, so the RadioButton is a
        // non-interactive visual (onClick = null) — the standard Material a11y pattern.
        RadioButton(selected = selected, onClick = null)
        Text(text = flagEmoji(country.code), style = MaterialTheme.typography.titleMedium)
        Text(text = country.name, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The flag emoji for a 2-letter ISO country code, formed from the two Regional Indicator Symbols
 * (U+1F1E6..U+1F1FF). Each is above the BMP, so it's emitted as a UTF-16 surrogate pair (no
 * `Character`/`appendCodePoint` in KMP common). Returns "" for anything that isn't two A–Z letters.
 */
internal fun flagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val a = countryCode[0].uppercaseChar()
    val b = countryCode[1].uppercaseChar()
    if (a !in 'A'..'Z' || b !in 'A'..'Z') return ""
    return regionalIndicator(a) + regionalIndicator(b)
}

private fun regionalIndicator(letter: Char): String {
    val codePoint = 0x1F1E6 + (letter - 'A')
    val offset = codePoint - 0x10000
    val high = (0xD800 + (offset shr 10)).toChar()
    val low = (0xDC00 + (offset and 0x3FF)).toChar()
    return charArrayOf(high, low).concatToString()
}
