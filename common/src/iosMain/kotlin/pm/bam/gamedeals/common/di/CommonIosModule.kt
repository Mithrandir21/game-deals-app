package pm.bam.gamedeals.common.di

import kotlinx.coroutines.Dispatchers
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults
import pm.bam.gamedeals.common.storage.KeychainBackend
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

    // Encrypted-at-rest store (iOS Keychain) for secrets like the ITAD auth token (#239).
    single<Storage>(SECURE_QUALIFIER) {
        StorageImpl(get(), KeychainBackend(service = "gamedeals_secure_storage"), Dispatchers.Default)
    }
}
