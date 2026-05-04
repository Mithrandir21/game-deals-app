package pm.bam.gamedeals.common.di

import android.content.Context
import android.content.SharedPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pm.bam.gamedeals.common.storage.SettingStorage
import pm.bam.gamedeals.common.storage.Storage

val SETTINGS_QUALIFIER = named("settings")

val commonAndroidModule = module {
    single<SharedPreferences>(SETTINGS_QUALIFIER) {
        androidContext().getSharedPreferences("gamedeals_common_storage", Context.MODE_PRIVATE)
    }
    single<Storage>(SETTINGS_QUALIFIER) {
        SettingStorage(get(), get(SETTINGS_QUALIFIER))
    }
}
