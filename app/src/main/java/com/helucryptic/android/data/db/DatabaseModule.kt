package com.helucryptic.android.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "helucryptic.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun messageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun contactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun roomDao(db: AppDatabase): RoomDao = db.roomDao()
}
