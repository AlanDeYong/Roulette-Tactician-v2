package com.roulettetactician.logic

/**
 * Trigger Calculation and Streak Monitoring Engine for Roulette.
 * Computes both Hot Streaks (consecutive hits/repeats) and Cold Streaks (consecutive misses/sleepers) for:
 * - Red vs Black
 * - High (19-36) vs Low (1-18)
 * - Odd vs Even
 * - Dozens (1st: 1-12, 2nd: 13-24, 3rd: 25-36)
 * - Columns (1st: 1,4,7..., 2nd: 2,5,8..., 3rd: 3,6,9...)
 *
 * Evaluates triggers against customizable user thresholds for both Hot and Cold streaks.
 */
object TriggerCalculator {

    val RED_NUMBERS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)
    val BLACK_NUMBERS = setOf(2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35)

    data class OutcomeColdInfo(
        val label: String,
        val coldSpins: Int
    )

    data class StreakStatus(
        val categoryName: String,
        val currentOutcome: String,
        val currentStreak: Int,
        val threshold: Int,
        val isHotTriggered: Boolean,
        val coldestOutcome: String,
        val coldestStreak: Int,
        val coldThreshold: Int,
        val isColdTriggered: Boolean,
        val coldBreakdown: List<OutcomeColdInfo> = emptyList(),
        val isTriggered: Boolean = isHotTriggered,
        val isAnyTriggered: Boolean = isHotTriggered || isColdTriggered,
        val alertMessage: String? = null,
        val historyStreakSummary: String = ""
    )

    data class TriggerEvaluationResult(
        val redBlack: StreakStatus,
        val highLow: StreakStatus,
        val oddEven: StreakStatus,
        val dozens: StreakStatus,
        val columns: StreakStatus,
        val hasAnyTriggered: Boolean,
        val totalTriggeredCount: Int
    )

    data class TriggerThresholds(
        val redBlack: Int = 4,
        val highLow: Int = 4,
        val oddEven: Int = 4,
        val dozens: Int = 3,
        val columns: Int = 3,
        val coldRedBlack: Int = 5,
        val coldHighLow: Int = 5,
        val coldOddEven: Int = 5,
        val coldDozens: Int = 8,
        val coldColumns: Int = 8
    )

    /**
     * Calculates the cold streak (consecutive misses from the latest spin backwards) for an outcome.
     */
    fun computeColdStreak(spins: List<Int>, isHit: (Int) -> Boolean): Int {
        if (spins.isEmpty()) return 0
        var count = 0
        for (i in spins.indices.reversed()) {
            if (isHit(spins[i])) {
                break
            }
            count++
        }
        return count
    }

    /**
     * Evaluates all trigger conditions against the provided spin history (chronological order: oldest to newest).
     */
    fun evaluate(spins: List<Int>, thresholds: TriggerThresholds): TriggerEvaluationResult {
        val rbStatus = evaluateRedBlack(spins, thresholds.redBlack, thresholds.coldRedBlack)
        val hlStatus = evaluateHighLow(spins, thresholds.highLow, thresholds.coldHighLow)
        val oeStatus = evaluateOddEven(spins, thresholds.oddEven, thresholds.coldOddEven)
        val dzStatus = evaluateDozens(spins, thresholds.dozens, thresholds.coldDozens)
        val colStatus = evaluateColumns(spins, thresholds.columns, thresholds.coldColumns)

        val all = listOf(rbStatus, hlStatus, oeStatus, dzStatus, colStatus)
        val triggeredCount = all.count { it.isAnyTriggered }

        return TriggerEvaluationResult(
            redBlack = rbStatus,
            highLow = hlStatus,
            oddEven = oeStatus,
            dozens = dzStatus,
            columns = colStatus,
            hasAnyTriggered = triggeredCount > 0,
            totalTriggeredCount = triggeredCount
        )
    }

    private fun evaluateRedBlack(spins: List<Int>, threshold: Int, coldThreshold: Int): StreakStatus {
        val redCold = computeColdStreak(spins) { it != 0 && it != 37 && RED_NUMBERS.contains(it) }
        val blackCold = computeColdStreak(spins) { it != 0 && it != 37 && BLACK_NUMBERS.contains(it) }
        val breakdown = listOf(OutcomeColdInfo("Red", redCold), OutcomeColdInfo("Black", blackCold))

        val (coldestName, coldestCount) = if (redCold >= blackCold) "Red" to redCold else "Black" to blackCold
        val isColdTriggered = spins.isNotEmpty() && coldestCount >= coldThreshold

        if (spins.isEmpty()) {
            return StreakStatus(
                categoryName = "Red / Black",
                currentOutcome = "None",
                currentStreak = 0,
                threshold = threshold,
                isHotTriggered = false,
                coldestOutcome = "None",
                coldestStreak = 0,
                coldThreshold = coldThreshold,
                isColdTriggered = false,
                coldBreakdown = breakdown
            )
        }

        val lastSpin = spins.last()
        val lastColor = when {
            lastSpin == 0 || lastSpin == 37 -> "Zero"
            RED_NUMBERS.contains(lastSpin) -> "Red"
            BLACK_NUMBERS.contains(lastSpin) -> "Black"
            else -> "None"
        }

        var hotStreak = 0
        if (lastColor != "Zero" && lastColor != "None") {
            for (i in spins.indices.reversed()) {
                val num = spins[i]
                val color = when {
                    num == 0 || num == 37 -> "Zero"
                    RED_NUMBERS.contains(num) -> "Red"
                    BLACK_NUMBERS.contains(num) -> "Black"
                    else -> "None"
                }
                if (color == lastColor) hotStreak++ else break
            }
        }

        val isHotTriggered = hotStreak >= threshold && hotStreak > 0
        val isAnyTriggered = isHotTriggered || isColdTriggered
        val alert = when {
            isHotTriggered && isColdTriggered -> "HOT: ${hotStreak}x $lastColor! COLD: $coldestName ($coldestCount missed)!"
            isHotTriggered -> "$hotStreak consecutive $lastColor!"
            isColdTriggered -> "$coldestName cold ($coldestCount missed)!"
            else -> null
        }

        return StreakStatus(
            categoryName = "Red / Black",
            currentOutcome = lastColor,
            currentStreak = hotStreak,
            threshold = threshold,
            isHotTriggered = isHotTriggered,
            coldestOutcome = coldestName,
            coldestStreak = coldestCount,
            coldThreshold = coldThreshold,
            isColdTriggered = isColdTriggered,
            coldBreakdown = breakdown,
            isTriggered = isHotTriggered,
            isAnyTriggered = isAnyTriggered,
            alertMessage = alert
        )
    }

    private fun evaluateHighLow(spins: List<Int>, threshold: Int, coldThreshold: Int): StreakStatus {
        val highCold = computeColdStreak(spins) { it != 0 && it != 37 && it in 19..36 }
        val lowCold = computeColdStreak(spins) { it != 0 && it != 37 && it in 1..18 }
        val breakdown = listOf(OutcomeColdInfo("High", highCold), OutcomeColdInfo("Low", lowCold))

        val (coldestName, coldestCount) = if (highCold >= lowCold) "High" to highCold else "Low" to lowCold
        val isColdTriggered = spins.isNotEmpty() && coldestCount >= coldThreshold

        if (spins.isEmpty()) {
            return StreakStatus(
                categoryName = "High / Low",
                currentOutcome = "None",
                currentStreak = 0,
                threshold = threshold,
                isHotTriggered = false,
                coldestOutcome = "None",
                coldestStreak = 0,
                coldThreshold = coldThreshold,
                isColdTriggered = false,
                coldBreakdown = breakdown
            )
        }

        val lastSpin = spins.last()
        val lastHL = when {
            lastSpin == 0 || lastSpin == 37 -> "Zero"
            lastSpin in 19..36 -> "High"
            lastSpin in 1..18 -> "Low"
            else -> "None"
        }

        var hotStreak = 0
        if (lastHL != "Zero" && lastHL != "None") {
            for (i in spins.indices.reversed()) {
                val num = spins[i]
                val hl = when {
                    num == 0 || num == 37 -> "Zero"
                    num in 19..36 -> "High"
                    num in 1..18 -> "Low"
                    else -> "None"
                }
                if (hl == lastHL) hotStreak++ else break
            }
        }

        val isHotTriggered = hotStreak >= threshold && hotStreak > 0
        val isAnyTriggered = isHotTriggered || isColdTriggered
        val alert = when {
            isHotTriggered && isColdTriggered -> "HOT: ${hotStreak}x $lastHL! COLD: $coldestName ($coldestCount missed)!"
            isHotTriggered -> "$hotStreak consecutive $lastHL!"
            isColdTriggered -> "$coldestName cold ($coldestCount missed)!"
            else -> null
        }

        return StreakStatus(
            categoryName = "High / Low",
            currentOutcome = lastHL,
            currentStreak = hotStreak,
            threshold = threshold,
            isHotTriggered = isHotTriggered,
            coldestOutcome = coldestName,
            coldestStreak = coldestCount,
            coldThreshold = coldThreshold,
            isColdTriggered = isColdTriggered,
            coldBreakdown = breakdown,
            isTriggered = isHotTriggered,
            isAnyTriggered = isAnyTriggered,
            alertMessage = alert
        )
    }

    private fun evaluateOddEven(spins: List<Int>, threshold: Int, coldThreshold: Int): StreakStatus {
        val oddCold = computeColdStreak(spins) { it != 0 && it != 37 && it % 2 != 0 }
        val evenCold = computeColdStreak(spins) { it != 0 && it != 37 && it % 2 == 0 }
        val breakdown = listOf(OutcomeColdInfo("Odd", oddCold), OutcomeColdInfo("Even", evenCold))

        val (coldestName, coldestCount) = if (oddCold >= evenCold) "Odd" to oddCold else "Even" to evenCold
        val isColdTriggered = spins.isNotEmpty() && coldestCount >= coldThreshold

        if (spins.isEmpty()) {
            return StreakStatus(
                categoryName = "Odd / Even",
                currentOutcome = "None",
                currentStreak = 0,
                threshold = threshold,
                isHotTriggered = false,
                coldestOutcome = "None",
                coldestStreak = 0,
                coldThreshold = coldThreshold,
                isColdTriggered = false,
                coldBreakdown = breakdown
            )
        }

        val lastSpin = spins.last()
        val lastOE = when {
            lastSpin == 0 || lastSpin == 37 -> "Zero"
            lastSpin % 2 != 0 -> "Odd"
            else -> "Even"
        }

        var hotStreak = 0
        if (lastOE != "Zero") {
            for (i in spins.indices.reversed()) {
                val num = spins[i]
                val oe = when {
                    num == 0 || num == 37 -> "Zero"
                    num % 2 != 0 -> "Odd"
                    else -> "Even"
                }
                if (oe == lastOE) hotStreak++ else break
            }
        }

        val isHotTriggered = hotStreak >= threshold && hotStreak > 0
        val isAnyTriggered = isHotTriggered || isColdTriggered
        val alert = when {
            isHotTriggered && isColdTriggered -> "HOT: ${hotStreak}x $lastOE! COLD: $coldestName ($coldestCount missed)!"
            isHotTriggered -> "$hotStreak consecutive $lastOE!"
            isColdTriggered -> "$coldestName cold ($coldestCount missed)!"
            else -> null
        }

        return StreakStatus(
            categoryName = "Odd / Even",
            currentOutcome = lastOE,
            currentStreak = hotStreak,
            threshold = threshold,
            isHotTriggered = isHotTriggered,
            coldestOutcome = coldestName,
            coldestStreak = coldestCount,
            coldThreshold = coldThreshold,
            isColdTriggered = isColdTriggered,
            coldBreakdown = breakdown,
            isTriggered = isHotTriggered,
            isAnyTriggered = isAnyTriggered,
            alertMessage = alert
        )
    }

    private fun evaluateDozens(spins: List<Int>, threshold: Int, coldThreshold: Int): StreakStatus {
        val doz1Cold = computeColdStreak(spins) { it != 0 && it != 37 && it in 1..12 }
        val doz2Cold = computeColdStreak(spins) { it != 0 && it != 37 && it in 13..24 }
        val doz3Cold = computeColdStreak(spins) { it != 0 && it != 37 && it in 25..36 }
        val breakdown = listOf(
            OutcomeColdInfo("1st", doz1Cold),
            OutcomeColdInfo("2nd", doz2Cold),
            OutcomeColdInfo("3rd", doz3Cold)
        )

        val coldest = breakdown.maxByOrNull { it.coldSpins } ?: OutcomeColdInfo("None", 0)
        val coldestName = when (coldest.label) {
            "1st" -> "1st Dozen"
            "2nd" -> "2nd Dozen"
            "3rd" -> "3rd Dozen"
            else -> coldest.label
        }
        val isColdTriggered = spins.isNotEmpty() && coldest.coldSpins >= coldThreshold

        if (spins.isEmpty()) {
            return StreakStatus(
                categoryName = "Dozens",
                currentOutcome = "None",
                currentStreak = 0,
                threshold = threshold,
                isHotTriggered = false,
                coldestOutcome = "None",
                coldestStreak = 0,
                coldThreshold = coldThreshold,
                isColdTriggered = false,
                coldBreakdown = breakdown
            )
        }

        val lastSpin = spins.last()
        val lastDozen = when {
            lastSpin == 0 || lastSpin == 37 -> "Zero"
            lastSpin in 1..12 -> "1st Dozen"
            lastSpin in 13..24 -> "2nd Dozen"
            lastSpin in 25..36 -> "3rd Dozen"
            else -> "None"
        }

        var hotStreak = 0
        if (lastDozen != "Zero" && lastDozen != "None") {
            for (i in spins.indices.reversed()) {
                val num = spins[i]
                val dozen = when {
                    num == 0 || num == 37 -> "Zero"
                    num in 1..12 -> "1st Dozen"
                    num in 13..24 -> "2nd Dozen"
                    num in 25..36 -> "3rd Dozen"
                    else -> "None"
                }
                if (dozen == lastDozen) hotStreak++ else break
            }
        }

        val isHotTriggered = hotStreak >= threshold && hotStreak > 0
        val isAnyTriggered = isHotTriggered || isColdTriggered
        val alert = when {
            isHotTriggered && isColdTriggered -> "HOT: ${hotStreak}x $lastDozen! COLD: $coldestName (${coldest.coldSpins} missed)!"
            isHotTriggered -> "$hotStreak consecutive in $lastDozen!"
            isColdTriggered -> "$coldestName cold (${coldest.coldSpins} missed)!"
            else -> null
        }

        return StreakStatus(
            categoryName = "Dozens",
            currentOutcome = lastDozen,
            currentStreak = hotStreak,
            threshold = threshold,
            isHotTriggered = isHotTriggered,
            coldestOutcome = coldestName,
            coldestStreak = coldest.coldSpins,
            coldThreshold = coldThreshold,
            isColdTriggered = isColdTriggered,
            coldBreakdown = breakdown,
            isTriggered = isHotTriggered,
            isAnyTriggered = isAnyTriggered,
            alertMessage = alert
        )
    }

    private fun evaluateColumns(spins: List<Int>, threshold: Int, coldThreshold: Int): StreakStatus {
        val col1Cold = computeColdStreak(spins) { it != 0 && it != 37 && it % 3 == 1 }
        val col2Cold = computeColdStreak(spins) { it != 0 && it != 37 && it % 3 == 2 }
        val col3Cold = computeColdStreak(spins) { it != 0 && it != 37 && it % 3 == 0 }
        val breakdown = listOf(
            OutcomeColdInfo("C1", col1Cold),
            OutcomeColdInfo("C2", col2Cold),
            OutcomeColdInfo("C3", col3Cold)
        )

        val coldest = breakdown.maxByOrNull { it.coldSpins } ?: OutcomeColdInfo("None", 0)
        val coldestName = when (coldest.label) {
            "C1" -> "1st Column"
            "C2" -> "2nd Column"
            "C3" -> "3rd Column"
            else -> coldest.label
        }
        val isColdTriggered = spins.isNotEmpty() && coldest.coldSpins >= coldThreshold

        if (spins.isEmpty()) {
            return StreakStatus(
                categoryName = "Columns",
                currentOutcome = "None",
                currentStreak = 0,
                threshold = threshold,
                isHotTriggered = false,
                coldestOutcome = "None",
                coldestStreak = 0,
                coldThreshold = coldThreshold,
                isColdTriggered = false,
                coldBreakdown = breakdown
            )
        }

        val lastSpin = spins.last()
        val lastCol = when {
            lastSpin == 0 || lastSpin == 37 -> "Zero"
            lastSpin % 3 == 1 -> "1st Column"
            lastSpin % 3 == 2 -> "2nd Column"
            lastSpin % 3 == 0 -> "3rd Column"
            else -> "None"
        }

        var hotStreak = 0
        if (lastCol != "Zero" && lastCol != "None") {
            for (i in spins.indices.reversed()) {
                val num = spins[i]
                val col = when {
                    num == 0 || num == 37 -> "Zero"
                    num % 3 == 1 -> "1st Column"
                    num % 3 == 2 -> "2nd Column"
                    num % 3 == 0 -> "3rd Column"
                    else -> "None"
                }
                if (col == lastCol) hotStreak++ else break
            }
        }

        val isHotTriggered = hotStreak >= threshold && hotStreak > 0
        val isAnyTriggered = isHotTriggered || isColdTriggered
        val alert = when {
            isHotTriggered && isColdTriggered -> "HOT: ${hotStreak}x $lastCol! COLD: $coldestName (${coldest.coldSpins} missed)!"
            isHotTriggered -> "$hotStreak consecutive in $lastCol!"
            isColdTriggered -> "$coldestName cold (${coldest.coldSpins} missed)!"
            else -> null
        }

        return StreakStatus(
            categoryName = "Columns",
            currentOutcome = lastCol,
            currentStreak = hotStreak,
            threshold = threshold,
            isHotTriggered = isHotTriggered,
            coldestOutcome = coldestName,
            coldestStreak = coldest.coldSpins,
            coldThreshold = coldThreshold,
            isColdTriggered = isColdTriggered,
            coldBreakdown = breakdown,
            isTriggered = isHotTriggered,
            isAnyTriggered = isAnyTriggered,
            alertMessage = alert
        )
    }
}
