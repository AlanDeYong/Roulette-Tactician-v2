package com.roulettetactician.di

import android.content.Context
import androidx.room.Room
import com.roulettetactician.data.AppDatabase
import com.roulettetactician.data.SessionDao
import com.roulettetactician.data.SpinDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendFamily")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendFamily` TEXT NOT NULL DEFAULT 'COMBINED'")
            if (!columns.contains("trendLookback")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendLookback` INTEGER NOT NULL DEFAULT 37")
            if (!columns.contains("trendProgression")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendProgression` TEXT NOT NULL DEFAULT 'AUTO'")
            if (!columns.contains("trendColdLimit")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendColdLimit` INTEGER NOT NULL DEFAULT 5")
            if (!columns.contains("maType")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maType` TEXT NOT NULL DEFAULT 'SMA'")
            if (!columns.contains("maPeriod")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maPeriod` INTEGER NOT NULL DEFAULT 10")
            if (!columns.contains("maCategory")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maCategory` TEXT NOT NULL DEFAULT 'ALL'")
            if (!columns.contains("maAlmaOffset")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maAlmaOffset` REAL NOT NULL DEFAULT 0.85")
            if (!columns.contains("maAlmaSigma")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maAlmaSigma` REAL NOT NULL DEFAULT 6.0")
            if (!columns.contains("maKamaFast")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maKamaFast` INTEGER NOT NULL DEFAULT 2")
            if (!columns.contains("maKamaSlow")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `maKamaSlow` INTEGER NOT NULL DEFAULT 30")
            if (!columns.contains("triggerRB")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `triggerRB` INTEGER NOT NULL DEFAULT 4")
            if (!columns.contains("triggerHL")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `triggerHL` INTEGER NOT NULL DEFAULT 4")
            if (!columns.contains("triggerOE")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `triggerOE` INTEGER NOT NULL DEFAULT 4")
            if (!columns.contains("triggerDozens")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `triggerDozens` INTEGER NOT NULL DEFAULT 6")
            if (!columns.contains("triggerColumns")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `triggerColumns` INTEGER NOT NULL DEFAULT 6")
        }
    }

    val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `gameplay_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `rouletteSessionName` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `startingBankroll` REAL NOT NULL DEFAULT 1000.0,
                    `insideBaseUnit` REAL NOT NULL DEFAULT 1.0,
                    `insideMinUnit` REAL NOT NULL DEFAULT 1.0,
                    `outsideBaseUnit` REAL NOT NULL DEFAULT 5.0,
                    `outsideMinUnit` REAL NOT NULL DEFAULT 5.0,
                    `recordsJson` TEXT NOT NULL DEFAULT '[]'
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_gameplay_sessions_rouletteSessionName` ON `gameplay_sessions` (`rouletteSessionName`)")
        }
    }

    val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendPrimaryCriterion")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendPrimaryCriterion` TEXT NOT NULL DEFAULT 'PNL'")
            if (!columns.contains("trendSecondaryCriterion")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendSecondaryCriterion` TEXT NOT NULL DEFAULT 'MAX_DD'")
        }
    }

    val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendColdRule")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendColdRule` TEXT NOT NULL DEFAULT 'STRICT'")
        }
    }

    val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendEqualizePayout")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendEqualizePayout` INTEGER NOT NULL DEFAULT 0")
            if (!columns.contains("trendPushOrProfit")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendPushOrProfit` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendRiskCap")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendRiskCap` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendUnderwaterFilter")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendUnderwaterFilter` INTEGER NOT NULL DEFAULT 1")
        }
    }

    val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendTransitionRule")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendTransitionRule` TEXT NOT NULL DEFAULT 'COMPLETE_CYCLE'")
            if (!columns.contains("trendSwitchSensitivity")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendSwitchSensitivity` TEXT NOT NULL DEFAULT 'MODERATE'")
            if (!columns.contains("trendShowIdealOverlay")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendShowIdealOverlay` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(sessions)")
            val columns = mutableSetOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val nameIdx = it.getColumnIndex("name")
                    if (nameIdx >= 0) columns.add(it.getString(nameIdx))
                }
            }
            if (!columns.contains("trendStartMode")) db.execSQL("ALTER TABLE `sessions` ADD COLUMN `trendStartMode` TEXT NOT NULL DEFAULT 'FROM_SPIN_1'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "roulette_db"
        )
        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()
    }

    @Provides
    fun provideSpinDao(db: AppDatabase): SpinDao {
        return db.spinDao()
    }

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao {
        return db.sessionDao()
    }

    @Provides
    @Singleton
    fun provideGameplaySessionDao(db: AppDatabase): com.roulettetactician.data.GameplaySessionDao {
        return db.gameplaySessionDao()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences("roulette_prefs", Context.MODE_PRIVATE)
    }
}
