package pm.bam.gamedeals.common.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_more_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_overflow_settings
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_overflow_stores
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_search_action
import pm.bam.gamedeals.common.ui.generated.resources.app_shell_title

/**
 * The app shell (epic #219): a Material3 [Scaffold] hosting the bottom [NavigationBar] (the four
 * [TopLevelDestination] tabs) and a [TopAppBar] (Search action + overflow), wrapping the per-tab
 * [content].
 *
 * Deliberately navigation-agnostic so it lives in `:common:ui` commonMain (navigation-compose is not a
 * common dependency): the hosting NavHost computes [selectedTab] from the current route and toggles
 * [showTopBar] / [showBottomBar]:
 * - top-level tab routes → both bars (so re-selecting a tab is handled by the host's `navigateTopLevel`);
 * - detail routes → neither bar (the detail screen owns its own `Scaffold`/`TopAppBar`).
 *
 * [contentWindowInsets] is zeroed so inner screens (which each have their own `Scaffold`) manage their
 * own system-bar insets; the shell's own [TopAppBar]/[NavigationBar] consume their insets via M3 defaults.
 *
 * Phase 1 interim: the Giveaways tab keeps its existing top bar, so the host passes `showTopBar = false`
 * there (bottom bar still shown). Unifying Giveaways into the shell top bar is a tracked follow-up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDealsAppShell(
    selectedTab: TopLevelDestination?,
    showTopBar: Boolean,
    showBottomBar: Boolean,
    onSelectTab: (TopLevelDestination) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseStores: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        // Inner screens own their insets; the bars below consume their own via M3 defaults.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.app_shell_title)) },
                    actions = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(Res.string.app_shell_search_action))
                        }
                        IconButton(onClick = { overflowExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(Res.string.app_shell_more_action))
                        }
                        DropdownMenu(
                            expanded = overflowExpanded,
                            onDismissRequest = { overflowExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.app_shell_overflow_settings)) },
                                onClick = {
                                    overflowExpanded = false
                                    onOpenSettings()
                                },
                            )
                            if (onBrowseStores != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.app_shell_overflow_stores)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onBrowseStores()
                                    },
                                )
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
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { onSelectTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        content(padding)
    }
}
