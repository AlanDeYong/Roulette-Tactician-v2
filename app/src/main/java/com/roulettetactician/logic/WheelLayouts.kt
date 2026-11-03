package com.roulettetactician.logic

import com.roulettetactician.ui.model.TableType

object WheelLayouts {
    // 00 is represented as 37
    val EU_WHEEL = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 
        10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )

    val US_WHEEL = listOf(
        0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 
        1, 37, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2
    )

    fun getWheelSequence(type: TableType): List<Int> = when (type) {
        TableType.EU -> EU_WHEEL
        TableType.US -> US_WHEEL
    }

    /**
     * Gets 9 neighbors to the left and 9 to the right of the given midpoint.
     * Total of 19 numbers.
     */
    fun getNeighbors(midpoint: Int, type: TableType): List<Int> {
        val sequence = getWheelSequence(type)
        val index = sequence.indexOf(midpoint)
        if (index == -1) return emptyList()

        val result = mutableListOf<Int>()
        val size = sequence.size
        
        // neighbors from -9 to +9 (inclusive)
        for (i in -9..9) {
            val neighborIndex = (index + i + size) % size
            result.add(sequence[neighborIndex])
        }
        
        return result
    }

    /**
     * Finds the single wheel position that minimizes the sum of distances to all target numbers.
     * Distance is measured as the shortest arc on the wheel.
     */
    fun findOptimalMidpoint(targets: List<Int>, type: TableType): Int? {
        if (targets.isEmpty()) return null
        
        val sequence = getWheelSequence(type)
        val n = sequence.size
        val targetIndices = targets.map { sequence.indexOf(it) }.filter { it != -1 }
        
        if (targetIndices.isEmpty()) return null

        var bestIndex = -1
        var minTotalDistance = Int.MAX_VALUE

        // Iterate through all possible positions on the wheel
        for (p in 0 until n) {
            var currentTotalDistance = 0
            for (targetIdx in targetIndices) {
                // Shortest arc distance on a circle
                val rawDist = Math.abs(p - targetIdx)
                val shortestDist = Math.min(rawDist, n - rawDist)
                currentTotalDistance += shortestDist
            }

            if (currentTotalDistance < minTotalDistance) {
                minTotalDistance = currentTotalDistance
                bestIndex = p
            }
        }

        return if (bestIndex != -1) sequence[bestIndex] else null
    }
}
