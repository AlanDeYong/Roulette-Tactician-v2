package com.roulettetactician.logic

import kotlin.math.*

/**
 * Moving Average Calculation Engine for Roulette Analysis.
 * Implements SMA, EMA, ALMA, HMA, KAMA, MD, and TEMA across
 * roulette outcome categories (Red/Black, High/Low, Odd/Even, Dozens, Columns).
 */
object MovingAverageCalculator {

    enum class MAType(val label: String, val fullName: String) {
        SMA("SMA", "Simple Moving Avg"),
        EMA("EMA", "Exponential Moving Avg"),
        ALMA("ALMA", "Arnaud Legoux"),
        HMA("HMA", "Hull Moving Avg"),
        KAMA("KAMA", "Kaufman Adaptive"),
        MD("MD", "McGinley Dynamic"),
        TEMA("TEMA", "Triple Exponential")
    }

    val RED_NUMBERS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
    val BLACK_NUMBERS = setOf(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35)

    data class MAConfig(
        val type: MAType = MAType.SMA,
        val period: Int = 10,
        val almaOffset: Double = 0.85,
        val almaSigma: Double = 6.0,
        val kamaFast: Int = 2,
        val kamaSlow: Int = 30
    )

    data class DataSetLine(
        val label: String,
        val colorHex: Long,
        val values: List<Float>
    )

    data class CategoryResult(
        val title: String,
        val subtitle: String,
        val lines: List<DataSetLine>
    )

    data class CompleteMAResult(
        val totalSpins: Int,
        val period: Int,
        val redBlack: CategoryResult,
        val highLow: CategoryResult,
        val oddEven: CategoryResult,
        val dozens: CategoryResult,
        val columns: CategoryResult
    )

    private fun getWMA(series: DoubleArray, period: Int): DoubleArray {
        val res = DoubleArray(series.size)
        if (period <= 0 || series.isEmpty()) return res

        for (i in (period - 1) until series.size) {
            var sum = 0.0
            var weightSum = 0.0
            for (j in 0 until period) {
                val w = (period - j).toDouble()
                sum += series[i - j] * w
                weightSum += w
            }
            res[i] = if (weightSum != 0.0) sum / weightSum else 0.0
        }
        val fillVal = if (period - 1 < series.size) res[period - 1] else series.firstOrNull() ?: 0.0
        for (i in 0 until minOf(period - 1, series.size)) {
            res[i] = fillVal
        }
        return res
    }

    private fun getEMA(series: DoubleArray, period: Int): DoubleArray {
        val res = DoubleArray(series.size)
        if (series.isEmpty() || period <= 0) return res
        val alpha = 2.0 / (period + 1.0)
        var ema = series[0]
        for (i in series.indices) {
            ema = (series[i] - ema) * alpha + ema
            res[i] = ema
        }
        return res
    }

