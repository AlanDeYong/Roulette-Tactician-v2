package com.roulettetactician.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gameplay_sessions",
    indices = [Index(value = ["rouletteSessionName"])]
)
data class GameplaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rouletteSessionName: String, // foreign reference to parent Roulette Session name
    val name: String, // e.g. "Game 1", "Martingale Test", etc.
    val timestamp: Long,
    val startingBankroll: Double = 1000.0,
    val insideBaseUnit: Double = 1.0,
    val insideMinUnit: Double = 1.0,
    val outsideBaseUnit: Double = 5.0,
    val outsideMinUnit: Double = 5.0,
    val recordsJson: String = "[]" // JSON representation of List<GameplaySpinRecord>
)
