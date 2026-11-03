package com.roulettetactician.logic

import org.junit.Assert.*
import org.junit.Test

class LogicTests {

    // --- Geometry Tests ---

    @Test
    fun testSnapTarget_Zero() {
        // Zero is LEFT 8% (0.0 to 0.08)
        // Check middle of Zero section
        val width = 1000f
        val height = 500f
        val x = width * 0.04f // 4%
        val y = height * 0.3f // 30% (Top half for 0)

        val target = Geometry.findSnapTarget(x, y, width, height)
        assertNotNull("Should find a target in Zero section", target)
        assertEquals("num-0", target?.betId)
    }

    @Test
    fun testSnapTarget_Number() {
        // Grid starts at 0.08. Width 0.80.
        // Columns: 12. Rows: 3.
        // Number 1 is Row 2 (Bottom), Col 0
        // Number 3 is Row 0 (Top), Col 0
        
        val width = 1000f
        val height = 500f
        
        // Target Number 3 (Row 0, Col 0) -> Top Left of Grid
        // Grid Left = 0.08 * 1000 = 80
        // Cell Width = 800 / 12 = 66.66
        // Cell Height = (500 * 0.6) / 3 = 100
        
        // Aim for center of Row 0, Col 0
        val targetX = 80f + 33f 
        val targetY = 50f 
        
        val target = Geometry.findSnapTarget(targetX, targetY, width, height)
        assertNotNull(target)
        assertEquals("num-3", target?.betId)
    }

    @Test
    fun testSnapTarget_OutsideBeta() {
        // Outside bets starts below Dozens. 
        // Main (0.6) + Dozens (0.15) = 0.75 start.
        // Red/Black is index 2 and 3 of 6 zones.
        
        val width = 1000f
        val height = 500f
        
        // RED is index 2. 
        // Grid Start 0.08. Width 0.8
        // Zone Width = 0.8 / 6 = 0.1333
        // Index 2 center = 0.08 + (2.5 * 0.1333) = 0.08 + 0.333 = 0.413
        
        val x = width * 0.413f
        val y = height * 0.9f // Near bottom
        
        val target = Geometry.findSnapTarget(x, y, width, height)
        assertNotNull(target)
        assertEquals("red", target?.betId)
    }

    // --- Heatmap Tests ---

    @Test
    fun testHeatmap_Frequency() {
        val history = listOf(1, 1, 1, 5, 10, 20) // 1 appears 3 times
        val stats = HeatmapCalculator.calculateStats(
            history = history,
            windowSize = 100,
            hotOverrides = emptyList(),
            coldOverrides = emptyList()
        )
        
        val rank1 = stats.ranks[1]
        val rank5 = stats.ranks[5]
        
        assertNotNull(rank1)
        assertNotNull(rank5)
        
        assertTrue("Number 1 (freq 3) should have higher score than Number 5 (freq 1)", rank1!!.score > rank5!!.score)
        assertEquals("Number 1 should be rank 0 (best)", 0, rank1.rank)
    }

    @Test
    fun testHeatmap_RecencyTieBreaker() {
        // 1 and 2 both appear once. 2 is more recent (index 0).
        val history = listOf(2, 1) 
        val stats = HeatmapCalculator.calculateStats(
            history = history,
            windowSize = 100,
            hotOverrides = emptyList(),
            coldOverrides = emptyList()
        )
        
        val rank1 = stats.ranks[1]
        val rank2 = stats.ranks[2]
        
        assertTrue("Number 2 (more recent) should have higher score than Number 1", rank2!!.score > rank1!!.score)
    }
    
    @Test
    fun testManualOverrides() {
        // Even if 36 is cold (no history), manual hot should make it hot color
        // Logic for color handles overrides, Logic for Stats handles stats.
        // Overrides exclude from Rank calculation in calculateStats.
        
        val history = listOf(1, 2, 3)
        val stats = HeatmapCalculator.calculateStats(
            history = history,
            windowSize = 100,
            hotOverrides = listOf(36), // 36 is manually HOT
            coldOverrides = emptyList()
        )
        
        // 36 should NOT be in ranks because it is manual
        assertNull("Manual override numbers should be excluded from automatic ranking", stats.ranks[36])
        
        // Test Color Logic
        val color = HeatmapCalculator.getHeatColor(36, stats, listOf(36), emptyList())
        assertEquals("Should return Manual Hot Color", HeatmapCalculator.COLOR_MANUAL_HOT, color)
    }
}
