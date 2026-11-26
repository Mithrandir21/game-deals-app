package pm.bam.gamedeals.common.di

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.storage.EncryptedSharedPreferencesBackend
import pm.bam.gamedeals.common.storage.SharedPreferencesBackend
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.StorageImpl

val commonAndroidModule = module {
    single<SharedPreferences>(SETTINGS_QUALIFIER) {
        androidContext().getSharedPreferences("gamedeals_common_storage", Context.MODE_PRIVATE)
    }
    single<Storage>(SETTINGS_QUALIFIER) {
        StorageImpl(get(), SharedPreferencesBackend(get(SETTINGS_QUALIFIER)), Dispatchers.IO)
    }

    // Encrypted-at-rest store (Android Keystore AES/GCM) for secrets like the ITAD auth token (#239).
    // Separate prefs file from the settings store so a clear/migration of one never touches the other.
    single<SharedPreferences>(SECURE_QUALIFIER) {
        androidContext().getSharedPreferences("gamedeals_secure_storage", Context.MODE_PRIVATE)
    }
    single<Storage>(SECURE_QUALIFIER) {
        StorageImpl(get(), EncryptedSharedPreferencesBackend(get(SECURE_QUALIFIER)), Dispatchers.IO)
    }
}
