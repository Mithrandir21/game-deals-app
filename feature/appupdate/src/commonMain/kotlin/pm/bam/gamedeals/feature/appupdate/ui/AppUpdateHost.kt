package pm.bam.gamedeals.feature.appupdate.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.feature.appupdate.generated.resources.Res
import pm.bam.gamedeals.feature.appupdate.generated.resources.app_update_action_later
import pm.bam.gamedeals.feature.appupdate.generated.resources.app_update_action_update
import pm.bam.gamedeals.feature.appupdate.generated.resources.app_update_body_blocking
import pm.bam.gamedeals.feature.appupdate.generated.resources.app_update_body_optional
import pm.bam.gamedeals.feature.appupdate.generated.resources.app_update_title

/**
 * Shell-level minimum-version gate: shows a single dialog when the running build is below the floor published
 * remotely. Mirrors `SignInPromptHost`'s shape — resolve a ViewModel, collect one state, render nothing when
 * there is nothing to say.
 *
 * Placed *above* the nav host rather than inside it (unlike `SignInPromptHost`, which is triggered by in-app
 * actions that only exist after onboarding): this gate has to cover every route including the onboarding
 * carousel, so it must not depend on navigation having resolved.
 */
@Composable
fun AppUpdateHost() {
    val viewModel: AppUpdateViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val platformActions = LocalPlatformActions.current

    val prompt = state as? AppUpdateState.Prompt ?: return

    LaunchedEffect(prompt) { viewModel.onPromptShown(prompt) }

    AppUpdateDialog(
        blocking = prompt.blocking,
        onUpdate = {
            viewModel.onUpdateTapped(prompt)
            platformActions.openStoreListing(viewModel.storeId)
        },
        onDismiss = { viewModel.onDismissed(prompt) },
    )
}

@Composable
internal fun AppUpdateDialog(
    blocking: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        // A blocking gate must survive both the back press and an outside tap, so it swallows the dismiss
        // request entirely rather than relying on the caller to ignore it. This is also why the gate is an
        // AlertDialog and not the ModalBottomSheet used elsewhere in the app: a sheet is always swipe-dismissible.
        onDismissRequest = { if (!blocking) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !blocking,
            dismissOnClickOutside = !blocking,
        ),
        title = { Text(stringResource(Res.string.app_update_title)) },
        text = {
            Text(
                stringResource(
                    if (blocking) Res.string.app_update_body_blocking else Res.string.app_update_body_optional
                )
            )
        },
        confirmButton = {
            // Deliberately does not dismiss: the user comes back to the prompt until they have actually
            // updated, and on the blocking variant there is nothing to dismiss to.
            TextButton(onClick = onUpdate) { Text(stringResource(Res.string.app_update_action_update)) }
        },
        dismissButton = if (blocking) {
            null
        } else {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.app_update_action_later)) } }
        },
    )
}
