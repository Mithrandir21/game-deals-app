package pm.bam.gamedeals.di

import android.content.Context
import android.util.Log
import coil.ImageLoader
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.toLogLevel
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    @Singleton
    fun provideFirebase(): FirebaseAnalytics = Firebase.analytics

    /**
     * System-clock adapter for the [Clock] port declared in `:common`.
     * Lives in the composition root so `:domain`/`:common` never depend on a concrete clock.
     * This is the only place in production code that calls [System.currentTimeMillis].
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    @Coil
    @Provides
    @Singleton
    fun provideCoilInternalLogger(logger: Logger): coil.util.Logger =
        object : coil.util.Logger {
            override var level: Int = Log.DEBUG
            override fun log(tag: String, priority: Int, message: String?, throwable: Throwable?) {
                logger.log(priority.toLogLevel(), "CoilLogging", throwable = throwable) { message ?: "Coil Log Message" }
            }
        }


    @Provides
    @Singleton
    fun provideCoilImageLoader(
        @ApplicationContext appContext: Context,
        @Coil coilLogger: coil.util.Logger
    ): ImageLoader = ImageLoader.Builder(appContext)
        .crossfade(true)
        .logger(coilLogger)
        .build()

}