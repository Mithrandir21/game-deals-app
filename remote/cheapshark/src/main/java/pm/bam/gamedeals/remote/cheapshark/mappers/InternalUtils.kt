package pm.bam.gamedeals.remote.cheapshark.mappers

internal fun Int.toBooleanStrict(): Boolean =
    when (this) {
        0 -> false
        1 -> true
        else -> throw Exception("Unknown value for int to boolean conversion: $this")
    }
