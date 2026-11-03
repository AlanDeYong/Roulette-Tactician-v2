package com.roulettetactician.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BetTrendCalculatorTest {

    @Test
    fun testCandidateGenerationEU() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = false)
        assertTrue(candidates.size > 130)

        val straights = candidates.filter { it.type == "straight" }
        assertEquals(37, straights.size) // 0 to 36

        val dozens = candidates.filter { it.type == "dozen" }
        assertEquals(3, dozens.size)

        val columns = candidates.filter { it.type == "column" }
        assertEquals(3, columns.size)

        val evenMoney = candidates.filter { it.type == "even_money" }
        assertEquals(6, evenMoney.size)
    }

    @Test
    fun testCandidateGenerationUS() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = true)
        val straights = candidates.filter { it.type == "straight" }
        assertEquals(38, straights.size) // 0 to 36 + 00 (37)
        assertTrue(candidates.any { it.betId == "num-00" })
    }

    @Test
    fun testWheelRotorDistance() {
        // Single Zero rotor: 0 is adjacent to 32 and 26
        val distTo32 = BetTrendCalculator.getWheelDistance(0, 32, isUS = false)
        assertEquals(1, distTo32)

        val distTo26 = BetTrendCalculator.getWheelDistance(0, 26, isUS = false)
        assertEquals(1, distTo26)

        val distToSelf = BetTrendCalculator.getWheelDistance(17, 17, isUS = false)
        assertEquals(0, distToSelf)
    }

    @Test
    fun testBitmaskRepresentation() {
        val mask17 = BetTrendCalculator.toBitmask(listOf(17))
        assertEquals(1L shl 17, mask17)

        val maskDoz1 = BetTrendCalculator.toBitmask((1..12).toList())
        assertTrue((maskDoz1 and (1L shl 1)) != 0L)
        assertTrue((maskDoz1 and (1L shl 12)) != 0L)
        assertFalse((maskDoz1 and (1L shl 13)) != 0L)
        assertFalse((maskDoz1 and (1L shl 0)) != 0L)
    }

    @Test
    fun testColdNumberElimination() {
        // 17 hits 5 times, 20 hits 3 times, 0 hits 2 times, rest 0 times
        val spins = listOf(17, 17, 17, 17, 17, 20, 20, 20, 0, 0)
        val cold5 = BetTrendCalculator.getColdBannedNumbers(spins, count = 5, isUS = false)

        assertEquals(5, cold5.size)
        // 17 and 20 must NOT be in cold 5
        assertFalse(cold5.contains(17))
        assertFalse(cold5.contains(20))
    }

    @Test
    fun testColdNumberDroughtAndSectorTieBreaking() {
        // 1. Drought length tie-breaker:
        // All numbers 0..36 hit twice EXCEPT 35 (hit once at index 0 -> drought 71)
        // and 1 (hit once at the very last spin -> drought 0).
        val baseTwice = (0..36).filter { it != 1 && it != 35 }.flatMap { listOf(it, it) }
        val spinsWithDroughtTie = listOf(35) + baseTwice + listOf(1)

        val cold1 = BetTrendCalculator.getColdBannedNumbers(spinsWithDroughtTie, count = 1, isUS = false)
        assertEquals("Number 35 (longest drought) must be selected over Number 1 (just hit)", setOf(35), cold1)

        // 2. Unseen sector isolation tie-breaker:
        // If 32 and 26 (the two immediate rotor neighbors of 0) just hit repeatedly,
        // unseen pocket 0 is in a hot wheel sector and must NOT be banned ahead of isolated unseen pockets.
        val sectorSpins = listOf(32, 26, 32, 26, 15, 3)
        val cold5Unseen = BetTrendCalculator.getColdBannedNumbers(sectorSpins, count = 5, isUS = false)
        assertEquals(5, cold5Unseen.size)
        assertFalse("Unseen pocket 0 flanked by hot rotor neighbors 32 & 26 should not be banned first", cold5Unseen.contains(0))
    }

    @Test
    fun testMartingaleProgression() {
        val tracker = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.MARTINGALE)
        assertEquals(1, tracker.getMultiplier())

        tracker.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, tracker.getMultiplier())

        tracker.record(won = false, netGainedUnits = -2.0)
        assertEquals(4, tracker.getMultiplier())

        tracker.record(won = true, netGainedUnits = 4.0)
        assertEquals(1, tracker.getMultiplier()) // Resets on win
    }

    @Test
    fun testFibonacciProgression() {
        val tracker = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.FIBONACCI)
        assertEquals(1, tracker.getMultiplier())

        tracker.record(won = false, netGainedUnits = -1.0)
        assertEquals(1, tracker.getMultiplier()) // fib[1] = 1

        tracker.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, tracker.getMultiplier()) // fib[2] = 2

        tracker.record(won = false, netGainedUnits = -2.0)
        assertEquals(3, tracker.getMultiplier()) // fib[3] = 3

        tracker.record(won = true, netGainedUnits = 3.0)
        assertEquals(1, tracker.getMultiplier()) // drops back 2 steps: fib[1] = 1
    }

    @Test
    fun testProgressionPreservedOnPush() {
        // 1. Martingale should not double on push
        val martin = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.MARTINGALE)
        assertEquals(1, martin.getMultiplier())
        martin.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, martin.getMultiplier())
        // Push occurs
        martin.record(won = false, netGainedUnits = 0.0, isPush = true)
        assertEquals(2, martin.getMultiplier()) // Preserves same bet amount
        martin.record(won = false, netGainedUnits = 0.0, isPush = true)
        assertEquals(2, martin.getMultiplier()) // Still preserves same bet amount
        // Loss occurs after pushes
        martin.record(won = false, netGainedUnits = -2.0)
        assertEquals(4, martin.getMultiplier())

        // 2. Paroli (positive progression) should not reset win streak on push
        val paroli = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.PAROLI)
        assertEquals(1, paroli.getMultiplier())
        paroli.record(won = true, netGainedUnits = 1.0)
        assertEquals(2, paroli.getMultiplier())
        // Push occurs
        paroli.record(won = false, netGainedUnits = 0.0, isPush = true)
        assertEquals(2, paroli.getMultiplier()) // Should not reset to 1
        paroli.record(won = true, netGainedUnits = 2.0)
        assertEquals(4, paroli.getMultiplier()) // Advances on win

        // 3. Fibonacci should not advance or drop on push
        val fib = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.FIBONACCI)
        fib.record(won = false, netGainedUnits = -1.0)
        fib.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, fib.getMultiplier())
        fib.record(won = false, netGainedUnits = 0.0, isPush = true)
        assertEquals(2, fib.getMultiplier()) // Unchanged
    }

    @Test
    fun testAutoOptimizeSimulationPerformance() {
        // Generate 40 sample spins
        val sampleSpins = listOf(
            17, 20, 17, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26, 0
        )

        val startTime = System.currentTimeMillis()
        val recommendation = BetTrendCalculator.optimize(
            spins = sampleSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Execution should be fast (elapsed: ${elapsed}ms)", elapsed < 500)
        assertNotNull(recommendation)
        assertNotNull(recommendation.bestProgression)
        assertTrue(recommendation.recommendedBets.isNotEmpty())
        assertTrue(recommendation.nextTotalStake > 0)
        assertTrue(recommendation.spinLogs.isNotEmpty())
        assertTrue("numberWeights must be populated", recommendation.numberWeights.isNotEmpty())
        assertEquals(37, recommendation.numberWeights.size)
        // Most recent spin (0) should have high weight due to recency
        val weight0 = recommendation.numberWeights[0] ?: 0.0
        assertTrue("Most recent spin 0 must have significant weight", weight0 > 0.8)
    }

    @Test
    fun testEquityTrajectoryAndLedgerPlotsAllLoadedSpins() {
        // Create 100 sample spins
        val hundredSpins = (1..100).map { (it * 7) % 37 }

        val result = BetTrendCalculator.optimize(
            spins = hundredSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.FLAT,
            family = BetTrendCalculator.BetFamily.OUTSIDE,
            coldLimit = 0,
            lookbackWindow = 25,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 5.0
        )

        assertEquals("Equity points must cover all 100 loaded spins", 100, result.equityPoints.size)
        assertEquals("Spin ledger records must cover all 100 loaded spins", 100, result.spinLogs.size)
        assertEquals("First equity point spinIndex must be 1", 1, result.equityPoints.first().spinIndex)
        assertEquals("Last equity point spinIndex must be 100", 100, result.equityPoints.last().spinIndex)
        assertEquals("First spin log record spinIndex must be 100 (newest first)", 100, result.spinLogs.first().spinIndex)
        assertEquals("Last spin log record spinIndex must be 1 (oldest last)", 1, result.spinLogs.last().spinIndex)
    }

    @Test
    fun testRecommendationIsMeantForNextSpin() {
        // Suppose 3 spins have occurred, ending with 17
        val spins = listOf(20, 20, 17)

        val result = BetTrendCalculator.optimize(
            spins = spins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.MARTINGALE,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldLimit = 0,
            lookbackWindow = 10,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )

        // Spin ledger records must reflect rounds 1..3 that were played
        assertEquals(3, result.spinLogs.size)
        // Newest spin log record is spin 3 (spunNumber 17)
        assertEquals(17, result.spinLogs.first().spunNumber)

        // The recommended bets for the NEXT spin (spin 4) must take spin 3 (17) into account
        val recommendedNumbers = result.recommendedBets.flatMap { it.bet.numbers }.toSet()
        assertTrue("Recommended bets for the next spin must include high-frequency/recent numbers like 17 or 20",
            recommendedNumbers.contains(17) || recommendedNumbers.contains(20))

        // Next multiplier must be the tracker's multiplier for spin 4
        assertTrue("Next multiplier must be >= 1", result.currentMultiplier >= 1)
        assertTrue("Next total stake must be > 0", result.nextTotalStake > 0)
    }

    @Test
    fun testWheelSequenceIntegrity() {
        val euSeq = WheelLayouts.getWheelSequence(com.roulettetactician.ui.model.TableType.EU)
        assertEquals(37, euSeq.size)
        assertEquals(37, euSeq.distinct().size)
        assertEquals(0, euSeq[0]) // Starts with 0

        val usSeq = WheelLayouts.getWheelSequence(com.roulettetactician.ui.model.TableType.US)
        assertEquals(38, usSeq.size)
        assertEquals(38, usSeq.distinct().size)
        assertTrue(usSeq.contains(37)) // Contains 00 (represented as 37)
    }

    @Test
    fun testFindMostProfitableConfig() {
        val sampleSpins = listOf(
            17, 20, 17, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23,
            10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26, 0
        )

        val (bestFamily, bestCold) = BetTrendCalculator.findMostProfitableConfig(
            spins = sampleSpins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.AUTO,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )

        assertTrue(bestFamily in listOf(BetTrendCalculator.BetFamily.INSIDE, BetTrendCalculator.BetFamily.OUTSIDE, BetTrendCalculator.BetFamily.COMBINED))
        assertTrue(bestCold in listOf(0, 3, 5, 7, 10))
    }

    @Test
    fun testSpinLedgerLogsProgressionName() {
        val sampleSpins = listOf(17, 20, 17, 32, 15, 19, 4, 21, 2, 25)
        val result = BetTrendCalculator.optimize(
            spins = sampleSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.MARTINGALE,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldLimit = 0,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )

        assertTrue(result.spinLogs.isNotEmpty())
        result.spinLogs.forEach { log ->
            assertEquals("Martingale", log.progressionUsed)
        }
    }

    @Test
    fun testCriteriaPrioritizationAffectsOptimization() {
        val historyStr = "25,3,30,6,8,15,35,10,20,15,13,3,9,22,34,22,18,25,23,3,26,8,20,7,23,30,0,17,25,7,4,36,2,33,23,27,30,26,30,17,30,25,12,17,19,34,4,35,11,20,27,17,12,16,9,17,17,3,16,34,2,30,3,23,23,14,32,2,19,18,24,15,26,33,16,23,30,6,19,18,32,3,28,25,3,17,3,16,3,13,20,19,33,33,13,30,22,0,16,22,12,17,8,35,13,6,28,24,28,14,34,26,2,17,0,3,20,21,1,16,2,5,36,36,36,1,9,30,18,13,15,19,12,25,4,34,9,27,34,12,13,3,8,2,25,30,21,36,36,3,7,29,28,30,34,26,3,23,30,5,22,6,14,25,26,15,23,10,33,28,4,2,3,36,28,0,34,36,21,22,33,0,26,9,28,20,20,3,9,13,12,20,15,6,35,32,28,11,15,35,23,9,30,32,21,19,8,17,22,13,30,24,21,28,11,26,4,14,29,13,22,14,11,5,9,30,22,16,7,12,26,18"
        val allSpins = historyStr.split(",").map { it.toInt() }

        // Optimize with Profit Primary + Drawdown Secondary
        val profitPrimary = BetTrendCalculator.optimize(
            spins = allSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.PNL,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD
        )

        // Optimize with Drawdown Primary + Profit Secondary
        val ddPrimary = BetTrendCalculator.optimize(
            spins = allSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.PNL
        )

        assertNotNull(profitPrimary.bestProgression)
        assertNotNull(ddPrimary.bestProgression)
        // Drawdown-primary should prioritize lower drawdown over extreme profit
        assertTrue(ddPrimary.maxDrawdown <= profitPrimary.maxDrawdown || ddPrimary.bestProgression != profitPrimary.bestProgression)
    }

    @Test
    fun testFindOptimalConfigRespectsCriteria() {
        val historyStr = "25,3,30,6,8,15,35,10,20,15,13,3,9,22,34,22,18,25,23,3,26,8,20,7,23,30,0,17,25,7,4,36,2,33,23,27,30,26,30,17,30,25,12,17,19,34,4,35,11,20,27,17,12,16,9,17,17,3,16,34,2,30,3,23,23,14,32,2,19,18,24,15,26,33,16,23,30,6,19,18,32,3,28,25,3,17,3,16,3,13,20,19,33,33,13,30,22,0,16,22,12,17,8,35,13,6,28,24,28,14,34,26,2,17,0,3,20,21,1,16,2,5,36,36,36,1,9,30,18,13,15,19,12,25,4,34,9,27,34,12,13,3,8,2,25,30,21,36,36,3,7,29,28,30,34,26,3,23,30,5,22,6,14,25,26,15,23,10,33,28,4,2,3,36,28,0,34,36,21,22,33,0,26,9,28,20,20,3,9,13,12,20,15,6,35,32,28,11,15,35,23,9,30,32,21,19,8,17,22,13,30,24,21,28,11,26,4,14,29,13,22,14,11,5,9,30,22,16,7,12,26,18"
        val allSpins = historyStr.split(",").map { it.toInt() }

        // When PNL is #1 priority, it evaluates all families and returns the best profit performer
        val (pnlFamily, pnlCold) = BetTrendCalculator.findOptimalConfig(
            spins = allSpins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.AUTO,
            lookbackWindow = 37,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.PNL,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.HIT_RATE
        )
        assertNotNull(pnlFamily)
        assertTrue(pnlCold in listOf(0, 3, 5, 7))

        // When MAX_DD is #1 priority and PNL is #2, lowest drawdown strictly dominates
        val (ddFamily, ddCold) = BetTrendCalculator.findOptimalConfig(
            spins = allSpins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.AUTO,
            lookbackWindow = 37,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.PNL
        )
        assertNotNull(ddFamily)
        assertTrue(ddCold in listOf(0, 3, 5, 7))
        assertEquals(BetTrendCalculator.BetFamily.OUTSIDE, ddFamily)
    }

    @Test
    fun testColdRulesBehavior() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = false)
        // Cold number set: let 17 and 20 be cold
        val coldBanned = setOf(17, 20)

        // Mock frequencies: give 17 a very high frequency/weight, 32 high, rest 0.1
        val freqs = mutableMapOf<Int, Double>()
        for (i in 0..36) {
            freqs[i] = 0.1
        }
        freqs[17] = 10.0 // Highest momentum but is cold
        freqs[20] = 8.0  // Second highest momentum but is cold
        freqs[32] = 5.0  // Good momentum, clean (not cold)

        // 1. STRICT: Bet covering 17 must NEVER be selected
        val strictInside = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = coldBanned,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldRule = BetTrendCalculator.ColdRule.STRICT
        )
        assertTrue(strictInside.isNotEmpty())
        assertTrue("Strict mode must exclude any bet with cold numbers", strictInside.none { it.numbers.contains(17) || it.numbers.contains(20) })

        // 2. PENALTY: Outside bet covering 17 (e.g. Dozen 2 or Odd/Black) can still be selected if penalized score beats alternatives
        val penaltyOutside = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = coldBanned,
            family = BetTrendCalculator.BetFamily.OUTSIDE,
            coldRule = BetTrendCalculator.ColdRule.PENALTY
        )
        assertTrue(penaltyOutside.isNotEmpty())

        // 3. WATERFALL: When all available candidates touch cold numbers, Waterfall selects least-cold candidates instead of returning empty
        val onlyColdCandidates = candidates.filter { it.numbers.contains(17) || it.numbers.contains(20) }
        val waterfallResult = BetTrendCalculator.selectOptimalBets(
            candidates = onlyColdCandidates,
            freqs = freqs,
            coldBanned = coldBanned,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldRule = BetTrendCalculator.ColdRule.WATERFALL
        )
        assertTrue("Waterfall must fall back to available candidates if 0-cold are unavailable", waterfallResult.isNotEmpty())

        // Under Strict mode with only cold candidates, it must return empty
        val strictResultOnCold = BetTrendCalculator.selectOptimalBets(
            candidates = onlyColdCandidates,
            freqs = freqs,
            coldBanned = coldBanned,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldRule = BetTrendCalculator.ColdRule.STRICT
        )
        assertTrue("Strict mode must return empty when all candidates are cold", strictResultOnCold.isEmpty())

        // 4. HYBRID: Inside bets strictly ban cold numbers; Outside bets use penalty
        val hybridInside = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = coldBanned,
            family = BetTrendCalculator.BetFamily.INSIDE,
            coldRule = BetTrendCalculator.ColdRule.HYBRID
        )
        assertTrue("Hybrid inside bets must strictly ban cold numbers", hybridInside.none { it.numbers.contains(17) || it.numbers.contains(20) })
    }

    @Test
    fun testEqualizePayoutInside() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = false)
        val freqs = (0..36).associateWith { 1.0 }

        val equalizedBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.INSIDE,
            equalizePayout = true
        )

        assertTrue(equalizedBets.isNotEmpty())
        for (b in equalizedBets) {
            // Every inside bet must yield exact 36 gross units on a win
            val grossReturn = b.baseUnits * (b.payoutRatio + 1)
            assertEquals("Every equalized inside bet must pay out 36 gross units", 36, grossReturn)
        }
    }

    @Test
    fun testPushOrProfitOutsideAndCombined() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = false)
        val freqs = (0..36).associateWith { 1.0 }

        // 1. Outside: Push or Profit must only choose 2 Dozens/Columns (no conflicting 3rd bet)
        val outsideBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.OUTSIDE,
            pushOrProfit = true
        )
        assertEquals(2, outsideBets.size)
        assertTrue("All outside bets must be dozens or columns for 2:1 profit guarantee", outsideBets.all { it.type == "dozen" || it.type == "column" })

        // Total stake = 2 units. Hit on either dozen yields 1 * (2 + 1) = 3 units -> Net +1 unit profit!
        val totalStake = outsideBets.sumOf { it.baseUnits }
        assertEquals(2, totalStake)

        // 2. Combined: Outside bet must be sized to cover the entire spin's inside + outside stake
        val combinedBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            pushOrProfit = true,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )
        val insideBets = combinedBets.filter { it.isInside }
        val outsideBet = combinedBets.find { !it.isInside }

        assertNotNull(outsideBet)
        val totalInsideStake = insideBets.sumOf { it.baseUnits * 1.0 }
        val outsideStake = outsideBet!!.baseUnits * 1.0
        val totalCombinedStake = totalInsideStake + outsideStake

        // When only the outside bet hits:
        val outsideGrossReturn = outsideStake * (outsideBet.payoutRatio + 1)
        assertTrue(
            "Outside hit must cover the full spin stake (Push or Profit): gross $outsideGrossReturn >= total $totalCombinedStake",
            outsideGrossReturn >= totalCombinedStake
        )
    }

    @Test
    fun testDeficitAwareCapAndCapitalRiskCeiling() {
        val candidates = BetTrendCalculator.generateAllCandidates(isUS = false)
        val lineBet = candidates.first { it.payoutRatio == 5 } // Line bet pays 5:1

        // Scenario matching user screenshot:
        // Starting bankroll $1000, after 6 losses bankroll is $622 (deficit -$378).
        // Line bet has 6 base units ($6 base stake), net profit per unit on hit is 6 * 5 = $30.
        val testBets = listOf(lineBet.copy(baseUnits = 6))

        // 1. With Risk Cap ENABLED:
        val cappedMult = BetTrendCalculator.computeEffectiveMultiplier(
            rawMultiplier = 64, // Martingale 64x ($384 stake)
            bets = testBets,
            bankroll = 622.0,
            peak = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            progressionType = BetTrendCalculator.ProgressionType.MARTINGALE,
            riskCapEnabled = true,
            maxBankrollRiskFraction = 0.20
        )

        // Needed to recover $378 deficit: ceil((378 + 30) / 30) = 14x ($84 stake)
        // Capital risk ceiling at 20% of $622 = $124.40 (max 20x).
        // Result should be 14x, NOT 64x!
        assertEquals(14, cappedMult)
        val cappedStake = testBets.sumOf { it.baseUnits * 1.0 } * cappedMult
        assertEquals(84.0, cappedStake, 0.01)

        // Verify that winning with 14x achieves a new session high (> $1000):
        val grossWin = cappedStake * (5 + 1) // 84 * 6 = 504
        val netProfit = grossWin - cappedStake // 504 - 84 = +420
        val endingBankroll = 622.0 + netProfit // 622 + 420 = 1042
        assertTrue("Winning at 14x must exceed the $1000 peak", endingBankroll > 1000.0)

        // 2. With Risk Cap DISABLED:
        val uncappedMult = BetTrendCalculator.computeEffectiveMultiplier(
            rawMultiplier = 64,
            bets = testBets,
            bankroll = 622.0,
            peak = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            progressionType = BetTrendCalculator.ProgressionType.MARTINGALE,
            riskCapEnabled = false
        )
        assertEquals("Uncapped multiplier must remain raw 64x", 64, uncappedMult)

        // 3. Test Capital Risk Ceiling when bankroll is heavily depleted:
        // Bankroll $200, peak $1000, deficit $800.
        // 20% ceiling on $200 allows max $40 stake -> floor(40 / 6) = 6x
        val ceilingMult = BetTrendCalculator.computeEffectiveMultiplier(
            rawMultiplier = 128,
            bets = testBets,
            bankroll = 200.0,
            peak = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            progressionType = BetTrendCalculator.ProgressionType.MARTINGALE,
            riskCapEnabled = true,
            maxBankrollRiskFraction = 0.20
        )
        assertEquals("Capital risk ceiling must restrict multiplier to 20% bankroll limit", 6, ceilingMult)
        val ceilingStake = testBets.sumOf { it.baseUnits * 1.0 } * ceilingMult
        assertTrue("Ceiling stake ($ceilingStake) must not exceed 20% of $200 ($40)", ceilingStake <= 40.0)
    }

    @Test
    fun testMaxStakeTracking() {
        // Run a simulation with a progression and verify maxStake reflects the highest stake reached
        val spins = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val result = BetTrendCalculator.evaluateSimulation(
            spins = spins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.MARTINGALE,
            family = BetTrendCalculator.BetFamily.OUTSIDE,
            coldLimit = 5,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 5.0,
            riskCap = false
        )

        val actualMaxWagered = result.spinLogs.maxOfOrNull { it.stake } ?: 0.0
        assertTrue("Highest stake wagered must be greater than 0", actualMaxWagered > 0.0)
        assertEquals("maxStake property must equal the maximum stake reached in the session", actualMaxWagered, result.maxStake, 0.01)
    }

    @Test
    fun testWalkForwardOptimizationIdealBenchmark() {
        val spins = listOf(
            17, 20, 17, 4, 15, 32, 0, 26, 3, 35,
            12, 28, 7, 29, 18, 22, 9, 31, 14, 20,
            1, 33, 16, 24, 5, 10, 23, 8, 30, 11
        )
        val result = BetTrendCalculator.optimize(
            spins = spins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 10,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 5.0,
            transitionRule = BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE,
            switchSensitivity = BetTrendCalculator.SwitchSensitivity.MODERATE
        )

        // Verify equityPoints and idealEquityPoints are produced
        assertEquals("Equity points size should match spins size", spins.size, result.equityPoints.size)
        assertEquals("Ideal equity points size should match spins size", spins.size, result.idealEquityPoints.size)
        assertNotNull("Ideal progression must be identified", result.idealProgression)

        // Verify each log entry has valid state
        result.spinLogs.forEach { log ->
            assertTrue("Log must record progression used", log.progressionUsed.isNotEmpty())
            assertTrue("Bankroll must be positive", log.bankroll > 0)
        }
    }

    @Test
    fun testProgressionTrackerCycleComplete() {
        // Flat betting is always complete
        val flatTracker = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.FLAT)
        assertTrue("Flat betting tracker is always cycle complete", flatTracker.isCycleComplete())

        // Martingale tracker is cycle complete when multiplier is 1 (base unit)
        val martTracker = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.MARTINGALE)
        assertTrue("Initial Martingale tracker is at cycle complete (base unit)", martTracker.isCycleComplete())
        martTracker.record(won = false, netGainedUnits = -1.0)
        assertFalse("Martingale in deficit is NOT cycle complete", martTracker.isCycleComplete())
        martTracker.record(won = true, netGainedUnits = 2.0)
        assertTrue("Martingale resets to cycle complete after winning", martTracker.isCycleComplete())

        // D'Alembert tracker
        val dalembertTracker = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.DALEMBERT)
        assertTrue("Initial D'Alembert is cycle complete", dalembertTracker.isCycleComplete())
        dalembertTracker.record(won = false, netGainedUnits = -1.0)
        assertFalse("D'Alembert increased is not cycle complete", dalembertTracker.isCycleComplete())
        dalembertTracker.reset()
        assertTrue("Resetting tracker restores cycle complete", dalembertTracker.isCycleComplete())
    }

    @Test
    fun testSwitchSensitivityLeadMargins() {
        assertEquals(0.00, BetTrendCalculator.SwitchSensitivity.AGGRESSIVE.leadMargin, 0.001)
        assertEquals(0.05, BetTrendCalculator.SwitchSensitivity.MODERATE.leadMargin, 0.001)
        assertEquals(0.10, BetTrendCalculator.SwitchSensitivity.CONSERVATIVE.leadMargin, 0.001)
    }

    @Test
    fun testOptimizePerformanceOn100Spins() {
        // Generate 100 pseudo-realistic spins
        val randomSpins = (0..99).map { (it * 7 + 13) % 37 }

        val start = System.currentTimeMillis()
        val result = BetTrendCalculator.optimize(
            spins = randomSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 5.0
        )
        val elapsedMs = System.currentTimeMillis() - start

        println("Optimize 100 spins elapsed time: ${elapsedMs}ms")
        assertTrue("Optimize 100 spins must complete in under 250ms, took ${elapsedMs}ms", elapsedMs < 250)
        assertEquals(100, result.equityPoints.size)
    }

    @Test
    fun testBettingStartModeEnumProperties() {
        assertEquals("From Spin 1", BetTrendCalculator.BettingStartMode.FROM_SPIN_1.displayName)
        assertEquals("Spin 1", BetTrendCalculator.BettingStartMode.FROM_SPIN_1.shortName)
        assertEquals("After Lookback", BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK.displayName)
        assertEquals("After Lookback", BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK.shortName)
    }

    @Test
    fun testEvaluateSimulationAfterLookbackUnderLookbackWindow() {
        val spins = listOf(17, 20, 11, 4, 32, 15, 19, 4, 21, 2) // 10 spins
        val lookback = 37

        val result = BetTrendCalculator.evaluateSimulation(
            spins = spins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.FLAT,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = lookback,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            startMode = BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK
        )

        // All 10 spins should be observational seeds
        assertEquals(10, result.equityPoints.size)
        result.equityPoints.forEach { pt ->
            assertEquals("Bankroll remains at startBankroll during seed period", 1000.0, pt.bankroll, 0.001)
            assertEquals("Net gain is 0 during seed period", 0.0, pt.netGain, 0.001)
            assertEquals("Outcome must be SEED", "SEED", pt.record?.outcome)
            assertEquals("Stake must be 0", 0.0, pt.record?.stake ?: 0.0, 0.001)
        }
        assertEquals(0.0, result.netProfit, 0.001)
        assertEquals(0.0, result.maxDrawdown, 0.001)
        assertEquals(0.0, result.nextTotalStake, 0.001)
        assertEquals(0, result.currentMultiplier)
    }

    @Test
    fun testEvaluateSimulationAfterLookbackOverLookbackWindow() {
        // 50 spins with lookback 20
        val spins = (0 until 50).map { (it * 3 + 7) % 37 }
        val lookback = 20

        val result = BetTrendCalculator.evaluateSimulation(
            spins = spins,
            isUS = false,
            progression = BetTrendCalculator.ProgressionType.FLAT,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = lookback,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            startMode = BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK
        )

        assertEquals(50, result.equityPoints.size)

        // First 20 spins must be SEED
        for (i in 0 until 20) {
            val pt = result.equityPoints[i]
            assertEquals(1000.0, pt.bankroll, 0.001)
            assertEquals(0.0, pt.netGain, 0.001)
            assertEquals("SEED", pt.record?.outcome)
            assertEquals(0.0, pt.record?.stake ?: 0.0, 0.001)
        }

        // Spins 21 to 50 (indices 20 to 49) must be active bets!
        var activeBetsFound = false
        for (i in 20 until 50) {
            val pt = result.equityPoints[i]
            assertTrue("Active bet spin #${pt.spinIndex} must have outcome WIN, LOSS, or PUSH", pt.record?.outcome in setOf("WIN", "LOSS", "PUSH"))
            if ((pt.record?.stake ?: 0.0) > 0.0) {
                activeBetsFound = true
            }
        }
        assertTrue("Active bets must be placed on spins after lookback period", activeBetsFound)

        // Next spin (spin 51) is after lookback, so active stake is recommended
        assertTrue(result.nextTotalStake > 0.0)
        assertTrue(result.currentMultiplier >= 1)
    }

    @Test
    fun testOptimizeAfterLookbackSeedsThenBets() {
        // 45 spins with lookback 30
        val spins = (0 until 45).map { (it * 5 + 11) % 37 }
        val lookback = 30

        val result = BetTrendCalculator.optimize(
            spins = spins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = lookback,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            startMode = BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK
        )

        assertEquals(45, result.equityPoints.size)

        // First 30 spins are SEED
        for (i in 0 until 30) {
            val pt = result.equityPoints[i]
            assertEquals(1000.0, pt.bankroll, 0.001)
            assertEquals("SEED", pt.record?.outcome)
            assertEquals(0.0, pt.record?.stake ?: 0.0, 0.001)
        }

        // Spin 31 (index 30) is the first active bet after lookback
        val firstActiveSpin = result.equityPoints[30]
        assertEquals(31, firstActiveSpin.spinIndex)
        assertTrue("Spin 31 outcome must be WIN/LOSS/PUSH", firstActiveSpin.record?.outcome in setOf("WIN", "LOSS", "PUSH"))
        assertTrue("Spin 31 note should indicate optimal strategy based on lookback",
            firstActiveSpin.record?.switchNote?.contains("Optimal strategy based on lookback") == true)

        // Next spin recommendation is ready with active bets
        assertTrue(result.nextTotalStake > 0.0)
        assertEquals(BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK, result.startMode)

        // Ideal Benchmark must always run from Spin 1 across all spins, regardless of start mode selected
        assertEquals("Ideal points must cover all 45 spins", 45, result.idealEquityPoints.size)
        assertNotNull("Ideal progression must be selected", result.idealProgression)
        val firstIdealSpin = result.idealEquityPoints[0]
        assertEquals(1, firstIdealSpin.spinIndex)
        assertTrue("Spin 1 in Ideal must be an active bet (not SEED)", firstIdealSpin.record?.outcome in setOf("WIN", "LOSS", "PUSH"))
        assertTrue("Spin 1 in Ideal must have positive stake", (firstIdealSpin.record?.stake ?: 0.0) > 0.0)
    }

    @Test
    fun testDAlembertAndContraDAlembertStepTransitions() {
        val dalembert = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.DALEMBERT)
        assertEquals(1, dalembert.getMultiplier())
        dalembert.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, dalembert.getMultiplier())
        dalembert.record(won = false, netGainedUnits = -1.0)
        assertEquals(3, dalembert.getMultiplier())
        // Win steps down to 2, does NOT reset to 1
        dalembert.record(won = true, netGainedUnits = 1.0)
        assertEquals(2, dalembert.getMultiplier())
        dalembert.record(won = true, netGainedUnits = 1.0)
        assertEquals(1, dalembert.getMultiplier())
        // Below 1 clamps to 1
        dalembert.record(won = true, netGainedUnits = 1.0)
        assertEquals(1, dalembert.getMultiplier())

        val contra = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.CONTRA_DALEMBERT)
        assertEquals(1, contra.getMultiplier())
        contra.record(won = true, netGainedUnits = 1.0)
        assertEquals(2, contra.getMultiplier())
        contra.record(won = true, netGainedUnits = 1.0)
        assertEquals(3, contra.getMultiplier())
        // Loss steps down to 2, does NOT collapse to 1
        contra.record(won = false, netGainedUnits = -1.0)
        assertEquals(2, contra.getMultiplier())
    }

    @Test
    fun testParoliResetAfterThreeWins() {
        val paroli = BetTrendCalculator.ProgressionTracker(BetTrendCalculator.ProgressionType.PAROLI)
        assertEquals(1, paroli.getMultiplier())
        paroli.record(won = true, netGainedUnits = 1.0)
        assertEquals(2, paroli.getMultiplier())
        paroli.record(won = true, netGainedUnits = 1.0)
        assertEquals(4, paroli.getMultiplier())
        // 3rd win achieves goal -> resets to 1x base unit
        paroli.record(won = true, netGainedUnits = 1.0)
        assertEquals(1, paroli.getMultiplier())
        assertTrue(paroli.isCycleComplete())
    }

    @Test
    fun testFixedLayoutSystemsGenerateAuthenticBets() {
        val candidates = BetTrendCalculator.getCandidates(isUS = false)
        val freqs = (0..36).associateWith { 1.0 / 37.0 }

        // James Bond: 14 units High, 5 units Line 13-18, 1 unit Zero = 20 units total
        val jbBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            progression = BetTrendCalculator.ProgressionType.JAMES_BOND
        )
        assertEquals(3, jbBets.size)
        assertEquals(20, jbBets.sumOf { it.baseUnits })
        assertTrue(jbBets.any { it.betId == "high" && it.baseUnits == 14 })
        assertTrue(jbBets.any { it.type == "six_line" && it.baseUnits == 5 })
        assertTrue(jbBets.any { it.betId == "num-0" && it.baseUnits == 1 })

        // Romanosky: 3 units on 2 dozens, 1 unit each on 2 corners = 8 units total
        val romBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            progression = BetTrendCalculator.ProgressionType.ROMANOSKY
        )
        assertEquals(4, romBets.size)
        assertEquals(8, romBets.sumOf { it.baseUnits })
        assertEquals(2, romBets.count { it.type == "dozen" && it.baseUnits == 3 })
        assertEquals(2, romBets.count { it.type == "corner" && it.baseUnits == 1 })

        // Kavouras: 8 units total covering 20 numbers
        val kavBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            progression = BetTrendCalculator.ProgressionType.KAVOURAS
        )
        assertEquals(8, kavBets.sumOf { it.baseUnits })

        // Shotwell: 5 units total
        val shotBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            progression = BetTrendCalculator.ProgressionType.SHOTWELL
        )
        assertEquals(5, shotBets.sumOf { it.baseUnits })

        // Four Pillars: 4 corners = 4 units total
        val fpBets = BetTrendCalculator.selectOptimalBets(
            candidates = candidates,
            freqs = freqs,
            coldBanned = emptySet(),
            family = BetTrendCalculator.BetFamily.COMBINED,
            progression = BetTrendCalculator.ProgressionType.FOUR_PILLARS
        )
        assertEquals(4, fpBets.size)
        assertEquals(4, fpBets.sumOf { it.baseUnits })
        assertTrue(fpBets.all { it.type == "corner" })
    }

    @Test
    fun testFindGlobalOptimalConfig() {
        val raw = "29,20,35,18,12,20,26,2,18,3,5,8,1,32,11,3,14,8,30,21,12,6,25,8,35,1,9,30,14,22,11,34,34,36,28,8,12,4,4,24,1,1,36,26,33,21,25,21,12,15,4,30,8,20,6,20,15,28,36,34,13,20,9,15,27,35,36,25,32,36,15,21,16,17,1,27,11,8,0,27,20,19,32,14,16,12,6,24,2,16,17,12,7,20,22,22,5,12,21,18,7,14,1,11,8,35,32,7,23,17,31,25,23,30"
        val spins = raw.split(",").map { it.trim().toInt() }

        val globalOpt = BetTrendCalculator.findGlobalOptimalConfig(
            spins = spins,
            isUS = false,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.QUALITY
        )

        assertNotNull(globalOpt)
        println("Global Optimal Found: ${globalOpt?.summaryText}")
        println("NetProfit: ${globalOpt?.netProfit}, MaxDD: ${globalOpt?.maxDrawdown}, Quality: ${globalOpt?.qualityScore} (${globalOpt?.qualityGrade})")
        assertTrue(globalOpt!!.idealEquityPoints.isNotEmpty())
        assertTrue(globalOpt.summaryText.isNotEmpty())
        assertTrue(globalOpt.conditionRowText.isNotEmpty())
    }

    @Test
    fun testFamilyOptimalVsOptimize() {
        val raw = "29,20,35,18,12,20,26,2,18,3,5,8,1,32,11,3,14,8,30,21,12,6,25,8,35,1,9,30,14,22,11,34,34,36,28,8,12,4,4,24,1,1,36,26,33,21,25,21,12,15,4,30,8,20,6,20,15,28,36,34,13,20,9,15,27,35,36,25,32,36,15,21,16,17,1,27,11,8,0,27,20,19,32,14,16,12,6,24,2,16,17,12,7,20,22,22,5,12,21,18,7,14,1,11,8,35,32,7,23,17,31,25,23,30"
        val spins = raw.split(",").map { it.trim().toInt() }

        val globalOpt = BetTrendCalculator.findGlobalOptimalConfig(
            spins = spins,
            isUS = false,
            primaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
            secondaryCriterion = BetTrendCalculator.OptimizationCriterion.QUALITY
        )
        assertNotNull(globalOpt)
        println("=== FAMILY OPTIMAL CONFIGS ===")
        globalOpt!!.familyOptimalColdConfigs.forEach { famOpt ->
            println("Family: ${famOpt.family}, Cold: ${famOpt.optimalColdLimit} (${famOpt.optimalColdRule}), Dialog P/L: ${famOpt.netProfit}, Rating: ${famOpt.qualityScore}")
            val actual = BetTrendCalculator.optimize(
                spins = spins,
                isUS = false,
                targetProgression = BetTrendCalculator.ProgressionType.AUTO,
                family = famOpt.family,
                coldLimit = famOpt.optimalColdLimit,
                coldRule = famOpt.optimalColdRule,
                primaryCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
                secondaryCriterion = BetTrendCalculator.OptimizationCriterion.QUALITY
            )
            println("  Actual optimize -> P/L: ${actual.netProfit}, Rating: ${actual.quality.score}, BestProg: ${actual.bestProgression}")
            assertEquals("P/L must match applied chart", famOpt.netProfit, actual.netProfit, 0.001)
            assertEquals("Rating must match applied chart", famOpt.qualityScore, actual.quality.score)
            assertEquals("MaxDD must match applied chart", famOpt.maxDrawdown, actual.maxDrawdown, 0.001)
            assertEquals("WinRate must match applied chart", famOpt.winRatePercent, actual.winRatePercent, 0.001)
        }
    }

    @Test
    fun testEmpiricalBayesRepeaterAndMarkovTransitionBoost() {
        // 17 repeats and is followed by 20; ending on 17 triggers 1st-order Markov transition boost on 20
        val spins = listOf(17, 20, 8, 17, 20, 11, 17)
        val freqs = BetTrendCalculator.computeFrequencies(spins, isUS = false)

        val weight17 = freqs[17] ?: 0.0
        val weight20 = freqs[20] ?: 0.0
        val weight11 = freqs[11] ?: 0.0

        assertTrue("Repeater pocket 17 must have highest concentration weight", weight17 > weight20)
        assertTrue("Markov transition successor 20 (following 17 twice) must outweigh single recent hit 11", weight20 > weight11)
    }

    @Test
    fun testDualScaleCircularVonMisesAndDealerProjection() {
        // Spins stepping +4 pockets clockwise along EU rotor: 0 -> 4 -> 17
        // Next projected +4 step from 17 (index 8) is 13 (index 12)
        val spins = listOf(0, 4, 17)
        val baseFreqs = BetTrendCalculator.computeFrequencies(spins, isUS = false)
        val macroWeights = BetTrendCalculator.computeMacroRotorWeights(spins, baseFreqs, isUS = false)

        // Neighbor 34 (adjacent to 17 on rotor) must receive spatial Von Mises diffusion even with 0 direct hits
        assertEquals(0.0, baseFreqs[34] ?: 0.0, 0.0001)
        assertTrue("Adjacent rotor pocket 34 must receive positive Von Mises spatial weight", macroWeights[34] > 0.0)

        // Projected dealer step target 13 (4 pockets clockwise from 17) must receive positive weight
        assertEquals(0.0, baseFreqs[13] ?: 0.0, 0.0001)
        assertTrue("Dealer step projected pocket 13 must receive positive weight", macroWeights[13] > 0.0)
    }

    @Test
    fun testFastEvaluateRoundsPushParityAndRecencyDecay() {
        val candidates = BetTrendCalculator.getCandidates(isUS = false)
        val doz1 = candidates.first { it.betId == "doz-1" }
        val doz2 = candidates.first { it.betId == "doz-2" }
        val low = candidates.first { it.betId == "low" }

        // 3 outside bets (1u each = 3u total stake): if number 20 hits (in doz2 13..24, not in doz1 1..12 or low 1..18),
        // gross win is 1u * (2 + 1) = 3u -> netGain == 0.0 (PUSH!)
        val pushBets = listOf(doz1, doz2, low)
        val pushRound = BetTrendCalculator.PrecomputedRound(
            spinIndex = 1,
            spinNumber = 20,
            spinMask = 1L shl 20,
            bets = pushBets,
            coldSet = emptySet(),
            coldStr = "None",
            betsDesc = "Push Test",
            baseStake = 3.0,
            minNetWinPerUnit = 1.0
        )
        // Second round: number 30 (LOSS: -3u)
        val lossRound = pushRound.copy(spinIndex = 2, spinNumber = 30, spinMask = 1L shl 30)

        val rounds = listOf(pushRound, lossRound)
        val fastMetrics = BetTrendCalculator.fastEvaluateRounds(
            prog = BetTrendCalculator.ProgressionType.MARTINGALE,
            rounds = rounds,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0,
            riskCap = false
        )
        // If round 1 is properly treated as a PUSH, Martingale stays at 1x on round 2 (loses -3.0, not -6.0)
        assertEquals("Push round must not escalate Martingale multiplier in fastEvaluateRounds", -3.0, fastMetrics.netProfit, 0.001)
    }

    @Test
    fun testBetSizingFollowsFeltSetup() {
        val spins = listOf(17, 20, 5, 14, 32, 0, 19, 4, 21, 2)
        // Felt setup: Start $1000, In Base $1, In Min $1, Out Base $5, Out Min $5
        val resCombined = BetTrendCalculator.optimize(
            spins = spins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.FLAT,
            family = BetTrendCalculator.BetFamily.COMBINED,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 5.0
        )
        val insideBets = resCombined.recommendedBets.filter { it.bet.isInside }
        val outsideBets = resCombined.recommendedBets.filter { !it.bet.isInside }
        assertTrue(insideBets.isNotEmpty())
        assertTrue(outsideBets.isNotEmpty())
        insideBets.forEach { assertEquals(1.0, it.dollarAmount, 0.001) }
        outsideBets.forEach { assertEquals(5.0, it.dollarAmount, 0.001) }
        assertEquals(
            insideBets.sumOf { it.dollarAmount } + outsideBets.sumOf { it.dollarAmount },
            resCombined.nextTotalStake,
            0.001
        )
    }
}



