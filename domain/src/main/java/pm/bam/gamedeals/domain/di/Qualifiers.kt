package pm.bam.gamedeals.domain.di

import javax.inject.Qualifier

/** A [Qualifier] used specifically for dependencies associated specifically with Domain module as opposed to any other Module. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Domain

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class CurrencyDenomination