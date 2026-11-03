package com.roulettetactician.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameplaySessionDao {

    @Query("SELECT * FROM gameplay_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<GameplaySessionEntity>>

    @Query("SELECT * FROM gameplay_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessionsList(): List<GameplaySessionEntity>

    @Query("SELECT * FROM gameplay_sessions WHERE rouletteSessionName = :rouletteSessionName ORDER BY timestamp DESC")
    fun getSessionsForRouletteSession(rouletteSessionName: String): Flow<List<GameplaySessionEntity>>

    @Query("SELECT * FROM gameplay_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): GameplaySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: GameplaySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<GameplaySessionEntity>): List<Long>

    @Update
    suspend fun updateSession(session: GameplaySessionEntity)

    @Delete
    suspend fun deleteSession(session: GameplaySessionEntity)

    @Query("DELETE FROM gameplay_sessions WHERE rouletteSessionName = :rouletteSessionName")
    suspend fun deleteSessionsForRouletteSession(rouletteSessionName: String)

    @Query("UPDATE gameplay_sessions SET rouletteSessionName = :newName WHERE rouletteSessionName = :oldName")
    suspend fun updateRouletteSessionName(oldName: String, newName: String)

    @Query("SELECT COUNT(*) FROM gameplay_sessions WHERE rouletteSessionName = :rouletteSessionName")
    suspend fun getCountForRouletteSession(rouletteSessionName: String): Int
}
