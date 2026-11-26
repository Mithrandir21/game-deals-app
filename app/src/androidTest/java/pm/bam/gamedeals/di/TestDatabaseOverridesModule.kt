package pm.bam.gamedeals.di

import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter

/**
 * Test-only Koin module that replaces the production [DomainDatabase] binding with an
 * in-memory Room database. Type-converters are inherited from the production
 * [pm.bam.gamedeals.domain.di.domainModule]. Loaded last by
 * [pm.bam.gamedeals.TestGameDealsApplication].
 */
val testDatabaseOverridesModule = module {
    single<DomainDatabase> {
        Room.inMemoryDatabaseBuilder(androidContext(), DomainDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(get<StoreImagesConverter>())
            .addTypeConverter(get<GiveawayPlatformsConverter>())
            .addTypeConverter(get<LocalDatetimeConverter>())
            .build()
    }
}
