package com.roulettetactician.data

data class BackupDataBundle(
    val version: Int = 1,
    val appVersion: String = "2.0",
    val exportTimestamp: Long = System.currentTimeMillis(),
    val rouletteSessions: List<SessionEntity> = emptyList(),
    val gameplaySessions: List<GameplaySessionEntity> = emptyList()
)
