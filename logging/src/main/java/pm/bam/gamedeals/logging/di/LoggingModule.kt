package pm.bam.gamedeals.logging.di

import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.LoggerImpl
import pm.bam.gamedeals.logging.implementations.SentryLoggingListener
import pm.bam.gamedeals.logging.implementations.SimpleLoggingListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class LoggingModule {

    @Provides
    @Singleton
    fun provideLogger(): Logger = LoggerImpl(mutableSetOf(SimpleLoggingListener(), SentryLoggingListener()))
}