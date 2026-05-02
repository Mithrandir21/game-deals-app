package pm.bam.gamedeals.remote.cheapshark.mappers

import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation

internal data class CheapsharkMapperContext(
    val currency: CurrencyTransformation,
    val dates: DateTimeFormatter,
)
