package pm.bam.gamedeals.logging.di

import org.koin.dsl.module
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.implementations.IosConsoleLoggingListener
import pm.bam.gamedeals.logging.implementations.IosSentryStubLoggingListener

val loggingIosModule = module {
    single<Logger> {
        // Mirrors Android's two-listener Logger: NSLog-based general logger
        // plus a Sentry stub that prints what real Sentry would have captured.
        // Swap the stub for a real SentryLoggingListener once Sentry-Cocoa is
        // wired via SPM — same constructor shape, no other changes.
        LoggerImpl(
            mutableSetOf(
                IosConsoleLoggingListener(),
                IosSentryStubLoggingListener(),
            )
        )
    }
}
