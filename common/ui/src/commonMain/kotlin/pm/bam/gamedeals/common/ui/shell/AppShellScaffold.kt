package pm.bam.gamedeals.common.ui.shell

import androidx.compose.foundation.layout.PaddingValues
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
 * The app shell (epic #219): a Material3 [Scaffold] with a bottom [NavigationBar] (the four
 * [TopLevelDestination] tabs), a [TopAppBar] carrying a Search action and an overflow menu
 * (Settings, Browse by store), wrapping the per-tab [content].
 *
 * Deliberately navigation-agnostic so it can live in `:common:ui` commonMain (navigation-compose is
 * not a common dependency). The hosting NavHost passes [selectedTab] (derived from the current route)
 * plus the tab/search/overflow callbacks. NOT yet wired into either platform's NavHost — that is
 * Phase 1.1 (#224); this is the scaffold the host will adopt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDealsAppShell(
    selectedTab: TopLevelDestination?,
    onSelectTab: (TopLevelDestination) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseStores: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.app_shell_overflow_stores)) },
                            onClick = {
                                overflowExpanded = false
                                onBrowseStores()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
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
        },
    ) { padding ->
        content(padding)
    }
}
