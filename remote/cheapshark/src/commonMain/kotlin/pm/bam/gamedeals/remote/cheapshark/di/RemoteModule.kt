package pm.bam.gamedeals.remote.cheapshark.di

import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.remote.cheapshark.CheapsharkSourceImpl
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformationImpl

val CHEAPSHARK_QUALIFIER = named("cheapshark")

private val CURRENCY_DENOMINATION_QUALIFIER = named("cheapshark.currencyDenomination")

val cheapsharkRemoteModule = module {
    single<String>(CURRENCY_DENOMINATION_QUALIFIER) { "$" }

    single<CurrencyTransformation> {
        CurrencyTransformationImpl(get(CURRENCY_DENOMINATION_QUALIFIER))
    }

    single<DealsSource> {
        CheapsharkSourceImpl(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
}
