package com.roulettetactician.logic

import androidx.compose.ui.graphics.Color
import kotlin.math.max

object HeatmapCalculator {

    // --- Configuration Constants ---
    val COLOR_MANUAL_HOT = Color(0xFFFACC15) // Bright Yellow
    val COLOR_MANUAL_COLD = Color(0xFF2563EB) // EU Button Blue

    // Palette Points
    val COLOR_GRAY = Color(0xFF64748B) // Slate 500
    private val COLOR_WARM_TARGET = Color(0xFFFACC15) // Bright Yellow
    private val COLOR_COOL_TARGET = Color(0xFF2563EB) // EU Button Blue

    data class HeatmapStats(
        val freq: Map<Int, Int>,
        val ranks: Map<Int, RankData>,
        val totalHistoryCount: Int
    )

    data class RankData(
        val rank: Int,
        val totalNonManual: Int,
        val score: Double
    )

    /**
     * Ports the React useMemo 'stats' logic.
     * Calculates Frequency and Recency scores for all numbers.
     */
    fun calculateStats(
        history: List<Int>,
        windowSize: Int,
        hotOverrides: List<Int?>,
        coldOverrides: List<Int?>
    ): HeatmapStats {
        val windowedHistory = history.take(windowSize)
        val analysisCount = windowedHistory.size
        
        val freq = mutableMapOf<Int, Int>()
        val lastIndices = mutableMapOf<Int, Int>()

        // Initialize 0-36
        for (i in 0..36) {
            freq[i] = 0
        }

        windowedHistory.forEachIndexed { index, n ->
            freq[n] = (freq[n] ?: 0) + 1
            if (!lastIndices.containsKey(n)) {
                lastIndices[n] = index
            }
        }

        // Identify Non-Manual Numbers
        val manualSet = (hotOverrides + coldOverrides).filterNotNull().toSet()
        val nonManualNumbers = mutableListOf<Pair<Int, Double>>()

        for (i in 0..36) {
            if (!manualSet.contains(i)) {
                val f = freq[i] ?: 0
                val lastIdx = lastIndices[i]
                var recencyScore = 0.0

                if (lastIdx != null) {
                    // 1.0 (Recent) -> 0.0 (Old)
                    recencyScore = 1.0 - (lastIdx.toDouble() / max(1.0, analysisCount.toDouble()))
                }

                // Score = Frequency + Tie-Breaker
                val score = f + (recencyScore * 0.99)
                nonManualNumbers.add(i to score)
            }
        }

        // Sort Descending (Higher score = Better rank)
        nonManualNumbers.sortWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })

        val ranks = mutableMapOf<Int, RankData>()
        nonManualNumbers.forEachIndexed { index, (num, score) ->
            ranks[num] = RankData(
                rank = index,
                totalNonManual = nonManualNumbers.size,
                score = score
            )
        }

        return HeatmapStats(freq, ranks, history.size)
    }

    /**
     * Ports getHeatColor
     */
    fun getHeatColor(
        number: Int,
        stats: HeatmapStats,
        hotOverrides: List<Int?>,
        coldOverrides: List<Int?>
    ): Color {
        // 1. Manual Overrides
        if (hotOverrides.contains(number)) return COLOR_MANUAL_HOT
        if (coldOverrides.contains(number)) return COLOR_MANUAL_COLD

        // 2. Initial state: When no spins have been recorded, all numbers are Slate Gray (#64748B)
        if (stats.totalHistoryCount == 0) return COLOR_GRAY

        // 3. Rank Logic
        val rankData = stats.ranks[number] ?: return COLOR_GRAY
        
        val rank = rankData.rank
        val total = rankData.totalNonManual
        val medianRank = (total - 1) / 2.0

        // Progressive temperature scaling over first 50 spins
        val spinFactor = (minOf(stats.totalHistoryCount, 50) / 50.0).toFloat()

        if (rank < medianRank) {
            // TOP HALF (Yellow/Warm)
            val intensity = 1.0 - (rank / medianRank)
            val effectiveIntensity = (intensity * spinFactor).toFloat().coerceIn(0f, 1f)
            return interpolateColor(COLOR_GRAY, COLOR_WARM_TARGET, effectiveIntensity)
        } else {
            // BOTTOM HALF (Blue/Cool)
            val intensity = (rank - medianRank) / (total - medianRank)
            val finalIntensity = (intensity * spinFactor).toFloat().coerceIn(0f, 1f)
            return interpolateColor(COLOR_GRAY, COLOR_COOL_TARGET, finalIntensity)
        }
    }

    fun interpolateColor(start: Color = COLOR_GRAY, end: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = start.red + (end.red - start.red) * f,
            green = start.green + (end.green - start.green) * f,
            blue = start.blue + (end.blue - start.blue) * f,
            alpha = 1f
        )
    }

    val OUTSIDE_DEFINITIONS: Map<String, List<Int>> = mapOf(
        "doz1" to (1..12).toList(),
        "doz2" to (13..24).toList(),
        "doz3" to (25..36).toList(),
        "col1" to listOf(1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34),
        "col2" to listOf(2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35),
        "col3" to listOf(3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36),
        "low" to (1..18).toList(),
        "even" to (1..36).filter { it % 2 == 0 },
        "red" to listOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36),
        "black" to listOf(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35),
        "odd" to (1..36).filter { it % 2 != 0 },
        "high" to (19..36).toList()
    )

    enum class TrendDirection {
        NONE, UP, DOWN
    }

    enum class TrendStrength {
        NONE,
        WEAK,     // Tier 1: Decelerating slope or small gain/loss
        MODERATE, // Tier 2: Steady trend
        STRONG    // Tier 3: Accelerating slope or large gain/loss
    }

    data class OutsideHeatInfo(
        val hitCount: Int,
        val totalSpins: Int,
        val percentage: Float,
        val color: Color,
        val badge: String,
        val trendDirection: TrendDirection = TrendDirection.NONE,
        val trendStrength: TrendStrength = TrendStrength.NONE
    )

    /**
     * Computes outside bets hit frequencies and temperature color for GLOBAL_HEAT mode.
     */
    fun computeOutsideHeat(
        spins: List<Int>,
        windowSize: Int = 50
    ): Map<String, OutsideHeatInfo> {
        val window = spins.take(windowSize)
        val n = window.size
        if (n == 0) {
            return OUTSIDE_DEFINITIONS.keys.associateWith {
                OutsideHeatInfo(0, 0, 0f, COLOR_GRAY, "0%")
            }
        }
        val spinFactor = (minOf(n, 50) / 50.0).toFloat()
        val result = mutableMapOf<String, OutsideHeatInfo>()
        for ((key, nums) in OUTSIDE_DEFINITIONS) {
            val numSet = nums.toSet()
            val hits = window.count { numSet.contains(it) }
            val pct = (hits.toFloat() / n) * 100f
            val expected = if (key.startsWith("doz") || key.startsWith("col")) 32.43f else 48.65f
            val delta = pct - expected

            val color = if (delta > 0) {
                val intensity = ((delta / 25f) * spinFactor).coerceIn(0f, 1f)
                interpolateColor(COLOR_GRAY, COLOR_WARM_TARGET, intensity)
            } else {
                val intensity = ((-delta / 25f) * spinFactor).coerceIn(0f, 1f)
                interpolateColor(COLOR_GRAY, COLOR_COOL_TARGET, intensity)
            }
            val sign = if (delta >= 0) "+" else ""
            val badge = "$hits (${sign}${delta.toInt()}%)"
            result[key] = OutsideHeatInfo(hits, n, pct, color, badge)
        }
        return result
    }

    /**
     * Computes outside bets aggregate Bet Trend weights and temperature for TREND_WEIGHTS mode.
     * Uses the exact same color spectrum as the numbers: Slate -> Cyan -> Emerald -> Amber/Gold,
     * highlighting differences clearly when outside bet weightages deviate.
     */
    fun computeOutsideWeights(
        numberWeights: Map<Int, Double>?
    ): Map<String, OutsideHeatInfo> {
        if (numberWeights == null || numberWeights.isEmpty()) {
            return OUTSIDE_DEFINITIONS.keys.associateWith {
                OutsideHeatInfo(0, 0, 0f, Color(0xFF334155), "w: 0.0")
            }
        }

        // Calculate total weight and average pocket weight for each outside bet group
        val groupAverages = OUTSIDE_DEFINITIONS.mapValues { (_, nums) ->
            val sumW = nums.sumOf { numberWeights[it] ?: 0.0 }
            Pair(sumW, sumW / nums.size.toDouble())
        }

        val minAvg = groupAverages.values.minOf { it.second }
        val maxAvg = groupAverages.values.maxOf { it.second }
        val range = (maxAvg - minAvg).toFloat()

        val result = mutableMapOf<String, OutsideHeatInfo>()
        for ((key, pair) in groupAverages) {
            val (sumW, avgW) = pair
            val norm = if (range > 0.05f) {
                ((avgW - minAvg).toFloat() / range).coerceIn(0f, 1f)
            } else {
                0.25f // Neutral baseline when all weights are nearly identical
            }

            // Same color range as numbers: Slate (0.0) -> Cyan (0.25) -> Emerald (0.65) -> Amber/Gold (1.0)
            val color = when {
                norm < 0.25f -> interpolateColor(Color(0xFF334155), Color(0xFF06B6D4), norm / 0.25f)
                norm < 0.65f -> interpolateColor(Color(0xFF06B6D4), Color(0xFF10B981), (norm - 0.25f) / 0.40f)
                else -> interpolateColor(Color(0xFF10B981), Color(0xFFF59E0B), (norm - 0.65f) / 0.35f)
            }

            result[key] = OutsideHeatInfo(0, 0, sumW.toFloat(), color, String.format("w: %.1f", sumW))
        }
        return result
    }
}
