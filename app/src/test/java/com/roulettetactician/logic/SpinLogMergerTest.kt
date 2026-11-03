package com.roulettetactician.logic

import com.roulettetactician.data.SpinLogExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinLogMergerTest {

    @Test
    fun `sanitize reverses newest-first to oldest-first chronological sequence`() {
        val rawNewestFirst = listOf("32", "0", "15", "19")
        val result = SpinLogMerger.sanitize(rawNewestFirst, allowDoubleZero = false)

        assertEquals(listOf(19, 15, 0, 32), result.spinsOldestFirst)
        assertEquals(0, result.droppedOlder)
    }

    @Test
    fun `sanitize parses 00 as 37 when allowDoubleZero is true`() {
        val rawNewestFirst = listOf("00", "7", "28")
        val result = SpinLogMerger.sanitize(rawNewestFirst, allowDoubleZero = true)

        assertEquals(listOf(28, 7, 37), result.spinsOldestFirst)
        assertEquals(0, result.droppedOlder)
    }

    @Test
    fun `sanitize rejects 00 when allowDoubleZero is false`() {
        val rawNewestFirst = listOf("12", "00", "5")
        val result = SpinLogMerger.sanitize(rawNewestFirst, allowDoubleZero = false)

        // "00" treated as invalid -> stops at "00", keeps newest "12", drops "00" and "5"
        assertEquals(listOf(12), result.spinsOldestFirst)
        assertEquals(2, result.droppedOlder)
    }

    @Test
    fun `sanitize glare drops older entries beyond the unreadable cell`() {
        // Newest is 36, then 11, then "?" (glare on screen), then 2, 4
        val rawNewestFirst = listOf("36", "11", "?", "2", "4")
        val result = SpinLogMerger.sanitize(rawNewestFirst, allowDoubleZero = true)

        // Keeps only [36, 11] reversed to [11, 36] (oldest to newest)
        assertEquals(listOf(11, 36), result.spinsOldestFirst)
        assertEquals(3, result.droppedOlder)
    }

    @Test
    fun `merge with empty existing history returns all scanned spins as new`() {
        val existing = emptyList<Int>()
        val scannedOldestFirst = listOf(1, 2, 3, 4)

        val result = SpinLogMerger.merge(existing, scannedOldestFirst)
        assertEquals(0, result.overlap)
        assertEquals(listOf(1, 2, 3, 4), result.newSpins)
        assertFalse(result.possibleGap)
    }

    @Test
    fun `merge detects trusted overlap and appends only genuinely new spins`() {
        // App history has spins ending in [10, 22, 18]
        val existing = listOf(5, 7, 10, 22, 18)
        // Camera scanned log covering [10, 22, 18] + 2 new spins [31, 9]
        val scannedOldestFirst = listOf(10, 22, 18, 31, 9)

        val result = SpinLogMerger.merge(existing, scannedOldestFirst)
        assertEquals(3, result.overlap)
        assertEquals(listOf(31, 9), result.newSpins)
        assertFalse(result.possibleGap)
    }

    @Test
    fun `merge flags possible gap when no overlap is found between non-empty histories`() {
        val existing = listOf(1, 2, 3)
        val scannedOldestFirst = listOf(15, 16, 17)

        val result = SpinLogMerger.merge(existing, scannedOldestFirst)
        assertEquals(0, result.overlap)
        assertEquals(listOf(15, 16, 17), result.newSpins)
        assertTrue(result.possibleGap)
    }

    @Test
    fun `extractor parses JSON from Gemini Interactions steps structure`() {
        val fakeResponse = """
            {
              "id": "interaction-123",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"log_found\":true,\"layout\":\"vertical_marquee\",\"newest_position\":\"top\",\"spins_newest_first\":[\"32\",\"0\",\"15\",\"00\"]}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = SpinLogExtractor.parseInteractionResponse(fakeResponse)
        assertTrue(parsed.logFound)
        assertEquals("vertical_marquee", parsed.layout)
        assertEquals("top", parsed.newestPosition)
        assertEquals(listOf("32", "0", "15", "00"), parsed.spinsNewestFirst)
    }
}
