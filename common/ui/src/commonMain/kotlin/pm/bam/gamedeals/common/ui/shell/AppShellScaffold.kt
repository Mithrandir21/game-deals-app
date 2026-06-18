package pm.bam.gamedeals.common.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_account_unread
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_more_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_overflow_stores
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_close
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_placeholder
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_title
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme

/**
 * The app shell (epic #219): a Material3 [Scaffold] hosting the bottom [NavigationBar] (the four
 * [TopLevelDestination] tabs) and a shared [TopAppBar] (an expandable search field + overflow), wrapping
 * the per-tab [content].
 *
 * Deliberately navigation-agnostic so it lives in `:common:ui` commonMain (navigation-compose is not a
 * common dependency): the hosting NavHost computes [selectedTab] from the current route and toggles
 * [showTopBar] / [showBottomBar]:
 * - top-level tab routes → both bars (so re-selecting a tab is handled by the host's `navigateTopLevel`);
 * - detail routes → neither bar (the detail screen owns its own `Scaffold`/`TopAppBar`).
 *
 * Search lives in the top bar: the Search icon expands an in-bar field; submitting fires [onSearchSubmit]
 * (the host navigates to the Deals tab and runs the search), closing it fires [onSearchClosed]. Once a
 * search is active ([activeSearchQuery] non-null) the field stays shown — and focused — only on the Deals
 * tab; every other tab falls back to the default title + Search icon.
 *
 * [contentWindowInsets] is zeroed so inner screens (which each have their own `Scaffold`) manage their
 * own system-bar insets; the shell's own [TopAppBar]/[NavigationBar] consume their insets via M3 defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDealsAppShell(
    selectedTab: TopLevelDestination?,
    showTopBar: Boolean,
    showBottomBar: Boolean,
    onSelectTab: (TopLevelDestination) -> Unit,
    activeSearchQuery: String?,
    onSearchSubmit: (String) -> Unit,
    onSearchClosed: () -> Unit,
    onBrowseStores: (() -> Unit)? = null,
    accountUnreadCount: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    // The search field shows while the user is opening one (manualSearch) OR whenever there's an active
    // search and we're on the Deals tab — so an actioned search stays visible+focused on Deals, while
    // every other screen falls back to the default toolbar. searchText holds the editable text.
    var manualSearch by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val isDealsTab = selectedTab == TopLevelDestination.DEALS
    val showSearchField = manualSearch || (isDealsTab && activeSearchQuery != null)
    // Seed the field from the active query whenever it's shown for an active search (so re-entering Deals
    // shows the query); the user's own typing survives because this only re-runs when those two change.
    LaunchedEffect(showSearchField, activeSearchQuery) {
        if (showSearchField) searchText = activeSearchQuery.orEmpty()
    }
    // A manual (not-yet-submitted) open shouldn't bleed across tabs; drop it whenever the tab changes.
    LaunchedEffect(selectedTab) { manualSearch = false }

    // The shared top bar scrolls away as the tab content scrolls down and re-enters immediately on any
    // upward scroll (Material3 "enter always"). Snap it back to fully shown whenever the tab changes or we
    // return to a tab route, so a new screen never starts with a half-hidden bar.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    LaunchedEffect(selectedTab, showTopBar) { scrollBehavior.state.heightOffset = 0f }

    Scaffold(
        // Feed the tab content's nested scroll to the top bar only while it's shown (detail routes have no
        // shell top bar). Inner screens' LazyColumns propagate their scroll up to this connection.
        modifier = if (showTopBar) modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else modifier,
        // Inner screens own their insets; the bars below consume their own via M3 defaults.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        if (showSearchField) {
                            ToolbarSearchField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                onSubmit = {
                                    if (searchText.isNotBlank()) {
                                        // Action the search; on Deals the field stays shown (active +
                                        // Deals), elsewhere it collapses back to the default toolbar.
                                        onSearchSubmit(searchText)
                                        manualSearch = false
                                    }
                                },
                                onClose = {
                                    manualSearch = false
                                    searchText = ""
                                    onSearchClosed()
                                },
                            )
                        } else {
                            // A single constant title — the bottom nav already names the current tab,
                            // so the top bar doesn't duplicate it.
                            Text(
                                modifier = Modifier.semantics { heading() },
                                text = stringResource(Res.string.app_shell_title),
                            )
                        }
                    },
                    actions = {
                        // While the field is shown (with its own close affordance) it fills the bar.
                        if (!showSearchField) {
                            IconButton(onClick = {
                                // Pre-fill with the active search (if any) so re-opening lets the user refine it.
                                searchText = activeSearchQuery.orEmpty()
                                manualSearch = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.app_shell_search_action))
                            }
                            // The overflow only appears when there's something in it (Settings folded into the
                            // Account hub in #276; "Browse by store" is the remaining — currently unused — entry).
                            if (onBrowseStores != null) {
                                IconButton(onClick = { overflowExpanded = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.app_shell_more_action))
                                }
                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(Res.string.app_shell_overflow_stores)) },
                                        onClick = {
                                            overflowExpanded = false
                                            onBrowseStores()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { tab ->
                        val showAccountBadge = tab == TopLevelDestination.ACCOUNT && accountUnreadCount > 0
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            icon = {
                                if (showAccountBadge) {
                                    val badgeCd = stringResource(Res.string.app_shell_account_unread, accountUnreadCount)
                                    BadgedBox(
                                        badge = { Badge { Text(accountUnreadCount.toString()) } },
                                        modifier = Modifier.semantics { contentDescription = badgeCd },
                                    ) {
                                        Icon(tab.icon, contentDescription = null)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = null)
                                }
                            },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // A little breathing room between the shared top bar and the tab content below it (only
        // on tab routes; detail routes have no shell top bar and own their own spacing).
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = if (showTopBar) {
            PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = padding.calculateBottomPadding(),
            )
        } else {
            padding
        }
        content(contentPadding)
    }
}

/**
 * The in-toolbar search field (submit-only): typing edits [value]; pressing the keyboard Search action
 * fires [onSubmit] (the host then navigates to the Deals tab and runs the search); the trailing close
 * button fires [onClose]. Styled to sit transparently on the top bar's `primaryContainer` background.
 */
@Composable
private fun ToolbarSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Focus + raise the keyboard as soon as the field is revealed.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TextField(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(stringResource(Res.string.app_shell_search_placeholder)) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.app_shell_search_close))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboardController?.hide()
            onSubmit()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.onPrimaryContainer,
            focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
            unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