    fun calculateMA(
        chronologicalSpins: List<Int>,
        config: MAConfig,
        checkFn: (Int) -> Boolean
    ): List<Float> {
        val n = config.period.coerceAtLeast(2)
        if (chronologicalSpins.size < n) return emptyList()

        val data = DoubleArray(chronologicalSpins.size) { i ->
            if (checkFn(chronologicalSpins[i])) 100.0 else 0.0
        }

        val res = mutableListOf<Float>()

        when (config.type) {
            MAType.SMA -> {
                for (i in (n - 1) until data.size) {
                    var sum = 0.0
                    for (k in 0 until n) sum += data[i - k]
                    res.add((sum / n).toFloat())
                }
            }

            MAType.EMA -> {
                val alpha = 2.0 / (n + 1.0)
                var ema = 0.0
                for (i in 0 until n) ema += data[i]
                ema /= n
                res.add(ema.toFloat())

                for (i in n until data.size) {
                    ema = (data[i] - ema) * alpha + ema
                    res.add(ema.toFloat())
                }
            }

            MAType.ALMA -> {
                val offset = config.almaOffset
                val sigma = config.almaSigma.coerceAtLeast(0.1)
                val m = floor(offset * (n - 1))
                val s = n / sigma
                val w = DoubleArray(n)
                var wSum = 0.0
                for (i in 0 until n) {
                    val weight = exp(-((i - m) * (i - m)) / (2.0 * s * s))
                    w[i] = weight
                    wSum += weight
                }
                for (i in (n - 1) until data.size) {
                    var sum = 0.0
                    for (j in 0 until n) {
                        sum += data[i - n + 1 + j] * w[j]
                    }
                    res.add(if (wSum != 0.0) (sum / wSum).toFloat() else 0f)
                }
            }

            MAType.HMA -> {
                val halfN = max(1, n / 2)
                val sqrtN = max(1, sqrt(n.toDouble()).toInt())
                val wmaFull = getWMA(data, n)
                val wmaHalf = getWMA(data, halfN)
                val rawHMA = DoubleArray(data.size) { i ->
                    2.0 * wmaHalf[i] - wmaFull[i]
                }
                val hmaFull = getWMA(rawHMA, sqrtN)
                for (i in (n - 1) until data.size) {
                    res.add(hmaFull[i].toFloat())
                }
            }

            MAType.KAMA -> {
                val fast = config.kamaFast.coerceAtLeast(1)
                val slow = config.kamaSlow.coerceAtLeast(1)
                var kama = 0.0
                for (i in 0 until n) kama += data[i]
                kama /= n
                res.add(kama.toFloat())

                val fastC = 2.0 / (fast + 1.0)
                val slowC = 2.0 / (slow + 1.0)

                for (i in n until data.size) {
                    val change = abs(data[i] - data[i - n])
                    var vol = 0.0
                    for (j in 0 until n) {
                        vol += abs(data[i - j] - data[i - j - 1])
                    }
                    val er = if (vol == 0.0) 0.0 else change / vol
                    val sc = (er * (fastC - slowC) + slowC).pow(2.0)
                    kama += sc * (data[i] - kama)
                    res.add(kama.toFloat())
                }
            }

            MAType.MD -> {
                // McGinley Dynamic adapted for roulette rolling hit rates:
                // Uses a rolling frequency source series and applies John McGinley's dynamic
                // adaptive tracking filter: denom = 0.6 * n * (sourceRate / prevMd)^4
                var windowSum = 0.0
                for (i in 0 until n) {
                    windowSum += data[i]
                }
                var md = windowSum / n
                res.add(md.toFloat())

                val minDenom = max(2.0, n * 0.25)
                for (i in n until data.size) {
                    windowSum += data[i] - data[i - n]
                    val sourceRate = windowSum / n
                    val prevMd = max(md, 0.1)
                    val ratio = (sourceRate / prevMd).coerceIn(0.5, 2.0)
                    val denom = max(0.6 * n * ratio.pow(4.0), minDenom)
                    md += (sourceRate - md) / denom
                    res.add(md.toFloat())
                }
            }

            MAType.TEMA -> {
                val ema1 = getEMA(data, n)
                val ema2 = getEMA(ema1, n)
                val ema3 = getEMA(ema2, n)
                for (i in (n - 1) until data.size) {
                    res.add((3.0 * ema1[i] - 3.0 * ema2[i] + ema3[i]).toFloat())
                }
            }
        }

        return res
    }

