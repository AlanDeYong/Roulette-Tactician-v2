package com.roulettetactician.logic

import org.junit.Assert.*
import org.junit.Test

class MovingAverageCalculatorTest {

    @Test
    fun testSMA_Calculation() {
        val spins = listOf(1, 3, 5, 7, 9, 2, 4, 6, 8, 10) // 5 Red, 5 Black
        val config = MovingAverageCalculator.MAConfig(
            type = MovingAverageCalculator.MAType.SMA,
            period = 5
        )

        // Red numbers are 1, 3, 5, 7, 9 -> first 5 are all red (100%)
        val redMA = MovingAverageCalculator.calculateMA(spins, config) {
            MovingAverageCalculator.RED_NUMBERS.contains(it)
        }

        assertEquals(6, redMA.size) // 10 - 5 + 1 = 6
        assertEquals(100f, redMA[0], 0.01f) // First window: [1, 3, 5, 7, 9] -> 100%
        assertEquals(0f, redMA.last(), 0.01f) // Last window: [2, 4, 6, 8, 10] -> 0%
    }

    @Test
    fun testAllMATypes_ProduceValidValues() {
        val spins = (1..50).map { it % 37 }
        val types = MovingAverageCalculator.MAType.values()

        types.forEach { type ->
            val config = MovingAverageCalculator.MAConfig(type = type, period = 10)
            val result = MovingAverageCalculator.computeAll(spins, config)

            assertEquals(50, result.totalSpins)
            assertEquals(10, result.period)

            // Verify all series have values between 0% and 100%
            result.redBlack.lines.forEach { line ->
                assertTrue(line.values.isNotEmpty())
                line.values.forEach { v ->
                    assertTrue("Value $v for type $type should be in 0..100", v in -1f..101f)
                }
            }

            result.highLow.lines.forEach { line ->
                assertTrue(line.values.isNotEmpty())
            }

            result.oddEven.lines.forEach { line ->
                assertTrue(line.values.isNotEmpty())
            }

            result.dozens.lines.forEach { line ->
                assertTrue(line.values.isNotEmpty())
            }

            result.columns.lines.forEach { line ->
                assertTrue(line.values.isNotEmpty())
            }
        }
    }

    @Test
    fun testMD_Calculation_DoesNotCollapseToZero() {
        // 150 alternating spins across dozens
        val spins = (1..150).map { (it % 36) + 1 }
        val config = MovingAverageCalculator.MAConfig(
            type = MovingAverageCalculator.MAType.MD,
            period = 50
        )
        val result = MovingAverageCalculator.computeAll(spins, config)

        assertEquals(150, result.totalSpins)
        assertEquals(50, result.period)

        // Verify Dozens MD values stay around ~33% and do not collapse to 0%
        result.dozens.lines.forEach { line ->
            assertEquals(101, line.values.size) // 150 - 50 + 1 = 101
            val lastValue = line.values.last()
            assertTrue("Last MD value ($lastValue%) should be near 33%, not 0%", lastValue in 20f..45f)
        }
    }

    @Test
    fun testComputeOutsideMA() {
        // Trending spins: 5 Black numbers (2, 4, 6, 8, 10) followed by 5 Red numbers (1, 3, 5, 7, 9)
        // With SMA period 5: previous MA was 80%, latest MA reached 100% (+20% deltaNow -> UP trend)
        val trendingSpins = listOf(2, 4, 6, 8, 10, 1, 3, 5, 7, 9)
        val config = MovingAverageCalculator.MAConfig(
            type = MovingAverageCalculator.MAType.SMA,
            period = 5
        )

        val outsideMA = MovingAverageCalculator.computeOutsideMA(trendingSpins, config, isUS = false)
        assertEquals(12, outsideMA.size)

        // "red" should be 100% MA and trending upward
        val redInfo = outsideMA["red"]
        assertNotNull(redInfo)
        assertEquals(10, redInfo!!.hitCount)
        assertEquals(100f, redInfo.percentage, 0.01f)
        assertEquals(HeatmapCalculator.TrendDirection.UP, redInfo.trendDirection)
        assertTrue("Badge should have upward arrow", redInfo.badge.startsWith("▲"))
        assertTrue("Badge should display 100%", redInfo.badge.contains("100%"))

        // "black" should be 0% MA and trending downward
        val blackInfo = outsideMA["black"]
        assertNotNull(blackInfo)
        assertEquals(0, blackInfo!!.hitCount)
        assertEquals(0f, blackInfo.percentage, 0.01f)
        assertEquals(HeatmapCalculator.TrendDirection.DOWN, blackInfo.trendDirection)
        assertTrue("Badge should have downward arrow", blackInfo.badge.startsWith("▼"))
        assertTrue("Badge should display 0%", blackInfo.badge.contains("0%"))

        // Dozens and columns should also be present
        assertTrue(outsideMA.containsKey("doz1"))
        assertTrue(outsideMA.containsKey("col1"))
    }
}
