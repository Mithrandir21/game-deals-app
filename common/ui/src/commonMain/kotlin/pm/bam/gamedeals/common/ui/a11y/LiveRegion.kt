package pm.bam.gamedeals.common.ui.a11y

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * Marks a status node as a **polite** live region so TalkBack/VoiceOver announces it when it appears
 * or its text changes — without interrupting whatever the user is currently hearing.
 *
 * Use it on the *terminal* states a user waits on: the empty-list message and the (non-snackbar)
 * error pane that replace a loading spinner. Material3 [androidx.compose.material3.Snackbar]s already
 * announce themselves, so error snackbars don't need this. Avoid it on transient spinners and on
 * in-content sub-sections that compose on scroll, where the announcement is noise rather than signal.
 */
fun Modifier.politeLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }
