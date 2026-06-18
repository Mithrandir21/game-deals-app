package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.home.StatCard
import pm.bam.gamedeals.common.ui.platform.rememberNotificationPermissionRequester
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_linked_steam_connected
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_action
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_body
import pm.bam.gamedeals.feature.account.generated.resources.account_reconnect_title
import pm.bam.gamedeals.feature.account.generated.resources.account_row_ignored
import pm.bam.gamedeals.feature.account.generated.resources.account_row_linked
import pm.bam.gamedeals.feature.account.generated.resources.account_notification_permission_denied
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notes
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notification_delivery
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notification_delivery_desc
import pm.bam.gamedeals.feature.account.generated.resources.account_row_mature
import pm.bam.gamedeals.feature.account.generated.resources.account_row_mature_desc
import pm.bam.gamedeals.feature.account.generated.resources.account_row_mature_switch_description
import pm.bam.gamedeals.feature.account.generated.resources.account_row_notifications
import pm.bam.gamedeals.feature.account.generated.resources.account_row_region
import pm.bam.gamedeals.feature.account.generated.resources.account_row_website_settings
import pm.bam.gamedeals.feature.account.generated.resources.account_row_website_settings_desc
import pm.bam.gamedeals.feature.account.generated.resources.account_section_app
import pm.bam.gamedeals.feature.account.generated.resources.account_section_connections
import pm.bam.gamedeals.feature.account.generated.resources.account_section_discovery
import pm.bam.gamedeals.feature.account.generated.resources.account_section_library
import pm.bam.gamedeals.feature.account.generated.resources.account_section_website
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_in
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_out
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_in_as
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_body
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_title
import pm.bam.gamedeals.feature.account.generated.resources.account_stat_collected
import pm.bam.gamedeals.feature.account.generated.resources.account_stat_waitlisted
import pm.bam.gamedeals.feature.account.ui.AccountViewModel.AccountScreenData

/** The ITAD website settings page — for the shop/persona/notification-rule options the API can't reach (#276). */
private const val ITAD_SETTINGS_URL = "https://isthereanydeal.com/settings/"

@Composable
internal fun AccountScreen(
    onOpenWaitlist: () -> Unit = {},
    onOpenCollection: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenIgnored: () -> Unit = {},
    onOpenMyNotes: () -> Unit = {},
    onOpenLinkedAccounts: () -> Unit = {},
    onOpenWebsite: (url: String) -> Unit = {},
    viewModel: AccountViewModel = koinViewModel(),
) {
    val data by viewModel.uiState.collectAsStateWithLifecycle()
    AccountScreenContent(
        data = data,
        countries = viewModel.countries,
        onLogin = viewModel::onLogin,
        onLogout = viewModel::onLogout,
        onCountrySelected = viewModel::onCountrySelected,
        onSetMature = viewModel::onSetMatureOptIn,
        onOpenWaitlist = onOpenWaitlist,
        onOpenCollection = onOpenCollection,
        onOpenNotifications = onOpenNotifications,
        onOpenIgnored = onOpenIgnored,
        onOpenMyNotes = onOpenMyNotes,
        onOpenLinkedAccounts = onOpenLinkedAccounts,
        onOpenWebsite = onOpenWebsite,
    )
}

@Composable
private fun AccountScreenContent(
    data: AccountScreenData,
    countries: ImmutableList<Country>,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSetMature: (Boolean) -> Unit,
    onOpenWaitlist: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenIgnored: () -> Unit,
    onOpenMyNotes: () -> Unit,
    onOpenLinkedAccounts: () -> Unit,
    onOpenWebsite: (url: String) -> Unit,
) {
    var showRegionPicker by rememberSaveable { mutableStateOf(false) }
    val onOpenRegion = { showRegionPicker = true }
    val regionName = data.selectedCountry?.name

    if (!data.loggedIn) {
        LoggedOutContent(
            loggingIn = data.loggingIn,
            onLogin = onLogin,
            regionName = regionName,
            onOpenRegion = onOpenRegion,
            matureOptIn = data.matureOptIn,
            onSetMature = onSetMature,
        )
    } else {
        LoggedInContent(
            data = data,
            onLogout = onLogout,
            // Reconnect re-runs the same OAuth flow as a fresh sign-in; on success the token is
            // re-stamped with the current scope version and the banner disappears.
            onReconnect = onLogin,
            onOpenWaitlist = onOpenWaitlist,
            onOpenCollection = onOpenCollection,
            onOpenNotifications = onOpenNotifications,
            onOpenIgnored = onOpenIgnored,
            onOpenMyNotes = onOpenMyNotes,
            onOpenLinkedAccounts = onOpenLinkedAccounts,
            onOpenRegion = onOpenRegion,
            regionName = regionName,
            matureOptIn = data.matureOptIn,
            onSetMature = onSetMature,
            onOpenWebsite = { onOpenWebsite(ITAD_SETTINGS_URL) },
        )
    }

    if (showRegionPicker) {
        RegionPickerSheet(
            countries = countries,
            selectedCode = data.selectedCountry?.code,
            onSelect = { onCountrySelected(it); showRegionPicker = false },
            onDismiss = { showRegionPicker = false },
        )
    }
}

