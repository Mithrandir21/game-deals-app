package pm.bam.gamedeals.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.rememberNotificationPermissionGranted
import pm.bam.gamedeals.common.ui.platform.rememberNotificationPermissionRequester
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.feature.onboarding.generated.resources.Res
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_back
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_discover_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_discover_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_done
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_next
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_denied
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_enable
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_enabled
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_off
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_notifications_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_open_settings
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_page_indicator
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_region_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_region_change
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_region_picker_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_region_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_save_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_save_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_action
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_done_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_done_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_later
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_signed_in_as
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signin_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_signing_in
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_skip
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_account_desc
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_account_label
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_deals_desc
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_deals_label
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_giveaways_desc
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_giveaways_label
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_home_desc
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tab_home_label
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_tabs_title
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_welcome_body
import pm.bam.gamedeals.feature.onboarding.generated.resources.onboarding_welcome_title
import pm.bam.gamedeals.feature.onboarding.ui.OnboardingViewModel.OnboardingState

private const val ONBOARDING_PAGE_COUNT = 7
private const val ONBOARDING_SIGN_IN_PAGE = ONBOARDING_PAGE_COUNT - 1

/**
 * First-run welcome carousel (one-time, replayable from the Account hub). Explains the app and walks the
 * three interactive setup steps — region, notifications, sign-in. [onFinish] is invoked once the flow is
 * done (Skip, "Maybe later", or after the OAuth round-trip resolves); the completed flag is persisted by
 * the [OnboardingViewModel] before [onFinish] runs.
 */
@Composable
internal fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionRequester = rememberNotificationPermissionRequester()
    val permissionGranted = rememberNotificationPermissionGranted()
    val platformActions = LocalPlatformActions.current
    var notificationsDenied by remember { mutableStateOf(false) }

    OnboardingContent(
        state = state,
        countries = viewModel.countries,
        onCountrySelected = viewModel::onCountrySelected,
        notificationsPermissionGranted = permissionGranted,
        notificationsDenied = notificationsDenied,
        onEnableNotifications = {
            permissionRequester.request { granted ->
                notificationsDenied = !granted
                if (granted) viewModel.onNotificationsEnabled()
            }
        },
        onOpenNotificationSettings = { platformActions.openAppNotificationSettings() },
        onSignIn = { viewModel.signInThenFinish(onFinish) },
        onFinish = { viewModel.finish(onFinish) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingContent(
    state: OnboardingState,
    countries: ImmutableList<Country>,
    onCountrySelected: (Country) -> Unit,
    notificationsPermissionGranted: Boolean,
    notificationsDenied: Boolean,
    onEnableNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Skip is always available so a one-time flow is never a dead end.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinish) { Text(stringResource(Res.string.onboarding_skip)) }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> WelcomeSlide()
                    1 -> TabsSlide()
                    2 -> DiscoverSlide()
                    3 -> SaveSlide()
                    4 -> RegionSlide(
                        selected = state.selectedCountry,
                        countries = countries,
                        onCountrySelected = onCountrySelected,
                    )
                    5 -> NotificationsSlide(
                        enabled = state.notificationsEnabled,
                        permissionGranted = notificationsPermissionGranted,
                        denied = notificationsDenied,
                        onEnable = onEnableNotifications,
                        onOpenSettings = onOpenNotificationSettings,
                    )
                    6 -> SignInSlide(
                        loggedIn = state.loggedIn,
                        username = state.username,
                        signingIn = state.signingIn,
                        onSignIn = onSignIn,
                        onLater = onFinish,
                    )
                }
            }

            BottomControls(
                currentPage = pagerState.currentPage,
                pageCount = ONBOARDING_PAGE_COUNT,
                onBack = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            )
        }
    }
}

@Composable
private fun BottomControls(
    currentPage: Int,
    pageCount: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Back / Next keep a fixed footprint so the dots stay centred when a control is hidden.
        Box(modifier = Modifier.widthIn(min = 72.dp)) {
            if (currentPage > 0) {
                TextButton(onClick = onBack) { Text(stringResource(Res.string.onboarding_back)) }
            }
        }

        PageIndicator(pageCount = pageCount, currentPage = currentPage)

        Box(modifier = Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.CenterEnd) {
            // The sign-in page owns its own primary actions, so the generic Next is dropped there.
            if (currentPage < pageCount - 1) {
                TextButton(onClick = onNext) { Text(stringResource(Res.string.onboarding_next)) }
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    val label = stringResource(Res.string.onboarding_page_indicator, currentPage + 1, pageCount)
    Row(
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            Box(
                modifier = Modifier
                    .size(if (selected) 10.dp else 8.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Shared layout for a slide: a large decorative icon, a heading, a body, then any [content]. */
@Composable
private fun SlideScaffold(
    icon: ImageVector,
    title: StringResource,
    body: StringResource,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.medium))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        content()
    }
}

@Composable
private fun WelcomeSlide() = SlideScaffold(
    icon = Icons.Filled.LocalOffer,
    title = Res.string.onboarding_welcome_title,
    body = Res.string.onboarding_welcome_body,
)

@Composable
private fun TabsSlide() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.onboarding_tabs_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
        TabRow(Icons.Filled.Home, Res.string.onboarding_tab_home_label, Res.string.onboarding_tab_home_desc)
        TabRow(Icons.Filled.LocalOffer, Res.string.onboarding_tab_deals_label, Res.string.onboarding_tab_deals_desc)
        TabRow(Icons.Filled.CardGiftcard, Res.string.onboarding_tab_giveaways_label, Res.string.onboarding_tab_giveaways_desc)
        TabRow(Icons.Filled.AccountCircle, Res.string.onboarding_tab_account_label, Res.string.onboarding_tab_account_desc)
    }
}

@Composable
private fun TabRow(icon: ImageVector, label: StringResource, desc: StringResource) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GameDealsCustomTheme.spacing.medium)
            // Read the label + description as one focus stop ("Home, Your hub: …") rather than two.
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.large),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = stringResource(label), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoverSlide() = SlideScaffold(
    icon = Icons.Filled.FilterList,
    title = Res.string.onboarding_discover_title,
    body = Res.string.onboarding_discover_body,
)

