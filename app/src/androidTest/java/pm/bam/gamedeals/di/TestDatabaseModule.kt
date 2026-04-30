package pm.bam.gamedeals.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import pm.bam.gamedeals.domain.db.DomainDatabase
import pm.bam.gamedeals.domain.di.Domain
import pm.bam.gamedeals.domain.utils.GiveawayPlatformsConverter
import pm.bam.gamedeals.domain.utils.LocalDatetimeConverter
import pm.bam.gamedeals.domain.utils.StoreImagesConverter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class TestDatabaseModule {

    @Provides
    @Singleton
    fun provideInMemoryDatabase(
        @ApplicationContext context: Context,
        @Domain storeImagesConverter: StoreImagesConverter,
        @Domain giveawayPlatformsConverter: GiveawayPlatformsConverter,
        @Domain localDatetimeConverter: LocalDatetimeConverter
    ): DomainDatabase =
        Room.inMemoryDatabaseBuilder(context, DomainDatabase::class.java)
            .allowMainThreadQueries()
            .addTypeConverter(storeImagesConverter)
            .addTypeConverter(giveawayPlatformsConverter)
            .addTypeConverter(localDatetimeConverter)
            .build()

    @Provides
    @Domain
    fun provideDomainSharedPreference(@ApplicationContext appContext: Context): SharedPreferences =
        appContext.getSharedPreferences("gamedeals_domain_storage", Context.MODE_PRIVATE)
}
