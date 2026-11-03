package com.roulettetactician.logic

import com.roulettetactician.ui.model.PlacedChip
import kotlin.math.max

enum class ChipRemovalMode {
    SINGLE_CHIP,
    ENTIRE_STACK
}

data class GameplaySpinRecord(
    val spinIndex: Int, // 1-based index corresponding to the spin
    val spunNumber: Int,
    val chips: List<PlacedChip>,
    val totalStake: Double,
    val totalPayout: Double,
    val netProfit: Double,
    val bankroll: Double
)

data class GameplayRoundEvaluation(
    val totalStake: Double,
    val totalPayout: Double,
    val netProfit: Double,
    val winningChips: List<PlacedChip>,
    val losingChips: List<PlacedChip>
)

object GameplayCalculator {

    private fun getCandidatesMap(isUS: Boolean): Map<String, BetTrendCalculator.CandidateBet> {
        return BetTrendCalculator.getCandidates(isUS).associateBy { it.betId }
    }

    /**
     * Resolves a CandidateBet from betId, handling alternate formatting such as
     * inverted split numbers (e.g. split-9-8 vs split-8-9) or alternative street anchors.
     */
    fun findCandidate(
        candidates: Map<String, BetTrendCalculator.CandidateBet>,
        betId: String
    ): BetTrendCalculator.CandidateBet? {
        // 1. Direct match
        candidates[betId]?.let { return it }

        // 2. Split with reversed or normalized numbers (e.g. split-9-8 vs split-8-9)
        if (betId.startsWith("split-")) {
            val parts = betId.removePrefix("split-").split("-")
            if (parts.size == 2) {
                val n1 = parts[0].toIntOrNull()
                val n2 = parts[1].toIntOrNull()
                if (n1 != null && n2 != null) {
                    val minN = minOf(n1, n2)
                    val maxN = maxOf(n1, n2)
                    candidates["split-$minN-$maxN"]?.let { return it }
                    candidates["split-$n2-$n1"]?.let { return it }
                    if (minN == 0) {
                        candidates["split-0-$maxN"]?.let { return it }
                    }
                    return BetTrendCalculator.CandidateBet(
                        betId = betId,
                        name = "Split $minN/$maxN",
                        type = "split",
                        numbers = listOf(n1, n2),
                        mask = (1L shl n1) or (1L shl n2),
                        payoutRatio = 17,
                        baseUnits = 1,
                        isInside = true,
                        xPercent = 0f,
                        yPercent = 0f
                    )
                }
            }
        }

        // 3. Street alternate indexing (e.g. street-3 vs street-1)
        if (betId.startsWith("street-")) {
            val num = betId.removePrefix("street-").toIntOrNull()
            if (num != null && num in 1..36) {
                val col = (num - 1) / 3
                val bottomNum = (col * 3) + 1
                candidates["street-$bottomNum"]?.let { return it }
                val topNum = (col * 3) + 3
                candidates["street-$topNum"]?.let { return it }

                val streetNums = listOf(col * 3 + 1, col * 3 + 2, col * 3 + 3)
                return BetTrendCalculator.CandidateBet(
                    betId = betId,
                    name = "Street ${streetNums.first()}-${streetNums.last()}",
                    type = "street",
                    numbers = streetNums,
                    mask = streetNums.fold(0L) { acc, n -> acc or (1L shl n) },
                    payoutRatio = 11,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = 0f,
                    yPercent = 0f
                )
            }
        }

        // 4. Corner / Basket alternatives
        if (betId.startsWith("corn-") || betId.startsWith("corner-")) {
            candidates[betId]?.let { return it }
            if (betId == "corner-0-1-2-3" || betId == "corn-0-1-2-3") {
                candidates["corner-0-1-2-3"]?.let { return it }
            }
        }

        return null
    }

