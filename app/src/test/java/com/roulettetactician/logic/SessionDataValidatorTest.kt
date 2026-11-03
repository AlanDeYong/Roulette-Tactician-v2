package com.roulettetactician.logic

import org.junit.Assert.*
import org.junit.Test

class SessionDataValidatorTest {

    @Test
    fun testParseAndValidateHistory_ValidSequence() {
        val input = "0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26,37"
        val result = SessionDataValidator.parseAndValidateHistory(input)

        assertTrue(result.isValid)
        assertEquals(38, result.validCount)
        assertEquals(0, result.droppedCount)
        assertEquals(38, result.numbers.size)
        assertEquals(0, result.numbers.first())
        assertEquals(37, result.numbers.last())
    }

    @Test
    fun testParseAndValidateHistory_EmptyAndWhitespace() {
        val nullResult = SessionDataValidator.parseAndValidateHistory(null)
        assertEquals(0, nullResult.validCount)
        assertTrue(nullResult.numbers.isEmpty())

        val emptyResult = SessionDataValidator.parseAndValidateHistory("")
        assertEquals(0, emptyResult.validCount)
        assertTrue(emptyResult.numbers.isEmpty())

        val spaceResult = SessionDataValidator.parseAndValidateHistory("   \n\t  ")
        assertEquals(0, spaceResult.validCount)
        assertTrue(spaceResult.numbers.isEmpty())
    }

    @Test
    fun testParseAndValidateHistory_CorruptedTokensFilteredOut() {
        // Contains negative numbers, numbers > 37, non-digit chars, trailing commas
        val input = "10, -5, 36, 999, abc, 0, 37, 100, 15, ,"
        val result = SessionDataValidator.parseAndValidateHistory(input)

        // Valid tokens: 10, 36, 0, 37, 15 (5 valid)
        assertEquals(listOf(10, 36, 0, 37, 15), result.numbers)
        assertEquals(5, result.validCount)
        assertTrue("Dropped count should be greater than 0", result.droppedCount > 0)
        assertFalse(result.isValid)
    }

    @Test
    fun testParseAndValidateHistory_VariousDelimiters() {
        val input = "0 32\n15\t19;4,21"
        val result = SessionDataValidator.parseAndValidateHistory(input)

        assertEquals(listOf(0, 32, 15, 19, 4, 21), result.numbers)
        assertEquals(6, result.validCount)
        assertEquals(0, result.droppedCount)
    }

    @Test
    fun testSerializationRoundTripAndOrdering() {
        val original = listOf(0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23)
        val serialized = SessionDataValidator.serializeHistory(original)
        val parsed = SessionDataValidator.parseAndValidateHistory(serialized)

        assertEquals(original, parsed.numbers)
        assertEquals(original.size, parsed.validCount)
    }

    @Test
    fun testChecksumDeterministic() {
        val seq1 = listOf(1, 2, 3, 4, 5)
        val seq2 = listOf(1, 2, 3, 4, 5)
        val seq3 = listOf(5, 4, 3, 2, 1)

        val checksum1 = SessionDataValidator.calculateChecksum(seq1)
        val checksum2 = SessionDataValidator.calculateChecksum(seq2)
        val checksum3 = SessionDataValidator.calculateChecksum(seq3)

        assertEquals(checksum1, checksum2)
        assertNotEquals(checksum1, checksum3) // Order sensitivity
    }

    @Test
    fun testBenchmark_LargeDatasetParsingSpeed() {
        // Generate 50,000 spins
        val randomSpins = (0 until 50_000).map { (it % 38) }
        val serialized = SessionDataValidator.serializeHistory(randomSpins)

        val startTime = System.nanoTime()
        val result = SessionDataValidator.parseAndValidateHistory(serialized)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0

        assertEquals(50_000, result.validCount)
        assertEquals(0, result.droppedCount)
        assertEquals(randomSpins, result.numbers)
        
        println("Parsed 50,000 spins in ${elapsedMs}ms")
        assertTrue("Parsing 50,000 spins should take < 50ms (was ${elapsedMs}ms)", elapsedMs < 50.0)
    }

    @Test
    fun testOverridesParsingAndSerialization() {
        val overrides = listOf(1, null, 36, null, 0)
        val serialized = SessionDataValidator.serializeOverrides(overrides)
        assertEquals("1,null,36,null,0", serialized)

        val parsed = SessionDataValidator.parseOverrides(serialized)
        assertEquals(overrides, parsed)

        // Invalid numbers in overrides should become null
        val corrupted = "1,999,invalid,-5,null"
        val parsedCorrupted = SessionDataValidator.parseOverrides(corrupted)
        assertEquals(listOf(1, null, null, null, null), parsedCorrupted)
    }

