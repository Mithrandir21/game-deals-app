package pm.bam.gamedeals.domain.di

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import pm.bam.gamedeals.domain.db.DomainDatabase

val domainIosModule = module {
    single<RoomDatabase.Builder<DomainDatabase>> {
        val documents = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
        Room.databaseBuilder<DomainDatabase>(
            name = "$documents/DomainDatabase.db"
        ).setDriver(BundledSQLiteDriver())
    }
}
