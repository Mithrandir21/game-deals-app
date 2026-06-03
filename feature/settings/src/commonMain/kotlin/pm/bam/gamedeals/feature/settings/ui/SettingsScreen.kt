package pm.bam.gamedeals.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.feature.settings.generated.resources.Res
import pm.bam.gamedeals.feature.settings.generated.resources.settings_screen_navigation_back_button
import pm.bam.gamedeals.feature.settings.generated.resources.settings_screen_region_description
import pm.bam.gamedeals.feature.settings.generated.resources.settings_screen_region_section
import pm.bam.gamedeals.feature.settings.generated.resources.settings_screen_title

@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val selectedCode by viewModel.selectedCountryCode.collectAsStateWithLifecycle()
    SettingsScreenContent(
        countries = viewModel.countries,
        selectedCode = selectedCode,
        onBack = onBack,
        onCountrySelected = viewModel::onCountrySelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    countries: ImmutableList<Country>,
    selectedCode: String?,
    onBack: () -> Unit,
    onCountrySelected: (Country) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.settings_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.settings_screen_navigation_back_button),
                            )
                        }
                    },
                )
            },
        ) { innerPadding: PaddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = GameDealsCustomTheme.spacing.large,
                                end = GameDealsCustomTheme.spacing.large,
                                top = GameDealsCustomTheme.spacing.large,
                                bottom = GameDealsCustomTheme.spacing.small,
                            ),
                    ) {
                        Text(
                            modifier = Modifier.semantics { heading() },
                            text = stringResource(Res.string.settings_screen_region_section),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.extraSmall),
                            text = stringResource(Res.string.settings_screen_region_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(countries, key = { it.code }) { country ->
                    CountryRow(
                        country = country,
                        selected = country.code == selectedCode,
                        onClick = { onCountrySelected(country) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: Country,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(
                horizontal = GameDealsCustomTheme.spacing.large,
                vertical = GameDealsCustomTheme.spacing.medium,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Click + selection state are owned by the row's `selectable`, so the RadioButton itself is
        // a non-interactive visual (onClick = null) — the standard Material a11y pattern.
        RadioButton(selected = selected, onClick = null)
        Text(
            modifier = Modifier.padding(start = GameDealsCustomTheme.spacing.medium),
            text = country.name,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    GameDealsTheme {
        SettingsScreenContent(
            countries = persistentListOf(
                Country("CA", "Canada"),
                Country("DE", "Germany"),
                Country("GB", "United Kingdom"),
                Country("US", "United States"),
            ),
            selectedCode = "GB",
            onBack = {},
            onCountrySelected = {},
        )
    }
}