@Composable
private fun LoggedOutContent(
    loggingIn: Boolean,
    onLogin: () -> Unit,
    regionName: String?,
    onOpenRegion: () -> Unit,
    matureOptIn: Boolean,
    onSetMature: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item { SignInCard(loggingIn = loggingIn, onLogin = onLogin) }
        // App-level preferences are reachable without signing in.
        item { SectionHeader(stringResource(Res.string.account_section_app)) }
        item { HubRow(label = stringResource(Res.string.account_row_region), subtitle = regionName, onClick = onOpenRegion) }
        item { MatureContentRow(checked = matureOptIn, onCheckedChange = onSetMature) }
    }
}

@Composable
private fun SignInCard(loggingIn: Boolean, onLogin: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large)) {
            Text(
                text = stringResource(Res.string.account_signed_out_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.account_signed_out_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = GameDealsCustomTheme.spacing.medium),
            )
            if (loggingIn) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onLogin, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(Res.string.account_sign_in))
                }
            }
        }
    }
}

@Composable
private fun LoggedInContent(
    data: AccountScreenData,
    onLogout: () -> Unit,
    onReconnect: () -> Unit,
    onOpenWaitlist: () -> Unit,
    onOpenCollection: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenIgnored: () -> Unit,
    onOpenMyNotes: () -> Unit,
    onOpenLinkedAccounts: () -> Unit,
    onOpenRegion: () -> Unit,
    regionName: String?,
    matureOptIn: Boolean,
    onSetMature: (Boolean) -> Unit,
    onOpenWebsite: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(GameDealsCustomTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
    ) {
        item { ProfileHeader(username = data.username, onLogout = onLogout) }

        if (data.needsReconnect) {
            item { ReconnectBanner(loggingIn = data.loggingIn, onReconnect = onReconnect) }
        }

        item { SectionHeader(stringResource(Res.string.account_section_library)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium)) {
                StatCard(
                    label = stringResource(Res.string.account_stat_waitlisted),
                    value = data.waitlistCount.toString(),
                    icon = Icons.Filled.Bookmark,
                    onClick = onOpenWaitlist,
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(Res.string.account_stat_collected),
                    value = data.collectionCount.toString(),
                    icon = Icons.Filled.LibraryAddCheck,
                    onClick = onOpenCollection,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { SectionHeader(stringResource(Res.string.account_section_discovery)) }
        item { HubRow(label = stringResource(Res.string.account_row_notifications), badgeCount = data.unreadNotifications, onClick = onOpenNotifications) }
        item { NotificationDeliveryRow() }
        item { HubRow(label = stringResource(Res.string.account_row_ignored), onClick = onOpenIgnored) }
        item { HubRow(label = stringResource(Res.string.account_row_notes), onClick = onOpenMyNotes) }

        item { SectionHeader(stringResource(Res.string.account_section_connections)) }
        item {
            HubRow(
                label = stringResource(Res.string.account_row_linked),
                subtitle = if (data.linkedSteam) stringResource(Res.string.account_linked_steam_connected) else null,
                onClick = onOpenLinkedAccounts,
            )
        }

        item { SectionHeader(stringResource(Res.string.account_section_app)) }
        item { HubRow(label = stringResource(Res.string.account_row_region), subtitle = regionName, onClick = onOpenRegion) }
        item { MatureContentRow(checked = matureOptIn, onCheckedChange = onSetMature) }

        item { SectionHeader(stringResource(Res.string.account_section_website)) }
        item {
            HubRow(
                label = stringResource(Res.string.account_row_website_settings),
                subtitle = stringResource(Res.string.account_row_website_settings_desc),
                onClick = onOpenWebsite,
            )
        }
    }
}

@Composable
private fun ProfileHeader(username: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(Res.string.account_signed_in_as, username),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = GameDealsCustomTheme.spacing.medium)
                .semantics { heading() },
        )
        OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.account_sign_out)) }
    }
}

