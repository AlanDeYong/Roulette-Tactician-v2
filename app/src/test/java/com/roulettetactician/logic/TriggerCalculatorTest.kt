package com.roulettetactician.logic

import org.junit.Assert.*
import org.junit.Test

class TriggerCalculatorTest {

    @Test
    fun testEmptySpins() {
        val result = TriggerCalculator.evaluate(emptyList(), TriggerCalculator.TriggerThresholds())
        assertFalse(result.hasAnyTriggered)
        assertEquals(0, result.redBlack.currentStreak)
        assertEquals(0, result.highLow.currentStreak)
    }

    @Test
    fun testRedStreakTrigger() {
        // 1, 3, 5, 7 are all Red numbers
        val spins = listOf(2, 4, 1, 3, 5, 7)
        val thresholds = TriggerCalculator.TriggerThresholds(redBlack = 4)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("Red", result.redBlack.currentOutcome)
        assertEquals(4, result.redBlack.currentStreak)
        assertTrue(result.redBlack.isTriggered)
        assertTrue(result.hasAnyTriggered)
        assertNotNull(result.redBlack.alertMessage)
    }

    @Test
    fun testZeroInterruptsStreak() {
        // Red streak interrupted by zero
        val spins = listOf(1, 3, 5, 7, 0)
        val thresholds = TriggerCalculator.TriggerThresholds(redBlack = 3)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("Zero", result.redBlack.currentOutcome)
        assertFalse(result.redBlack.isTriggered)
    }

    @Test
    fun testHighLowStreak() {
        // High numbers: 19, 20, 21, 22
        val spins = listOf(1, 2, 19, 20, 21, 22)
        val thresholds = TriggerCalculator.TriggerThresholds(highLow = 4)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("High", result.highLow.currentOutcome)
        assertEquals(4, result.highLow.currentStreak)
        assertTrue(result.highLow.isTriggered)
    }

    @Test
    fun testOddEvenStreak() {
        // Odd numbers: 1, 3, 5, 7, 9
        val spins = listOf(2, 4, 1, 3, 5, 7, 9)
        val thresholds = TriggerCalculator.TriggerThresholds(oddEven = 5)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("Odd", result.oddEven.currentOutcome)
        assertEquals(5, result.oddEven.currentStreak)
        assertTrue(result.oddEven.isTriggered)
    }

    @Test
    fun testDozensStreak() {
        // 1st Dozen: 1, 4, 7 (25 is in 3rd dozen)
        val spins = listOf(25, 1, 4, 7)
        val thresholds = TriggerCalculator.TriggerThresholds(dozens = 3)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("1st Dozen", result.dozens.currentOutcome)
        assertEquals(3, result.dozens.currentStreak)
        assertTrue(result.dozens.isTriggered)
    }

    @Test
    fun testColumnsStreak() {
        // 1st Column: 1, 4, 7 (2 is in 2nd column)
        val spins = listOf(2, 1, 4, 7)
        val thresholds = TriggerCalculator.TriggerThresholds(columns = 3)
        val result = TriggerCalculator.evaluate(spins, thresholds)

        assertEquals("1st Column", result.columns.currentOutcome)
        assertEquals(3, result.columns.currentStreak)
        assertTrue(result.columns.isTriggered)
    }
}
