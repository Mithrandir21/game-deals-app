package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "Release")
@Immutable
@Serializable
data class Release(
    @PrimaryKey
    @SerialName("title")
    val title: String,
    @SerialName("date")
    val date: Int,
    @SerialName("image")
    val image: String,
)
