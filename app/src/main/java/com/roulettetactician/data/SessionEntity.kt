package com.roulettetactician.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val name: String,
    val timestamp: Long,
    val hotOverrides: String, // Stored as CSV "1,2,3,null,5"
    val coldOverrides: String,
    val analysisWindow: Int,
    val history: String, // Stored as CSV "0,32,15..."
    val chips: String, // Stored as format: "val:x:y:betId|val:x:y:betId"
    // Bet Trend Configurations
    val trendFamily: String = "COMBINED",
    val trendLookback: Int = 37,
    val trendProgression: String = "AUTO",
    val trendColdLimit: Int = 5,
    val trendPrimaryCriterion: String = "PNL",
    val trendSecondaryCriterion: String = "MAX_DD",
    val trendColdRule: String = "STRICT",
    val trendEqualizePayout: Boolean = false,
    val trendPushOrProfit: Boolean = false,
    val trendRiskCap: Boolean = true,
    val trendUnderwaterFilter: Boolean = true,
    val trendTransitionRule: String = "COMPLETE_CYCLE",
    val trendSwitchSensitivity: String = "MODERATE",
    val trendShowIdealOverlay: Boolean = false,
    val trendStartMode: String = "FROM_SPIN_1",
    // Moving Average Configurations
    val maType: String = "SMA",
    val maPeriod: Int = 10,
    val maCategory: String = "ALL",
    val maAlmaOffset: Double = 0.85,
    val maAlmaSigma: Double = 6.0,
    val maKamaFast: Int = 2,
    val maKamaSlow: Int = 30,
    // Trigger Thresholds Configurations
    val triggerRB: Int = 4,
    val triggerHL: Int = 4,
    val triggerOE: Int = 4,
    val triggerDozens: Int = 6,
    val triggerColumns: Int = 6
)
