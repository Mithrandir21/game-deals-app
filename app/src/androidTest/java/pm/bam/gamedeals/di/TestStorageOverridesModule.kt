package pm.bam.gamedeals.di

import android.content.Context
import android.content.SharedPreferences
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.SECURE_QUALIFIER
import pm.bam.gamedeals.common.di.SETTINGS_QUALIFIER

/**
 * Test-only Koin module that points the settings store and the secure store at their own prefs files, emptied when
 * the test app starts. The tests run inside the installed app, so without this they would read and write that app's
 * settings and sign-in: a phone signed in to IsThereAnyDeal would fail every test that expects a signed-out user, and
 * the tests would change the phone's own settings. Loaded last by [pm.bam.gamedeals.TestGameDealsApplication].
 */
val testStorageOverridesModule = module {
    single<SharedPreferences>(SETTINGS_QUALIFIER) { androidContext().emptyPrefs("gamedeals_test_common_storage") }
    single<SharedPreferences>(SECURE_QUALIFIER) { androidContext().emptyPrefs("gamedeals_test_secure_storage") }
}

private fun Context.emptyPrefs(name: String): SharedPreferences =
    getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }
