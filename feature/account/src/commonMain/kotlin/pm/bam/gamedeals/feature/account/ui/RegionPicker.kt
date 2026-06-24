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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_region_picker_title

/**
 * Bottom-sheet region picker (#276): the currently-selected country is pinned to the top, the rest follow
 * alphabetically, each shown with its flag emoji (derived from the ISO code). Selecting one persists the
 * region and dismisses the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionPickerSheet(
    countries: ImmutableList<Country>,
    selectedCode: String?,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val ordered = remember(countries, selectedCode) {
        val selected = countries.firstOrNull { it.code == selectedCode }
        val rest = countries.filter { it.code != selectedCode }.sortedBy { it.name }
        listOfNotNull(selected) + rest
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
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(ordered, key = { it.code }) { country ->
                CountryRow(
                    country = country,
                    selected = country.code == selectedCode,
                    onClick = { onSelect(country) },
                )
            }
        }
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
