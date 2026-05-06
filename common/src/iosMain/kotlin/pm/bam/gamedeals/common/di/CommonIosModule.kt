package pm.bam.gamedeals.common.di

import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import pm.bam.gamedeals.common.storage.NSUserDefaultsBackend
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.StorageImpl

val commonIosModule = module {
    single<NSUserDefaults>(SETTINGS_QUALIFIER) {
        NSUserDefaults(suiteName = "gamedeals_common_storage")!!
    }
    single<Storage>(SETTINGS_QUALIFIER) {
        // Kotlin/Native's Dispatchers.IO aliases to Default; keep the choice explicit here.
        StorageImpl(get(), NSUserDefaultsBackend(get(SETTINGS_QUALIFIER)), Dispatchers.Default)
    }
}
