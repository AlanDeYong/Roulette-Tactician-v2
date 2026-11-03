package com.roulettetactician.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpinDao {
    @Query("SELECT * FROM spins ORDER BY id DESC")
    fun getAllSpins(): Flow<List<SpinResult>>

    @Query("SELECT * FROM spins WHERE sessionId = :sessionId ORDER BY id DESC")
    fun getSpinsForSession(sessionId: Long): Flow<List<SpinResult>>

    @Insert
    suspend fun insertSpin(spin: SpinResult): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(spins: List<SpinResult>): List<Long>

    @Update
    suspend fun updateSpin(spin: SpinResult): Int

    @Delete
    suspend fun deleteSpin(spin: SpinResult): Int

    @Query("DELETE FROM spins")
    suspend fun clearAll(): Int

    @Transaction
    suspend fun replaceAllSpins(spins: List<SpinResult>) {
        clearAll()
        if (spins.isNotEmpty()) {
            insertAll(spins)
        }
    }
}
