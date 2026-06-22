package pm.bam.gamedeals.common.di

import org.koin.core.qualifier.named

val SETTINGS_QUALIFIER = named("settings")

/**
 * Encrypted-at-rest [pm.bam.gamedeals.common.storage.Storage] (Android Keystore / iOS Keychain), for
 * secrets like the ITAD auth token (#239). Same `Storage` contract as [SETTINGS_QUALIFIER]; only the
 * platform backend differs.
 */
val SECURE_QUALIFIER = named("secure")