    /**
     * Computes all roulette MA series for the given chronological spins and config.
     */
    fun computeAll(
        chronologicalSpins: List<Int>,
        config: MAConfig
    ): CompleteMAResult {
        val typeLabel = config.type.label

        // Red / Black (0 and 37 are zeroes)
        val redMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && RED_NUMBERS.contains(it) }
        val blackMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && BLACK_NUMBERS.contains(it) }

        // High (19-36) / Low (1-18)
        val highMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it in 19..36 }
        val lowMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it in 1..18 }

        // Odd / Even
        val oddMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it % 2 != 0 }
        val evenMA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it % 2 == 0 }

        // Dozens
        val doz1MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it in 1..12 }
        val doz2MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it in 13..24 }
        val doz3MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it in 25..36 }

        // Columns
        val col1MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it % 3 == 1 }
        val col2MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it % 3 == 2 }
        val col3MA = calculateMA(chronologicalSpins, config) { it != 0 && it != 37 && it % 3 == 0 }

        return CompleteMAResult(
            totalSpins = chronologicalSpins.size,
            period = config.period,
            redBlack = CategoryResult(
                title = "Red / Black $typeLabel %",
                subtitle = "Even Money",
                lines = listOf(
                    DataSetLine("Red", 0xFFEF4444, redMA),
                    DataSetLine("Black", 0xFF94A3B8, blackMA)
                )
            ),
            highLow = CategoryResult(
                title = "High / Low $typeLabel %",
                subtitle = "Even Money",
                lines = listOf(
                    DataSetLine("High", 0xFFA855F7, highMA),
                    DataSetLine("Low", 0xFFEAB308, lowMA)
                )
            ),
            oddEven = CategoryResult(
                title = "Odd / Even $typeLabel %",
                subtitle = "Even Money",
                lines = listOf(
                    DataSetLine("Odd", 0xFF3B82F6, oddMA),
                    DataSetLine("Even", 0xFFF97316, evenMA)
                )
            ),
            dozens = CategoryResult(
                title = "Dozens $typeLabel %",
                subtitle = "1st / 2nd / 3rd",
                lines = listOf(
                    DataSetLine("1st", 0xFF22C55E, doz1MA),
                    DataSetLine("2nd", 0xFF3B82F6, doz2MA),
                    DataSetLine("3rd", 0xFFEC4899, doz3MA)
                )
            ),
            columns = CategoryResult(
                title = "Columns $typeLabel %",
                subtitle = "1st / 2nd / 3rd",
                lines = listOf(
                    DataSetLine("1st", 0xFF06B6D4, col1MA),
                    DataSetLine("2nd", 0xFFF43F5E, col2MA),
                    DataSetLine("3rd", 0xFFEAB308, col3MA)
                )
            )
        )
    }

    /**
     * Computes outside bet moving averages and signals for MA_TREND mode.
     */
    fun computeOutsideMA(
        chronologicalSpins: List<Int>,
        config: MAConfig,
        isUS: Boolean = false
    ): Map<String, HeatmapCalculator.OutsideHeatInfo> {
        val totalNumbers = if (isUS) 38.0 else 37.0
        val dozExpected = (12.0 / totalNumbers * 100.0).toFloat()
        val evenMoneyExpected = (18.0 / totalNumbers * 100.0).toFloat()

        val checkFns = mapOf<String, Pair<(Int) -> Boolean, Float>>(
            "doz1" to Pair({ n: Int -> n in 1..12 }, dozExpected),
            "doz2" to Pair({ n: Int -> n in 13..24 }, dozExpected),
            "doz3" to Pair({ n: Int -> n in 25..36 }, dozExpected),
            "col1" to Pair({ n: Int -> n != 0 && n != 37 && n % 3 == 1 }, dozExpected),
            "col2" to Pair({ n: Int -> n != 0 && n != 37 && n % 3 == 2 }, dozExpected),
            "col3" to Pair({ n: Int -> n != 0 && n != 37 && n % 3 == 0 }, dozExpected),
            "low" to Pair({ n: Int -> n in 1..18 }, evenMoneyExpected),
            "even" to Pair({ n: Int -> n != 0 && n != 37 && n % 2 == 0 }, evenMoneyExpected),
            "red" to Pair({ n: Int -> RED_NUMBERS.contains(n) }, evenMoneyExpected),
            "black" to Pair({ n: Int -> BLACK_NUMBERS.contains(n) }, evenMoneyExpected),
            "odd" to Pair({ n: Int -> n != 0 && n != 37 && n % 2 != 0 }, evenMoneyExpected),
            "high" to Pair({ n: Int -> n in 19..36 }, evenMoneyExpected)
        )

        val result = mutableMapOf<String, HeatmapCalculator.OutsideHeatInfo>()
        val n = chronologicalSpins.size
        for ((key, pair) in checkFns) {
            val (fn, expected) = pair
            val maSeries = calculateMA(chronologicalSpins, config, fn)
            val latestMA = maSeries.lastOrNull()
            if (latestMA == null) {
                result[key] = HeatmapCalculator.OutsideHeatInfo(
                    hitCount = 0,
                    totalSpins = n,
                    percentage = 0f,
                    color = HeatmapCalculator.COLOR_GRAY,
                    badge = "MA: --"
                )
            } else {
                val delta = latestMA - expected
                val color = if (delta > 0) {
                    val intensity = (delta / 20f).coerceIn(0f, 1f)
                    HeatmapCalculator.interpolateColor(HeatmapCalculator.COLOR_GRAY, HeatmapCalculator.COLOR_MANUAL_HOT, intensity)
                } else {
                    val intensity = (-delta / 20f).coerceIn(0f, 1f)
                    HeatmapCalculator.interpolateColor(HeatmapCalculator.COLOR_GRAY, HeatmapCalculator.COLOR_MANUAL_COLD, intensity)
                }
                val prevMA = if (maSeries.size >= 2) maSeries[maSeries.size - 2] else null
                val prevPrevMA = if (maSeries.size >= 3) maSeries[maSeries.size - 3] else null

                var trendDirection = HeatmapCalculator.TrendDirection.NONE
                var trendStrength = HeatmapCalculator.TrendStrength.NONE

                if (prevMA != null) {
                    val deltaNow = latestMA - prevMA
                    val deltaPrev = if (prevPrevMA != null) prevMA - prevPrevMA else deltaNow

                    if (Math.abs(deltaNow) >= 0.05f) {
                        if (deltaNow > 0f) {
                            trendDirection = HeatmapCalculator.TrendDirection.UP
                            trendStrength = if (prevPrevMA != null) {
                                when {
                                    // Accelerating/surging upward: slope increased noticeably, or big upward leap
                                    (deltaPrev <= 0f && deltaNow >= 1.2f) || (deltaNow > deltaPrev + 0.3f && deltaNow >= 1.0f) || deltaNow >= 2.5f ->
                                        HeatmapCalculator.TrendStrength.STRONG
                                    // Decelerating/losing steam: was rising but slope slowed down noticeably or very small gain
                                    (deltaPrev > 0f && deltaNow < deltaPrev - 0.3f) || deltaNow < 0.6f ->
                                        HeatmapCalculator.TrendStrength.WEAK
                                    else ->
                                        HeatmapCalculator.TrendStrength.MODERATE
                                }
                            } else {
                                when {
                                    deltaNow >= 2.0f -> HeatmapCalculator.TrendStrength.STRONG
                                    deltaNow >= 0.8f -> HeatmapCalculator.TrendStrength.MODERATE
                                    else -> HeatmapCalculator.TrendStrength.WEAK
                                }
                            }
                        } else {
                            trendDirection = HeatmapCalculator.TrendDirection.DOWN
                            val absNow = -deltaNow
                            val absPrev = -deltaPrev
                            trendStrength = if (prevPrevMA != null) {
                                when {
                                    // Accelerating downward: drop rate increased noticeably, or big drop
                                    (deltaPrev >= 0f && absNow >= 1.2f) || (absNow > absPrev + 0.3f && absNow >= 1.0f) || absNow >= 2.5f ->
                                        HeatmapCalculator.TrendStrength.STRONG
                                    // Decelerating downward: drop is flattening out or very small drop
                                    (deltaPrev < 0f && absNow < absPrev - 0.3f) || absNow < 0.6f ->
                                        HeatmapCalculator.TrendStrength.WEAK
                                    else ->
                                        HeatmapCalculator.TrendStrength.MODERATE
                                }
                            } else {
                                when {
                                    absNow >= 2.0f -> HeatmapCalculator.TrendStrength.STRONG
                                    absNow >= 0.8f -> HeatmapCalculator.TrendStrength.MODERATE
                                    else -> HeatmapCalculator.TrendStrength.WEAK
                                }
                            }
                        }
                    }
                }

                val sign = if (delta > 0) "+" else ""
                val arrow = when (trendDirection) {
                    HeatmapCalculator.TrendDirection.UP -> "▲"
                    HeatmapCalculator.TrendDirection.DOWN -> "▼"
                    HeatmapCalculator.TrendDirection.NONE -> "—"
                }
                val badge = "$arrow ${latestMA.roundToInt()}% (${sign}${delta.roundToInt()}%)"

                result[key] = HeatmapCalculator.OutsideHeatInfo(
                    hitCount = (latestMA * n / 100f).roundToInt(),
                    totalSpins = n,
                    percentage = latestMA,
                    color = color,
                    badge = badge,
                    trendDirection = trendDirection,
                    trendStrength = trendStrength
                )
            }
        }
        return result
    }
}

