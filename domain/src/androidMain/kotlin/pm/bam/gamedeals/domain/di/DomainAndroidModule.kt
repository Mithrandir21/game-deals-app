package pm.bam.gamedeals.domain.di

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.scheduling.AndroidNotificationScheduler
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.verbose
import java.util.concurrent.Executors

val domainAndroidModule = module {

    single<NotificationScheduler> { AndroidNotificationScheduler(androidContext()) }

    single<RoomDatabase.Builder<DomainDatabase>> {
        val builder = Room.databaseBuilder<DomainDatabase>(
            context = androidContext(),
            name = "DomainDatabase.db"
        )
            .setQueryCoroutineContext(Dispatchers.IO)
        // Debug only: every statement and its bound values would otherwise reach logcat in release.
        if (getOrNull<AppInfo>()?.isDebug == true) {
            val logger = get<Logger>()
            builder.setQueryCallback(
                { sqlQuery, bindArgs -> verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" } },
                Executors.newSingleThreadExecutor()
            )
        }
        builder
    }
}
