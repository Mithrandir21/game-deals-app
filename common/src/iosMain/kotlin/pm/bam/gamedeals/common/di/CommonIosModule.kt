package pm.bam.gamedeals.common.di

import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import pm.bam.gamedeals.common.storage.NSUserDefaultsStorage
import pm.bam.gamedeals.common.storage.Storage

val commonIosModule = module {
    single<NSUserDefaults>(SETTINGS_QUALIFIER) {
        NSUserDefaults(suiteName = "gamedeals_common_storage")!!
    }
    single<Storage>(SETTINGS_QUALIFIER) {
        NSUserDefaultsStorage(get(), get(SETTINGS_QUALIFIER))
    }
}
