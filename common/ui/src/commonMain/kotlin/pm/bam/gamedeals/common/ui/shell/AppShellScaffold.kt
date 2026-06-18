package pm.bam.gamedeals.common.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_account_unread
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_more_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_overflow_stores
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_close
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_placeholder
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_title
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * The Material3 [Scaffold] shell hosting the bottom [NavigationBar] and shared [TopAppBar].
 * Manages tab selection, search field expansion, and hide-on-scroll behavior.
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

    // Show search field if manually opened or if there's an active query on the Deals tab.
    var manualSearch by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    val isDealsTab = selectedTab == TopLevelDestination.DEALS
    val showSearchField = manualSearch || (isDealsTab && activeSearchQuery != null)

    // Sync field text with active query.
    LaunchedEffect(showSearchField, activeSearchQuery) {
        if (showSearchField) searchText = activeSearchQuery.orEmpty()
    }
    // Reset manual search state on tab change.
    LaunchedEffect(selectedTab) { manualSearch = false }

    // Manage hide-on-scroll behavior for both bars.
    val topScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    val density = LocalDensity.current
    val bottomBarHeightPx = with(density) { 80.dp.toPx() }
    var bottomBarOffsetHeightPx by remember { mutableStateOf(0f) }

    val nestedScrollConnection = remember(topScrollBehavior, bottomBarHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                bottomBarOffsetHeightPx = (bottomBarOffsetHeightPx + delta).coerceIn(-bottomBarHeightPx, 0f)
                return topScrollBehavior.nestedScrollConnection.onPreScroll(available, source)
            }
        }
    }

    // Reset bar visibility on tab change or visibility toggle.
    LaunchedEffect(selectedTab, showTopBar, showBottomBar) {
        topScrollBehavior.state.heightOffset = 0f
        bottomBarOffsetHeightPx = 0f
    }

    Scaffold(
        // Feed the tab content's nested scroll to the bars while shown.
        modifier = modifier.then(if (showTopBar || showBottomBar) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
        // Inner screens own their insets; the bars below consume their own via M3 defaults.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    scrollBehavior = topScrollBehavior,
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
                            Text(
                                modifier = Modifier.semantics { heading() },
                                text = stringResource(Res.string.app_shell_title),
                            )
                        }
                    },
                    actions = {
                        if (!showSearchField) {
                            IconButton(onClick = {
                                searchText = activeSearchQuery.orEmpty()
                                manualSearch = true
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.app_shell_search_action))
                            }
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
                NavigationBar(
                    modifier = Modifier.graphicsLayer {
                        translationY = -bottomBarOffsetHeightPx
                    }
                ) {
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
        // Content spacing on tab routes; detail routes own their own padding.
        // Ignore bottom padding to avoid the gap when the bottom bar hides.
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = if (showTopBar) {
            PaddingValues(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
                bottom = 0.dp,
            )
        } else {
            padding
        }
        content(contentPadding)
    }
}

/**
 * Inline toolbar search field. Submitting navigates to Deals; closing clears the search.
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

@Preview
@Composable
private fun GameDealsAppShell_Preview() {
    GameDealsTheme {
        GameDealsAppShell(
            selectedTab = TopLevelDestination.HOME,
            showTopBar = true,
            showBottomBar = true,
            onSelectTab = {},
            activeSearchQuery = null,
            onSearchSubmit = {},
            onSearchClosed = {},
            content = { padding ->
                Text("Content", modifier = Modifier.padding(padding))
            }
        )
    }
}
