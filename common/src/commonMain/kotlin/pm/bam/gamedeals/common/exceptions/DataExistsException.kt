package pm.bam.gamedeals.common.exceptions

class DataExistsException(dataKey: String?) : Exception(
    when (dataKey) {
        null -> "Data already exists"
        else -> "Data already exists for key: $dataKey"
    }
)
