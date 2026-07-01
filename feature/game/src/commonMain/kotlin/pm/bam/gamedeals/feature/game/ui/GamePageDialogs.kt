package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.theme.GameDealsCustomTheme
import pm.bam.gamedeals.feature.game.generated.resources.Res
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_close
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_empty
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_error
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_explanation
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_loading_cd
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_retry
import pm.bam.gamedeals.feature.game.generated.resources.game_details_title_match_picker_title
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_cancel
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_label
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_placeholder
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_save
import pm.bam.gamedeals.feature.game.generated.resources.game_screen_note_dialog_title
import pm.bam.gamedeals.feature.game.ui.GamePageViewModel.GamePageData
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
internal fun NoteEditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.game_screen_note_dialog_title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.game_screen_note_dialog_label)) },
                placeholder = { Text(stringResource(Res.string.game_screen_note_dialog_placeholder)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(Res.string.game_screen_note_dialog_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.game_screen_note_dialog_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CandidatePickerSheet(
    data: GamePageData.Data,
    onDismiss: () -> Unit,
    onCandidatePicked: (igdbGameId: Long) -> Unit,
    onRetry: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = GameDealsCustomTheme.spacing.large, vertical = GameDealsCustomTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
        ) {
            Text(text = stringResource(Res.string.game_details_title_match_picker_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = stringResource(Res.string.game_details_title_match_picker_explanation), style = MaterialTheme.typography.bodyMedium)
            when (val state = data.candidatesState) {
                GamePageViewModel.CandidatesState.Idle,
                GamePageViewModel.CandidatesState.Loading -> {
                    val loadingCd = stringResource(Res.string.game_details_title_match_picker_loading_cd)
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).semantics { contentDescription = loadingCd })
                    }
                }
                is GamePageViewModel.CandidatesState.Loaded -> {
                    if (state.items.isEmpty()) {
                        Text(text = stringResource(Res.string.game_details_title_match_picker_empty))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            verticalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            horizontalArrangement = Arrangement.spacedBy(GameDealsCustomTheme.spacing.medium),
                            modifier = Modifier.fillMaxWidth().height(420.dp),
                        ) {
                            gridItems(state.items, key = { it.id }) { candidate ->
                                IgdbGameTile(game = candidate, onClick = onCandidatePicked, isCurrent = candidate.id == data.igdbGameOrNull?.id)
                            }
                        }
                    }
                }
                GamePageViewModel.CandidatesState.Error -> {
                    Text(text = stringResource(Res.string.game_details_title_match_picker_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.game_details_title_match_picker_retry)) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.game_details_title_match_picker_close)) }
            }
        }
    }
}
