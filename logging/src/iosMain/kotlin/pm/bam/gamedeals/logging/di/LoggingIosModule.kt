package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.implementations.IosConsoleLoggingListener

val loggingIosModule = module {
    single<Logger> {
        // Sentry-Cocoa isn't yet wired into the Xcode project, so the iOS
        // framework can't auto-link against it. SentryLoggingListener stays
        // out of the iOS Logger until Phase 7 polish adds the SPM/CocoaPods
        // dependency on Sentry-Cocoa.
        LoggerImpl(mutableSetOf(IosConsoleLoggingListener()))
    }
}
