package com.roulettetactician.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "spins")
data class SpinResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long = 0, // 0 for default/main session
    val number: Int, // 0-36
    val timestamp: Long = System.currentTimeMillis()
)
