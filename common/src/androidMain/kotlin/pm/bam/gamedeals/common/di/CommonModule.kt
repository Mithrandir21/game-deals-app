package pm.bam.gamedeals.common.di

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatterImpl
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsingImpl
import pm.bam.gamedeals.common.serializer.Serializer
import pm.bam.gamedeals.common.serializer.SerializerImpl
import pm.bam.gamedeals.common.storage.SettingStorage
import pm.bam.gamedeals.common.storage.Storage

val SETTINGS_QUALIFIER = named("settings")

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

    single<SharedPreferences>(SETTINGS_QUALIFIER) {
        androidContext().getSharedPreferences("gamedeals_common_storage", Context.MODE_PRIVATE)
    }
    single<Storage>(SETTINGS_QUALIFIER) {
        SettingStorage(get(), get(SETTINGS_QUALIFIER))
    }
}
