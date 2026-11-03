package com.roulettetactician.logic

import com.roulettetactician.ui.model.PlacedChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayCalculatorTest {

    @Test
    fun testStraightBetEvaluation() {
        val chip = PlacedChip(id = 1L, value = 5, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")
        
        // Winning outcome on 17
        val winEval = GameplayCalculator.evaluateRound(listOf(chip), spunNumber = 17, isUS = false)
        assertEquals(5.0, winEval.totalStake, 0.001)
        assertEquals(180.0, winEval.totalPayout, 0.001) // 5 * (35 + 1) = 180
        assertEquals(175.0, winEval.netProfit, 0.001) // 180 - 5 = 175
        assertEquals(1, winEval.winningChips.size)
        assertEquals(0, winEval.losingChips.size)

        // Losing outcome on 20
        val loseEval = GameplayCalculator.evaluateRound(listOf(chip), spunNumber = 20, isUS = false)
        assertEquals(5.0, loseEval.totalStake, 0.001)
        assertEquals(0.0, loseEval.totalPayout, 0.001)
        assertEquals(-5.0, loseEval.netProfit, 0.001)
        assertEquals(0, loseEval.winningChips.size)
        assertEquals(1, loseEval.losingChips.size)
    }

    @Test
    fun testOutsideBetsEvaluation() {
        val dozChip = PlacedChip(id = 1L, value = 10, xPercent = 0.5f, yPercent = 0.7f, betId = "doz-2") // 13-24
        val redChip = PlacedChip(id = 2L, value = 20, xPercent = 0.5f, yPercent = 0.9f, betId = "red")

        // Spin 14 (Red and in 2nd dozen)
        val eval14 = GameplayCalculator.evaluateRound(listOf(dozChip, redChip), spunNumber = 14, isUS = false)
        assertEquals(30.0, eval14.totalStake, 0.001)
        // Doz payout: 10 * 3 = 30. Red payout: 20 * 2 = 40. Total = 70
        assertEquals(70.0, eval14.totalPayout, 0.001)
        assertEquals(40.0, eval14.netProfit, 0.001) // 70 - 30 = +40

        // Spin 13 (Black in 2nd dozen): Doz wins, Red loses
        val eval13 = GameplayCalculator.evaluateRound(listOf(dozChip, redChip), spunNumber = 13, isUS = false)
        assertEquals(30.0, eval13.totalStake, 0.001)
        assertEquals(30.0, eval13.totalPayout, 0.001) // Doz 30, Red 0
        assertEquals(0.0, eval13.netProfit, 0.001) // PUSH
    }

    @Test
    fun testSplitAndStreetBetEvaluationFormats() {
        // Test inverted horizontal split (e.g. split-9-8 vs split-8-9) with spun number 8
        val splitChip = PlacedChip(id = 1L, value = 2, xPercent = 0.5f, yPercent = 0.5f, betId = "split-9-8")
        val eval8 = GameplayCalculator.evaluateRound(listOf(splitChip), spunNumber = 8, isUS = false)
        assertEquals(2.0, eval8.totalStake, 0.001)
        assertEquals(36.0, eval8.totalPayout, 0.001) // 2 * (17 + 1) = 36
        assertEquals(34.0, eval8.netProfit, 0.001)
        assertEquals(1, eval8.winningChips.size)

        // Test with spun number 9 (other number of split)
        val eval9 = GameplayCalculator.evaluateRound(listOf(splitChip), spunNumber = 9, isUS = false)
        assertEquals(36.0, eval9.totalPayout, 0.001)
        assertEquals(34.0, eval9.netProfit, 0.001)

        // Test with 12 split bets of $2 each (total stake $24), where split-9-8 hits on 8
        val chips = listOf(
            PlacedChip(id = 1L, value = 2, xPercent = 0.1f, yPercent = 0.1f, betId = "split-3-6"),
            PlacedChip(id = 2L, value = 2, xPercent = 0.2f, yPercent = 0.2f, betId = "split-4-5"),
            PlacedChip(id = 3L, value = 2, xPercent = 0.3f, yPercent = 0.3f, betId = "split-9-8"), // Winning split on 8
            PlacedChip(id = 4L, value = 2, xPercent = 0.4f, yPercent = 0.4f, betId = "split-11-12"),
            PlacedChip(id = 5L, value = 2, xPercent = 0.5f, yPercent = 0.5f, betId = "split-13-14"),
            PlacedChip(id = 6L, value = 2, xPercent = 0.6f, yPercent = 0.6f, betId = "split-15-18"),
            PlacedChip(id = 7L, value = 2, xPercent = 0.7f, yPercent = 0.7f, betId = "split-19-20"),
            PlacedChip(id = 8L, value = 2, xPercent = 0.8f, yPercent = 0.8f, betId = "split-21-24"),
            PlacedChip(id = 9L, value = 2, xPercent = 0.9f, yPercent = 0.9f, betId = "split-26-27"),
            PlacedChip(id = 10L, value = 2, xPercent = 0.1f, yPercent = 0.2f, betId = "split-29-30"),
            PlacedChip(id = 11L, value = 2, xPercent = 0.2f, yPercent = 0.3f, betId = "split-32-33"),
            PlacedChip(id = 12L, value = 2, xPercent = 0.3f, yPercent = 0.4f, betId = "split-34-35")
        )
        val eval12 = GameplayCalculator.evaluateRound(chips, spunNumber = 8, isUS = false)
        assertEquals(24.0, eval12.totalStake, 0.001)
        assertEquals(36.0, eval12.totalPayout, 0.001) // $36 payout
        assertEquals(12.0, eval12.netProfit, 0.001) // +$12 PROFIT (not a loss!)
        assertEquals(1, eval12.winningChips.size)
        assertEquals(11, eval12.losingChips.size)

        // Test street bet alternate format (street-3 vs street-1) on number 2
        val streetChip = PlacedChip(id = 20L, value = 5, xPercent = 0.1f, yPercent = 0.1f, betId = "street-3")
        val streetEval = GameplayCalculator.evaluateRound(listOf(streetChip), spunNumber = 2, isUS = false)
        assertEquals(5.0, streetEval.totalStake, 0.001)
        assertEquals(60.0, streetEval.totalPayout, 0.001) // 5 * (11 + 1) = 60
        assertEquals(55.0, streetEval.netProfit, 0.001)
    }

    @Test
    fun testStackOrAddChip() {
        val chip1 = PlacedChip(id = 1L, value = 5, xPercent = 0.1f, yPercent = 0.1f, betId = "num-8")
        val chip2 = PlacedChip(id = 2L, value = 10, xPercent = 0.1f, yPercent = 0.1f, betId = "num-8")
        val chip3 = PlacedChip(id = 3L, value = 25, xPercent = 0.2f, yPercent = 0.2f, betId = "num-9")

        var chips = listOf(chip1)
        chips = GameplayCalculator.stackOrAddChip(chips, chip2)
        assertEquals(1, chips.size)
        assertEquals(15, chips[0].value) // 5 + 10 = 15

        chips = GameplayCalculator.stackOrAddChip(chips, chip3)
        assertEquals(2, chips.size)
        assertEquals(15, chips[0].value)
        assertEquals(25, chips[1].value)
    }

    @Test
    fun testRemoveOrDeductChip() {
        val chip = PlacedChip(id = 100L, value = 25, xPercent = 0.1f, yPercent = 0.1f, betId = "num-8")
        val chips = listOf(chip)

        // Single chip deduction of 5
        val afterDeduct5 = GameplayCalculator.removeOrDeductChip(chips, 100L, ChipRemovalMode.SINGLE_CHIP, 5)
        assertEquals(1, afterDeduct5.size)
        assertEquals(20, afterDeduct5[0].value)

        // Entire stack removal
        val afterEntireStack = GameplayCalculator.removeOrDeductChip(chips, 100L, ChipRemovalMode.ENTIRE_STACK, 5)
        assertEquals(0, afterEntireStack.size)
    }

    @Test
    fun testUserQuirkStackedChipSingleRemovals() {
        // User scenario: Added $1 + $1 + $5 on a number
        val chip1 = PlacedChip(id = 1L, value = 1, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")
        val chip2 = PlacedChip(id = 2L, value = 1, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")
        val chip3 = PlacedChip(id = 3L, value = 5, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")

        var chips = listOf(chip1)
        chips = GameplayCalculator.stackOrAddChip(chips, chip2)
        chips = GameplayCalculator.stackOrAddChip(chips, chip3)

        assertEquals(1, chips.size)
        assertEquals(7, chips[0].value) // $1 + $1 + $5 = $7
        assertEquals(listOf(1, 1, 5), chips[0].stack)

        val chipId = chips[0].id

        // First removal: should remove the $5 chip, leaving $2
        chips = GameplayCalculator.removeOrDeductChip(chips, chipId, ChipRemovalMode.SINGLE_CHIP, deductionValue = 5)
        assertEquals(1, chips.size)
        assertEquals(2, chips[0].value) // Left $2
        assertEquals(listOf(1, 1), chips[0].stack)

        // Second removal (moving/removing again while selectedChipValue may still be 5):
        // MUST retain $1 instead of wiping out the entire $2!
        chips = GameplayCalculator.removeOrDeductChip(chips, chipId, ChipRemovalMode.SINGLE_CHIP, deductionValue = 5)
        assertEquals(1, chips.size)
        assertEquals(1, chips[0].value) // Retains $1!
        assertEquals(listOf(1), chips[0].stack)

        // Third removal: removes the final $1 chip completely
        chips = GameplayCalculator.removeOrDeductChip(chips, chipId, ChipRemovalMode.SINGLE_CHIP, deductionValue = 5)
        assertEquals(0, chips.size)
    }

    @Test
    fun testIncreaseAllByBaseUnit() {
        val inside = PlacedChip(id = 1L, value = 5, xPercent = 0.1f, yPercent = 0.1f, betId = "num-8")
        val outside = PlacedChip(id = 2L, value = 10, xPercent = 0.1f, yPercent = 0.9f, betId = "red")
        val chips = listOf(inside, outside)

        val increased = GameplayCalculator.increaseAllByBaseUnit(chips, insideBaseUnit = 2.0, outsideBaseUnit = 10.0, isUS = false)
        assertEquals(7, increased[0].value) // 5 + 2 = 7
        assertEquals(20, increased[1].value) // 10 + 10 = 20
    }

    @Test
    fun testRecalculateSession() {
        val chip1 = PlacedChip(id = 1L, value = 10, xPercent = 0.5f, yPercent = 0.5f, betId = "red")
        val chip2 = PlacedChip(id = 2L, value = 10, xPercent = 0.5f, yPercent = 0.5f, betId = "black")

        val rec1 = GameplaySpinRecord(spinIndex = 1, spunNumber = 1, chips = listOf(chip1), totalStake = 10.0, totalPayout = 20.0, netProfit = 10.0, bankroll = 1010.0) // 1 is Red -> +10
        val rec2 = GameplaySpinRecord(spinIndex = 2, spunNumber = 2, chips = listOf(chip2), totalStake = 10.0, totalPayout = 20.0, netProfit = 10.0, bankroll = 1020.0) // 2 is Black -> +10

        val recalculated = GameplayCalculator.recalculateSession(listOf(rec1, rec2), startBankroll = 500.0, isUS = false)
        assertEquals(510.0, recalculated[0].bankroll, 0.001)
        assertEquals(520.0, recalculated[1].bankroll, 0.001)
    }

    @Test
    fun testJsonSerializationRoundtrip() {
        val chip = PlacedChip(id = 55L, value = 15, xPercent = 0.33f, yPercent = 0.66f, betId = "split-1-2")
        val record = GameplaySpinRecord(
            spinIndex = 1,
            spunNumber = 2,
            chips = listOf(chip),
            totalStake = 15.0,
            totalPayout = 270.0,
            netProfit = 255.0,
            bankroll = 1255.0
        )

        val json = GameplayCalculator.serializeRecords(listOf(record))
        val restored = GameplayCalculator.deserializeRecords(json)

        assertEquals(1, restored.size)
        assertEquals(record.spinIndex, restored[0].spinIndex)
        assertEquals(record.spunNumber, restored[0].spunNumber)
        assertEquals(record.totalStake, restored[0].totalStake, 0.001)
        assertEquals(record.bankroll, restored[0].bankroll, 0.001)
        assertEquals(1, restored[0].chips.size)
        assertEquals(chip.betId, restored[0].chips[0].betId)
        assertEquals(chip.value, restored[0].chips[0].value)
    }

    @Test
    fun testLedgerFormatting() {
        assertEquals("Straight 17", com.roulettetactician.ui.components.formatBetIdFriendly("num-17"))
        assertEquals("Split 8/9", com.roulettetactician.ui.components.formatBetIdFriendly("split-8-9"))
        assertEquals("1st 12", com.roulettetactician.ui.components.formatBetIdFriendly("doz-1"))
        assertEquals("Red", com.roulettetactician.ui.components.formatBetIdFriendly("red"))

        val chip1 = PlacedChip(id = 1L, value = 5, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")
        val chip2 = PlacedChip(id = 2L, value = 2, xPercent = 0.5f, yPercent = 0.5f, betId = "split-8-9")
        val summary = com.roulettetactician.ui.components.formatBetsSummary(listOf(chip1, chip2))
        assertEquals("Straight 17 ($5), Split 8/9 ($2)", summary)
    }

    @Test
    fun testPreviousSpinBetsAddition() {
        val prev1 = PlacedChip(id = 1L, value = 5, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17")
        val prev2 = PlacedChip(id = 2L, value = 2, xPercent = 0.3f, yPercent = 0.3f, betId = "split-8-9")
        val prevBets = listOf(prev1, prev2)

        // 1. Adding to empty felt
        var currentChips = emptyList<PlacedChip>()
        prevBets.forEach { chip ->
            currentChips = GameplayCalculator.stackOrAddChip(currentChips, chip)
        }
        assertEquals(2, currentChips.size)
        assertEquals(7, currentChips.sumOf { it.value })

        // 2. Adding / repeating again stacks on top of existing bets
        prevBets.forEach { chip ->
            currentChips = GameplayCalculator.stackOrAddChip(currentChips, chip)
        }
        assertEquals(2, currentChips.size)
        assertEquals(14, currentChips.sumOf { it.value })
        assertEquals(10, currentChips.find { it.betId == "num-17" }?.value)
        assertEquals(4, currentChips.find { it.betId == "split-8-9" }?.value)
        assertEquals(listOf(5, 5), currentChips.find { it.betId == "num-17" }?.stack)
    }

    @Test
    fun testDecreaseAllByBaseUnit() {
        val insideChip = PlacedChip(id = 1L, value = 5, xPercent = 0.5f, yPercent = 0.5f, betId = "num-17", stack = listOf(5))
        val smallChip = PlacedChip(id = 2L, value = 1, xPercent = 0.5f, yPercent = 0.5f, betId = "num-20", stack = listOf(1))
        val outsideChip = PlacedChip(id = 3L, value = 10, xPercent = 0.5f, yPercent = 0.7f, betId = "red", stack = listOf(5, 5))

        val chips = listOf(insideChip, smallChip, outsideChip)

        // Decreasing with insideBaseUnit = 1.0, outsideBaseUnit = 5.0
        val decreased = GameplayCalculator.decreaseAllByBaseUnit(chips, insideBaseUnit = 1.0, outsideBaseUnit = 5.0, isUS = false)

        // insideChip should become 4
        // smallChip (1 - 1 = 0) should be removed!
        // outsideChip (10 - 5 = 5) should become 5
        assertEquals(2, decreased.size)
        assertEquals(4, decreased.find { it.betId == "num-17" }?.value)
        assertEquals(5, decreased.find { it.betId == "red" }?.value)
        assertEquals(null, decreased.find { it.betId == "num-20" })
    }
}