    /**
     * Determines whether a given betId corresponds to an Inside bet or Outside bet.
     */
    fun isInsideBet(betId: String, isUS: Boolean = false): Boolean {
        val candidate = findCandidate(getCandidatesMap(isUS), betId)
        if (candidate != null) return candidate.isInside
        // Fallback heuristics based on ID naming convention
        return when {
            betId.startsWith("num-") -> true
            betId.startsWith("split-") -> true
            betId.startsWith("street-") -> true
            betId.startsWith("corn-") || betId.startsWith("corner-") -> true
            betId.startsWith("line-") -> true
            betId.startsWith("trio-") -> true
            else -> false
        }
    }

    /**
     * Evaluates payout and profit/loss for a list of placed chips against a spun number.
     * Roulette payout rule:
     * - If bet hits: payout = stake * (payoutRatio + 1) [net gain = stake * payoutRatio]
     * - If bet loses: payout = 0 [net loss = -stake]
     * Net round profit = totalPayout - totalStake
     */
    fun evaluateRound(
        chips: List<PlacedChip>,
        spunNumber: Int,
        isUS: Boolean
    ): GameplayRoundEvaluation {
        if (chips.isEmpty()) {
            return GameplayRoundEvaluation(
                totalStake = 0.0,
                totalPayout = 0.0,
                netProfit = 0.0,
                winningChips = emptyList(),
                losingChips = emptyList()
            )
        }

        val candidates = getCandidatesMap(isUS)
        var totalStake = 0.0
        var totalPayout = 0.0
        val winningChips = mutableListOf<PlacedChip>()
        val losingChips = mutableListOf<PlacedChip>()

        for (chip in chips) {
            val stake = chip.value.toDouble()
            totalStake += stake
            val candidate = findCandidate(candidates, chip.betId)

            if (candidate != null && candidate.numbers.contains(spunNumber)) {
                // Winning bet: returns original stake + winnings
                val payout = stake * (candidate.payoutRatio + 1.0)
                totalPayout += payout
                winningChips.add(chip)
            } else {
                losingChips.add(chip)
            }
        }

        val netProfit = totalPayout - totalStake
        return GameplayRoundEvaluation(
            totalStake = totalStake,
            totalPayout = totalPayout,
            netProfit = netProfit,
            winningChips = winningChips,
            losingChips = losingChips
        )
    }

    /**
     * Recalculates all round bankrolls and equity across the session starting from startBankroll.
     */
    fun recalculateSession(
        records: List<GameplaySpinRecord>,
        startBankroll: Double,
        isUS: Boolean
    ): List<GameplaySpinRecord> {
        var currentBankroll = startBankroll
        val updated = mutableListOf<GameplaySpinRecord>()

        for (record in records) {
            val eval = evaluateRound(record.chips, record.spunNumber, isUS)
            currentBankroll += eval.netProfit
            updated.add(
                record.copy(
                    totalStake = eval.totalStake,
                    totalPayout = eval.totalPayout,
                    netProfit = eval.netProfit,
                    bankroll = currentBankroll
                )
            )
        }
        return updated
    }

    /**
     * Stacks a chip onto an existing position with the same betId,
     * or adds a new chip if that position has no chips yet.
     */
    fun stackOrAddChip(
        currentChips: List<PlacedChip>,
        newChip: PlacedChip
    ): List<PlacedChip> {
        val existingIndex = currentChips.indexOfFirst { it.betId == newChip.betId }
        return if (existingIndex >= 0) {
            currentChips.mapIndexed { idx, chip ->
                if (idx == existingIndex) {
                    val combinedStack = chip.safeStack() + newChip.safeStack()
                    chip.copy(
                        value = combinedStack.sum(),
                        stack = combinedStack
                    )
                } else {
                    chip
                }
            }
        } else {
            currentChips + newChip
        }
    }

