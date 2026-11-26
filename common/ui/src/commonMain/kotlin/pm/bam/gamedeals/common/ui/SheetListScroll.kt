package pm.bam.gamedeals.common.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * For a list inside a `ModalBottomSheet`: keeps the list's drags and flings from moving the sheet. Without it,
 * every drag the list can't use (all of them when the list fits the sheet, or a downward one at its top) pulls
 * the whole sheet along, and the sheet closes on release once it has moved about 56dp, so an ordinary scroll
 * attempt dismisses it. The sheet can still be dragged by its handle, and closed with Back or a tap outside.
 */
fun Modifier.scrollWithoutDraggingSheet(): Modifier = nestedScroll(KeepLeftoverScroll)

/** Consumes whatever scroll and fling the list leaves over, so none of it reaches the sheet around it. */
private object KeepLeftoverScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}