    @Test
    fun testChipsParsingAndSerialization() {
        val chipString = "25:0.5:0.5:num-17|100:0.2:0.8:red"
        val chips = SessionDataValidator.parseChips(chipString)

        assertEquals(2, chips.size)
        assertEquals(25, chips[0].value)
        assertEquals(0.5f, chips[0].xPercent, 0.001f)
        assertEquals(0.5f, chips[0].yPercent, 0.001f)
        assertEquals("num-17", chips[0].betId)

        assertEquals(100, chips[1].value)
        assertEquals(0.2f, chips[1].xPercent, 0.001f)
        assertEquals(0.8f, chips[1].yPercent, 0.001f)
        assertEquals("red", chips[1].betId)

        val reserialized = SessionDataValidator.serializeChips(chips)
        assertEquals(chipString, reserialized)
    }

    @Test
    fun testParseAndValidateHistory_DoubleZero() {
        val input = "0, 00, 32, 15, 00"
        val result = SessionDataValidator.parseAndValidateHistory(input)
        assertTrue(result.isValid)
        assertEquals(listOf(0, 37, 32, 15, 37), result.numbers)
    }

    @Test
    fun testSerializeHistoryForFile() {
        val spins = listOf(0, 37, 17, 36)
        val fileContent = SessionDataValidator.serializeHistoryForFile(spins)
        assertEquals("0\n00\n17\n36", fileContent)

        // Verify round-trip parsing from file content (1st to last)
        val parsed = SessionDataValidator.parseAndValidateHistory(fileContent)
        assertEquals(spins, parsed.numbers)
    }

    @Test
    fun testInitialHeatmapColorIsSlateGray() {
        val emptyStats = HeatmapCalculator.calculateStats(
            history = emptyList(),
            windowSize = 37,
            hotOverrides = emptyList(),
            coldOverrides = emptyList()
        )
        // For every number 0..36, heat color must be Slate Gray (#64748B) when history is empty
        for (i in 0..36) {
            val color = HeatmapCalculator.getHeatColor(i, emptyStats, emptyList(), emptyList())
            assertEquals("Initial color for number $i must be Slate Gray", HeatmapCalculator.COLOR_GRAY, color)
        }
    }

    @Test
    fun testSessionEntityTabConfigsDefaultsAndCustom() {
        // Test default parameters for backwards compatibility
        val defaultSession = com.roulettetactician.data.SessionEntity(
            name = "Default",
            timestamp = 1000L,
            hotOverrides = "",
            coldOverrides = "",
            analysisWindow = 37,
            history = "",
            chips = ""
        )
        assertEquals("COMBINED", defaultSession.trendFamily)
        assertEquals(37, defaultSession.trendLookback)
        assertEquals("AUTO", defaultSession.trendProgression)
        assertEquals(5, defaultSession.trendColdLimit)
        assertEquals("SMA", defaultSession.maType)
        assertEquals(10, defaultSession.maPeriod)
        assertEquals("ALL", defaultSession.maCategory)
        assertEquals(4, defaultSession.triggerRB)

        // Test custom configurations
        val customSession = defaultSession.copy(
            trendFamily = "INSIDE",
            trendLookback = 50,
            trendProgression = "MARTINGALE",
            trendColdLimit = 3,
            maType = "EMA",
            maPeriod = 20,
            triggerRB = 6
        )
        assertEquals("INSIDE", customSession.trendFamily)
        assertEquals(50, customSession.trendLookback)
        assertEquals("MARTINGALE", customSession.trendProgression)
        assertEquals(3, customSession.trendColdLimit)
        assertEquals("EMA", customSession.maType)
        assertEquals(20, customSession.maPeriod)
        assertEquals(6, customSession.triggerRB)
    }

    @Test
    fun testTimeMachineHistorySlicingAndClamping() {
        val allSpins = (1..60).toList()
        
        // At checkpoint 25
        val k = 25
        val effectiveSpins = allSpins.take(k)
        assertEquals(25, effectiveSpins.size)
        assertEquals(1, effectiveSpins.first())
        assertEquals(25, effectiveSpins.last())

        // Reverse for newest-first consumers (HeatmapCalculator)
        val newestFirst = effectiveSpins.reversed()
        assertEquals(25, newestFirst.first())
        assertEquals(1, newestFirst.last())

        // Boundary clamp
        val clampedStart = allSpins.take(0.coerceIn(1, allSpins.size))
        assertEquals(1, clampedStart.size)

        val clampedEnd = allSpins.take(100.coerceIn(1, allSpins.size))
        assertEquals(60, clampedEnd.size)
    }