@Composable
private fun SaveSlide() = SlideScaffold(
    icon = Icons.Filled.Bookmark,
    title = Res.string.onboarding_save_title,
    body = Res.string.onboarding_save_body,
)

@Composable
private fun RegionSlide(
    selected: Country?,
    countries: ImmutableList<Country>,
    onCountrySelected: (Country) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    SlideScaffold(
        icon = Icons.Filled.Public,
        title = Res.string.onboarding_region_title,
        body = Res.string.onboarding_region_body,
    ) {
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
        if (selected != null) {
            Text(
                text = "${flagEmoji(selected.code)}  ${selected.name}",
                style = MaterialTheme.typography.titleMedium,
                // The leading flag emoji would otherwise be read as a second country name.
                modifier = Modifier.clearAndSetSemantics { contentDescription = selected.name },
            )
            Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.medium))
        }
        OutlinedButton(onClick = { showPicker = true }) {
            Text(stringResource(Res.string.onboarding_region_change))
        }
    }

    if (showPicker) {
        OnboardingRegionPicker(
            countries = countries,
            selectedCode = selected?.code,
            onSelect = {
                onCountrySelected(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun NotificationsSlide(
    enabled: Boolean,
    permissionGranted: Boolean,
    denied: Boolean,
    onEnable: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    SlideScaffold(
        icon = Icons.Filled.Notifications,
        title = Res.string.onboarding_notifications_title,
        body = Res.string.onboarding_notifications_body,
    ) {
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
        // Alerts are genuinely active only when the opt-in is on AND the OS still permits posting; a
        // revoked permission must not read as "on".
        if (enabled && permissionGranted) {
            Row(
                // Announce the success the moment the toggle flips, since focus was on the now-gone button.
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.onboarding_notifications_enabled),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            // Surface the current OS-permission state up front, before any tap.
            if (!permissionGranted) {
                Text(
                    text = stringResource(
                        if (denied) Res.string.onboarding_notifications_denied else Res.string.onboarding_notifications_off
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (denied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    // Announce the state when it changes (e.g. a denial after tapping).
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.medium))
            }
            // Once blocked the in-app prompt won't reappear, so send the user straight to OS settings.
            // After they enable it there and return (permission now granted), fall back to the normal
            // enable button so a single tap turns the opt-in on.
            if (denied && !permissionGranted) {
                Button(onClick = onOpenSettings) {
                    Text(stringResource(Res.string.onboarding_open_settings))
                }
            } else {
                Button(onClick = onEnable) {
                    Text(stringResource(Res.string.onboarding_notifications_enable))
                }
            }
        }
    }
}

@Composable
private fun SignInSlide(
    loggedIn: Boolean,
    username: String,
    signingIn: Boolean,
    onSignIn: () -> Unit,
    onLater: () -> Unit,
) {
    SlideScaffold(
        icon = Icons.Filled.AccountCircle,
        title = if (loggedIn) Res.string.onboarding_signin_done_title else Res.string.onboarding_signin_title,
        body = if (loggedIn) Res.string.onboarding_signin_done_body else Res.string.onboarding_signin_body,
    ) {
        Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
        if (loggedIn) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.onboarding_signin_signed_in_as, username),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.large))
            Button(onClick = onLater) {
                Text(stringResource(Res.string.onboarding_done))
            }
        } else {
            // While signing in the button shows only a spinner, so give it an explicit name and announce
            // the transition — otherwise TalkBack lands on an unlabelled, disabled control.
            val signingInLabel = stringResource(Res.string.onboarding_signing_in)
            Button(
                onClick = onSignIn,
                enabled = !signingIn,
                modifier = if (signingIn) {
                    Modifier.semantics {
                        contentDescription = signingInLabel
                        liveRegion = LiveRegionMode.Polite
                    }
                } else {
                    Modifier
                },
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.onboarding_signin_action))
                }
            }
            Spacer(modifier = Modifier.size(GameDealsCustomTheme.spacing.small))
            TextButton(onClick = onLater, enabled = !signingIn) {
                Text(stringResource(Res.string.onboarding_signin_later))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingRegionPicker(
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
            text = stringResource(Res.string.onboarding_region_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.small)
                .semantics { heading() },
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(ordered, key = { it.code }) { country ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = country.code == selectedCode,
                            role = Role.RadioButton,
                            onClick = { onSelect(country) },
                        )
                        .padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                ) {
                    // Click + selection are owned by the row's `selectable`, so the RadioButton is a
                    // non-interactive visual; the flag emoji is decorative (the country name carries it).
                    RadioButton(selected = country.code == selectedCode, onClick = null)
                    Text(
                        text = flagEmoji(country.code),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                    Text(text = country.name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/**
 * The flag emoji for a 2-letter ISO country code (two Regional Indicator Symbols, each a UTF-16 surrogate
 * pair). Returns "" for anything that isn't two A–Z letters. Mirrors the account hub's picker helper.
 */
private fun flagEmoji(countryCode: String): String {
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
