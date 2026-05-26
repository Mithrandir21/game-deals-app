package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

@Immutable
data class IgdbGame(
    val id: Long,
    val name: String,
    val summary: String?,
)
