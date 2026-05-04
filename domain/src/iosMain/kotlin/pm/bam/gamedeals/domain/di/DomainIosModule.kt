package pm.bam.gamedeals.domain.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.dsl.module
import platform.Foundation.NSHomeDirectory
import pm.bam.gamedeals.domain.db.DomainDatabase

val domainIosModule = module {
    single<RoomDatabase.Builder<DomainDatabase>> {
        Room.databaseBuilder<DomainDatabase>(
            name = NSHomeDirectory() + "/DomainDatabase.db"
        ).setDriver(BundledSQLiteDriver())
    }
}
