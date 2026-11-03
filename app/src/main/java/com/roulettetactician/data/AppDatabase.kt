package com.roulettetactician.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SpinResult::class, SessionEntity::class, GameplaySessionEntity::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spinDao(): SpinDao
    abstract fun sessionDao(): SessionDao
    abstract fun gameplaySessionDao(): GameplaySessionDao
}
