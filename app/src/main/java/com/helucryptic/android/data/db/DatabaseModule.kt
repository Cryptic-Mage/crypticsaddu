package com.helucryptic.android.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** v1 → v2: added keyChanged column (DEFAULT 0 / false) to contacts table. */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE contacts ADD COLUMN keyChanged INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "helucryptic.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun messageDao(db: AppDatabase): MessageDao = db.messageDao()
    @Provides fun contactDao(db: AppDatabase): ContactDao = db.contactDao()
    @Provides fun roomDao(db: AppDatabase): RoomDao = db.roomDao()
}
