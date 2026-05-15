package pm.bam.gamedeals.domain.di

import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.verbose
import java.util.concurrent.Executors

val domainAndroidModule = module {
    single<RoomDatabase.Builder<DomainDatabase>> {
        val logger = get<Logger>()
        Room.databaseBuilder<DomainDatabase>(
            context = androidContext(),
            name = "DomainDatabase.db"
        )
            .setQueryCallback(
                { sqlQuery, bindArgs -> verbose(logger) { "SQL Query: $sqlQuery SQL Args: $bindArgs" } },
                Executors.newSingleThreadExecutor()
            )
            .setQueryCoroutineContext(Dispatchers.IO)
    }
}