    /**
     * Removes or deducts from a chip stack based on removal mode.
     */
    fun removeOrDeductChip(
        currentChips: List<PlacedChip>,
        chipId: Long,
        mode: ChipRemovalMode,
        deductionValue: Int = 1
    ): List<PlacedChip> {
        val target = currentChips.find { it.id == chipId } ?: return currentChips

        return when (mode) {
            ChipRemovalMode.ENTIRE_STACK -> {
                currentChips.filter { it.id != chipId }
            }
            ChipRemovalMode.SINGLE_CHIP -> {
                val currentStack = target.safeStack()
                if (currentStack.size > 1) {
                    val newStack = currentStack.dropLast(1)
                    currentChips.map {
                        if (it.id == chipId) {
                            it.copy(
                                value = newStack.sum(),
                                stack = newStack
                            )
                        } else it
                    }
                } else {
                    val remValue = target.value - max(1, deductionValue)
                    if (remValue <= 0) {
                        currentChips.filter { it.id != chipId }
                    } else {
                        currentChips.map {
                            if (it.id == chipId) {
                                it.copy(
                                    value = remValue,
                                    stack = listOf(remValue)
                                )
                            } else it
                        }
                    }
                }
            }
        }
    }

    /**
     * Increases every placed chip on the felt by its base unit
     * (insideBaseUnit for inside bets, outsideBaseUnit for outside bets).
     */
    fun increaseAllByBaseUnit(
        currentChips: List<PlacedChip>,
        insideBaseUnit: Double,
        outsideBaseUnit: Double,
        isUS: Boolean
    ): List<PlacedChip> {
        val insideInc = max(1, insideBaseUnit.toInt())
        val outsideInc = max(1, outsideBaseUnit.toInt())

        return currentChips.map { chip ->
            val isInside = isInsideBet(chip.betId, isUS)
            val inc = if (isInside) insideInc else outsideInc
            val newStack = chip.safeStack() + inc
            chip.copy(
                value = newStack.sum(),
                stack = newStack
            )
        }
    }

    /**
     * Decreases every placed chip on the felt by its base unit
     * (insideBaseUnit for inside bets, outsideBaseUnit for outside bets).
     * If a chip's value drops to 0 or less, it is removed from the felt.
     */
    fun decreaseAllByBaseUnit(
        currentChips: List<PlacedChip>,
        insideBaseUnit: Double,
        outsideBaseUnit: Double,
        isUS: Boolean
    ): List<PlacedChip> {
        val insideDec = max(1, insideBaseUnit.toInt())
        val outsideDec = max(1, outsideBaseUnit.toInt())

        return currentChips.mapNotNull { chip ->
            val isInside = isInsideBet(chip.betId, isUS)
            val dec = if (isInside) insideDec else outsideDec
            val targetValue = chip.value - dec
            if (targetValue <= 0) {
                null
            } else {
                val currentStack = chip.safeStack().toMutableList()
                var remainingToDeduct = dec
                while (remainingToDeduct > 0 && currentStack.isNotEmpty()) {
                    val lastIdx = currentStack.lastIndex
                    val lastChip = currentStack[lastIdx]
                    if (lastChip <= remainingToDeduct) {
                        remainingToDeduct -= lastChip
                        currentStack.removeAt(lastIdx)
                    } else {
                        currentStack[lastIdx] = lastChip - remainingToDeduct
                        remainingToDeduct = 0
                    }
                }
                val finalStack = if (currentStack.isEmpty()) listOf(targetValue) else currentStack
                chip.copy(
                    value = targetValue,
                    stack = finalStack
                )
            }
        }
    }

    /**
     * Serialization of spin records to JSON string for Room persistence.
     */
    private val gson = com.google.gson.Gson()

    /**
     * Serialization of spin records to JSON string.
     */
    fun serializeRecords(records: List<GameplaySpinRecord>): String {
        return gson.toJson(records)
    }

    /**
     * Deserialization of spin records from JSON string.
     */
    fun deserializeRecords(json: String?): List<GameplaySpinRecord> {
        if (json.isNullOrBlank() || json == "[]") return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<GameplaySpinRecord>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
