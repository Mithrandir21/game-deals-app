package pm.bam.gamedeals.feature.debug.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.debug.generated.resources.Res
import pm.bam.gamedeals.feature.debug.generated.resources.debug_navigation_back
import pm.bam.gamedeals.feature.debug.generated.resources.debug_intro
import pm.bam.gamedeals.feature.debug.generated.resources.debug_section_app_update
import pm.bam.gamedeals.feature.debug.generated.resources.debug_section_winter_sale
import pm.bam.gamedeals.feature.debug.generated.resources.debug_title

/**
 * Developer tools hub, reached from the Account hub in debug builds.
 *
 * This is a container, deliberately thin: each tool is a self-contained composable that resolves its own
 * dependencies from Koin and owns its own state, so adding one means writing a section and dropping it into
 * the [Column] below — no plumbing through this screen's signature, no shared ViewModel to grow.
 *
 * Nothing here ships. The Account hub entry point is gated on `AppInfo.isDebug`, and each section should be
 * safe by construction rather than relying on that alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugScreen(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = { Text(stringResource(Res.string.debug_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.debug_navigation_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.small),
            ) {
                Text(
                    text = stringResource(Res.string.debug_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = GameDealsCustomTheme.spacing.large,
                        vertical = GameDealsCustomTheme.spacing.small,
                    ),
                )

                // --- Tools. Add new ones here; each owns its own state and dependencies. ---
                DebugSectionHeader(stringResource(Res.string.debug_section_app_update))
                ForceUpdateOverrideSection()

                HorizontalDivider()

                DebugSectionHeader(stringResource(Res.string.debug_section_winter_sale))
                WinterSaleTestSection()

                HorizontalDivider()
            }
        }
    }
}

@Composable
internal fun DebugSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(
                start = GameDealsCustomTheme.spacing.large,
                end = GameDealsCustomTheme.spacing.large,
                top = GameDealsCustomTheme.spacing.small,
            )
            .semantics { heading() },
    )
}