    @Test
    fun testTimeMachineHistoricalHeatmapEquivalence() {
        val chronologicalSpins = listOf(
            7, 14, 21, 28, 35, 7, 7, 14, 0, 32,
            15, 19, 4, 21, 2, 25, 17, 34, 6, 27
        )

        // At spin 10 (first 10 spins: 7, 14, 21, 28, 35, 7, 7, 14, 0, 32)
        val checkpointSpins = chronologicalSpins.take(10)
        val historicalStats = HeatmapCalculator.calculateStats(
            history = checkpointSpins.reversed(),
            windowSize = 50,
            hotOverrides = listOf(null, null, null, null, null),
            coldOverrides = listOf(null, null, null, null, null)
        )

        // Number 7 occurred 3 times in the first 10 spins
        assertEquals(3, historicalStats.freq[7])
        // Number 14 occurred 2 times
        assertEquals(2, historicalStats.freq[14])
        // Total history count is 10
        assertEquals(10, historicalStats.totalHistoryCount)

        // Full history stats (20 spins)
        val fullStats = HeatmapCalculator.calculateStats(
            history = chronologicalSpins.reversed(),
            windowSize = 50,
            hotOverrides = listOf(null, null, null, null, null),
            coldOverrides = listOf(null, null, null, null, null)
        )
        assertEquals(20, fullStats.totalHistoryCount)

        // Confirm historical stats strictly isolate the historical time window
        assertNotEquals(historicalStats.totalHistoryCount, fullStats.totalHistoryCount)
    }

    @Test
    fun testTimeMachineHistoricalRecommendationEquivalence() {
        val chronologicalSpins = listOf(
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
            11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            21, 22, 23, 24, 25, 26, 27, 28, 29, 30
        )

        val checkpointSpins = chronologicalSpins.take(15)
        val recAt15 = BetTrendCalculator.optimize(
            spins = checkpointSpins,
            isUS = false,
            targetProgression = BetTrendCalculator.ProgressionType.AUTO,
            family = BetTrendCalculator.BetFamily.COMBINED,
            coldLimit = 5,
            lookbackWindow = 37,
            startBankroll = 1000.0,
            baseInsideBet = 1.0,
            baseOutsideBet = 1.0
        )

        // Verify valid recommendation returned at checkpoint
        assertTrue(recAt15.recommendedBets.isNotEmpty())
        assertEquals(15, recAt15.equityPoints.size)
        assertEquals(15, recAt15.equityPoints.last().spinIndex)
        assertEquals(15, recAt15.equityPoints.last().spunNumber)
    }

    @Test
    fun testSessionEntityBetTrendFieldPersistence() {
        val session = com.roulettetactician.data.SessionEntity(
            name = "Test Table",
            timestamp = 123456789L,
            hotOverrides = "",
            coldOverrides = "",
            analysisWindow = 37,
            history = "17,20,32,15",
            chips = "",
            trendFamily = "COMBINED",
            trendLookback = 37,
            trendProgression = "AUTO",
            trendColdLimit = 3,
            trendPrimaryCriterion = "QUALITY",
            trendSecondaryCriterion = "MAX_DD",
            trendColdRule = "WATERFALL",
            trendEqualizePayout = false,
            trendPushOrProfit = false,
            trendRiskCap = true,
            trendUnderwaterFilter = true,
            trendTransitionRule = "COMPLETE_CYCLE",
            trendSwitchSensitivity = "MODERATE",
            trendShowIdealOverlay = false,
            trendStartMode = "AFTER_LOOKBACK"
        )

        assertEquals("QUALITY", session.trendPrimaryCriterion)
        assertEquals("MAX_DD", session.trendSecondaryCriterion)
        assertEquals(3, session.trendColdLimit)
        assertEquals("WATERFALL", session.trendColdRule)
        assertEquals(false, session.trendEqualizePayout)
        assertEquals(false, session.trendPushOrProfit)
        assertEquals(true, session.trendRiskCap)
        assertEquals(true, session.trendUnderwaterFilter)
        assertEquals("COMPLETE_CYCLE", session.trendTransitionRule)
        assertEquals("MODERATE", session.trendSwitchSensitivity)
        assertEquals("AFTER_LOOKBACK", session.trendStartMode)
    }
}


