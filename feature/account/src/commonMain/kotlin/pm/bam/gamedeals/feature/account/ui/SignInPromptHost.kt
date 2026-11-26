package pm.bam.gamedeals.feature.account.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.common.navigation.SignInPromptController
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.account.generated.resources.Res
import pm.bam.gamedeals.feature.account.generated.resources.account_sign_in
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_body
import pm.bam.gamedeals.feature.account.generated.resources.account_signed_out_title
import pm.bam.gamedeals.feature.account.generated.resources.account_signin_prompt_dismiss

/**
 * Shell-level sign-in prompt: a single bottom sheet shown whenever a logged-out user taps a gated action
 * (waitlist/collection/ignore/note), routed here via [SignInPromptController]. "Sign in" launches the ITAD
 * OAuth flow in place; once it resolves the sheet dismisses (success updates the app's auth state, so the
 * action becomes usable on the next tap). Rendered once by the app shell.
 */
@Composable
fun SignInPromptHost() {
    val viewModel: SignInPromptViewModel = koinViewModel()
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        SignInPromptController.requests.collect { visible = true }
    }

    if (visible) {
        val signingIn by viewModel.signingIn.collectAsStateWithLifecycle()
        SignInPromptSheet(
            signingIn = signingIn,
            onSignIn = { viewModel.login { visible = false } },
            onDismiss = { visible = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignInPromptSheet(
    signingIn: Boolean,
    onSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = GameDealsCustomTheme.spacing.large,
                    vertical = GameDealsCustomTheme.spacing.medium,
                ),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Text(
                text = stringResource(Res.string.account_signed_out_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(Res.string.account_signed_out_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onSignIn,
                enabled = !signingIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (signingIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(Res.string.account_sign_in))
                }
            }
            TextButton(
                onClick = onDismiss,
                enabled = !signingIn,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(Res.string.account_signin_prompt_dismiss))
            }
        }
    }
}
