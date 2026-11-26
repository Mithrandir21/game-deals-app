package pm.bam.gamedeals.common.exceptions

class DataNotFoundException(dataKey: String?) : Exception(
    when (dataKey) {
        null -> "Data not found"
        else -> "Data not found for key: $dataKey"
    }
)
