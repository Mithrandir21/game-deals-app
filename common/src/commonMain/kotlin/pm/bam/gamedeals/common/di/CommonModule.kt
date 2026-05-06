package pm.bam.gamedeals.common.di

import kotlinx.serialization.json.Json
import org.koin.dsl.module
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatterImpl
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsingImpl
import pm.bam.gamedeals.common.serializer.Serializer
import pm.bam.gamedeals.common.serializer.SerializerImpl

val commonModule = module {
    single<Json> {
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
    single<Serializer> { SerializerImpl(get()) }
    single<DatetimeParsing> { DatetimeParsingImpl() }
    single<DateTimeFormatter> { DateTimeFormatterImpl(get()) }
}
