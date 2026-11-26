package pm.bam.gamedeals.domain.utils

const val millisInMinute: Long = 60L * 1000L

const val millisInHour: Long = millisInMinute * 60L

/** Convenience for the metadata/feed cache tiers (see the ITAD caching strategy, §4). */
const val millisInDay: Long = millisInHour * 24L