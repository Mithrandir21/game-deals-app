package pm.bam.gamedeals.domain.utils

const val millisInHour: Long = 60L * 60L * 1000L

/** Convenience for the metadata/feed cache tiers (see the ITAD caching strategy, §4). */
const val millisInDay: Long = millisInHour * 24L