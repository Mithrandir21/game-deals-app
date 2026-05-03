package pm.bam.gamedeals.common.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatterImpl
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsingImpl
import pm.bam.gamedeals.common.serializer.Serializer
import pm.bam.gamedeals.common.serializer.SerializerImpl
import pm.bam.gamedeals.common.storage.SettingStorage
import pm.bam.gamedeals.common.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CommonModule {

    @Provides
    @Settings
    fun provideCommonSharedPreference(@ApplicationContext appContext: Context): SharedPreferences =
        appContext.getSharedPreferences("gamedeals_common_storage", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideKotlinSerializer(): Json = Json {
        encodeDefaults = true // Makes sure default field values are encoded
        ignoreUnknownKeys = true
    }

    @Provides
    @Singleton
    fun provideSerializer(json: Json): Serializer = SerializerImpl(json)

    @Provides
    @Settings
    @Singleton
    fun provideSettingsStorage(serializer: Serializer, @Settings sharedPreferences: SharedPreferences): Storage = SettingStorage(serializer, sharedPreferences)

    @Provides
    @Singleton
    fun provideDateTimeParsing(): DatetimeParsing = DatetimeParsingImpl()

    @Provides
    @Singleton
    fun provideDatetimeFormatter(datetimeParsing: DatetimeParsing): DateTimeFormatter = DateTimeFormatterImpl(datetimeParsing)
}