@Composable
private fun ReconnectBanner(loggingIn: Boolean, onReconnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(GameDealsCustomTheme.spacing.large)) {
            Text(
                text = stringResource(Res.string.account_reconnect_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.account_reconnect_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = GameDealsCustomTheme.spacing.extraSmall),
            )
            Button(
                onClick = onReconnect,
                enabled = !loggingIn,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = GameDealsCustomTheme.spacing.medium),
            ) {
                if (loggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(Res.string.account_reconnect_action))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(top = GameDealsCustomTheme.spacing.small)
            .semantics { heading() },
    )
}

/**
 * Background-alerts opt-in toggle (background-notifications feature, Phase D). Self-contained: pulls its
 * own [NotificationSettingsViewModel] + the platform permission requester. Flipping on requests the OS
 * notification permission and only enables on grant; a denial leaves it off with an inline rationale.
 */
@Composable
private fun NotificationDeliveryRow(
    viewModel: NotificationSettingsViewModel = koinViewModel(),
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val permissionRequester = rememberNotificationPermissionRequester()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(Res.string.account_row_notification_delivery)) },
        supportingContent = {
            Text(
                if (permissionDenied) {
                    stringResource(Res.string.account_notification_permission_denied)
                } else {
                    stringResource(Res.string.account_row_notification_delivery_desc)
                }
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        permissionRequester.request { granted ->
                            permissionDenied = !granted
                            if (granted) viewModel.onEnable()
                        }
                    } else {
                        permissionDenied = false
                        viewModel.onDisable()
                    }
                },
            )
        },
    )
}

/**
 * The single app-wide "show adult titles" opt-in (moved out of the Deals & Bundles filters). Flipping it
 * persists via [AccountViewModel.onSetMatureOptIn]; both lists react to the shared preference.
 */
@Composable
private fun MatureContentRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val switchCd = stringResource(Res.string.account_row_mature_switch_description)
    ListItem(
        headlineContent = { Text(stringResource(Res.string.account_row_mature)) },
        supportingContent = { Text(stringResource(Res.string.account_row_mature_desc)) },
        trailingContent = {
            Switch(
                modifier = Modifier.semantics { contentDescription = switchCd },
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun HubRow(
    label: String,
    subtitle: String? = null,
    badgeCount: Int = 0,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        headlineContent = { Text(label) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = if (badgeCount > 0) {
            { Badge { Text(badgeCount.toString()) } }
        } else {
            null
        },
    )
}

private val previewCountries = persistentListOf(Country("US", "United States"), Country("GB", "United Kingdom"))

@Composable
private fun AccountScreenContentPreview(data: AccountScreenData) {
    GameDealsTheme {
        AccountScreenContent(
            data = data,
            countries = previewCountries,
            onLogin = {},
            onLogout = {},
            onCountrySelected = {},
            onSetMature = {},
            onOpenWaitlist = {},
            onOpenCollection = {},
            onOpenNotifications = {},
            onOpenIgnored = {},
            onOpenMyNotes = {},
            onOpenLinkedAccounts = {},
            onOpenWebsite = {},
        )
    }
}

@Preview
@Composable
private fun AccountScreen_LoggedOut_Preview() {
    AccountScreenContentPreview(
        AccountScreenData(loggedIn = false, selectedCountry = Country("US", "United States")),
    )
}

@Preview
@Composable
private fun AccountScreen_LoggedIn_Preview() {
    AccountScreenContentPreview(
        AccountScreenData(
            loggedIn = true,
            username = "alice",
            waitlistCount = 12,
            collectionCount = 34,
            unreadNotifications = 3,
            linkedSteam = true,
            selectedCountry = Country("US", "United States"),
            matureOptIn = true,
        ),
    )
}
