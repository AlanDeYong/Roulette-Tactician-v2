package com.roulettetactician.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpinInsertionAndUndoTest {

    @Test
    fun testInsertBefore_StartMiddleEnd() {
        // Chronological list of spins: [10, 20, 30]
        val chronological = mutableListOf(10, 20, 30)

        // 1. Insert before index 1 (before 20)
        val middleList = chronological.toMutableList().apply { add(1, 15) }
        assertEquals(listOf(10, 15, 20, 30), middleList)

        // 2. Insert before index 0 (before 10, new first spin)
        val startList = chronological.toMutableList().apply { add(0, 5) }
        assertEquals(listOf(5, 10, 20, 30), startList)

        // 3. Insert before index 2 (before 30)
        val endList = chronological.toMutableList().apply { add(2, 25) }
        assertEquals(listOf(10, 20, 25, 30), endList)
    }

    @Test
    fun testInsertAfter_StartMiddleEnd() {
        // Chronological list of spins: [10, 20, 30]
        val chronological = mutableListOf(10, 20, 30)

        // 1. Insert after index 0 (after 10)
        val afterFirst = chronological.toMutableList().apply { add(1, 12) }
        assertEquals(listOf(10, 12, 20, 30), afterFirst)

        // 2. Insert after index 1 (after 20)
        val afterMiddle = chronological.toMutableList().apply { add(2, 25) }
        assertEquals(listOf(10, 20, 25, 30), afterMiddle)

        // 3. Insert after index 2 (after 30, new latest spin)
        val afterLast = chronological.toMutableList().apply { add(3, 36) }
        assertEquals(listOf(10, 20, 30, 36), afterLast)
    }

    @Test
    fun testUndoRedoStackForImportedSpins() {
        // Emulate undo / redo stack behavior for imported spins
        val undoStack = mutableListOf<List<Int>>()
        val redoStack = mutableListOf<List<Int>>()
        var currentSpins = listOf(1, 2, 3)

        // Batch importation (camera / image) adds [17, 32, 0]
        val importedSpins = listOf(17, 32, 0)
        undoStack.add(currentSpins)
        redoStack.clear()
        currentSpins = currentSpins + importedSpins
        assertEquals(listOf(1, 2, 3, 17, 32, 0), currentSpins)
        assertTrue(undoStack.isNotEmpty())
        assertFalse(redoStack.isNotEmpty())

        // User hits UNDO in Spin History
        redoStack.add(currentSpins)
        currentSpins = undoStack.removeAt(undoStack.lastIndex)
        assertEquals(listOf(1, 2, 3), currentSpins)
        assertFalse(undoStack.isNotEmpty())
        assertTrue(redoStack.isNotEmpty())

        // User hits REDO in Spin History
        undoStack.add(currentSpins)
        currentSpins = redoStack.removeAt(redoStack.lastIndex)
        assertEquals(listOf(1, 2, 3, 17, 32, 0), currentSpins)
        assertTrue(undoStack.isNotEmpty())
        assertFalse(redoStack.isNotEmpty())
    }

    @Test
    fun testUndoRedoWithInsertionBeforeAndAfter() {
        val undoStack = mutableListOf<List<Int>>()
        val redoStack = mutableListOf<List<Int>>()
        var currentSpins = listOf(10, 20, 30)

        // Insert 15 before 20 (at index 1)
        undoStack.add(currentSpins)
        redoStack.clear()
        currentSpins = currentSpins.toMutableList().apply { add(1, 15) }
        assertEquals(listOf(10, 15, 20, 30), currentSpins)

        // Insert 25 after 20 (which is now index 2, so insert at 3)
        undoStack.add(currentSpins)
        redoStack.clear()
        currentSpins = currentSpins.toMutableList().apply { add(3, 25) }
        assertEquals(listOf(10, 15, 20, 25, 30), currentSpins)

        // Undo last insert (25)
        redoStack.add(currentSpins)
        currentSpins = undoStack.removeAt(undoStack.lastIndex)
        assertEquals(listOf(10, 15, 20, 30), currentSpins)

        // Undo prior insert (15)
        redoStack.add(currentSpins)
        currentSpins = undoStack.removeAt(undoStack.lastIndex)
        assertEquals(listOf(10, 20, 30), currentSpins)

        // Redo insert (15)
        undoStack.add(currentSpins)
        currentSpins = redoStack.removeAt(redoStack.lastIndex)
        assertEquals(listOf(10, 15, 20, 30), currentSpins)

        // Redo insert (25)
        undoStack.add(currentSpins)
        currentSpins = redoStack.removeAt(redoStack.lastIndex)
        assertEquals(listOf(10, 15, 20, 25, 30), currentSpins)
    }

    @Test
    fun testDiscardFirstAndRetainLatestSpinsWithUndoRedo() {
        val undoStack = mutableListOf<List<Int>>()
        val redoStack = mutableListOf<List<Int>>()
        var currentSpins = (1..120).map { it % 37 }

        // 1. Discard first 50 spins -> 70 latest spins remain
        undoStack.add(currentSpins)
        redoStack.clear()
        currentSpins = currentSpins.drop(50)
        assertEquals(70, currentSpins.size)
        assertEquals((51..120).map { it % 37 }, currentSpins)

        // 2. Retain only the latest 30 spins -> 30 latest spins remain
        undoStack.add(currentSpins)
        redoStack.clear()
        val discardForRetain = (currentSpins.size - 30).coerceAtLeast(0)
        currentSpins = currentSpins.drop(discardForRetain)
        assertEquals(30, currentSpins.size)
        assertEquals((91..120).map { it % 37 }, currentSpins)

        // 3. Undo retain latest 30 -> back to 70 spins
        redoStack.add(currentSpins)
        currentSpins = undoStack.removeAt(undoStack.lastIndex)
        assertEquals(70, currentSpins.size)
        assertEquals((51..120).map { it % 37 }, currentSpins)

        // 4. Undo discard first 50 -> back to all 120 spins
        redoStack.add(currentSpins)
        currentSpins = undoStack.removeAt(undoStack.lastIndex)
        assertEquals(120, currentSpins.size)
        assertEquals((1..120).map { it % 37 }, currentSpins)

        // 5. Redo discard first 50 -> 70 spins
        undoStack.add(currentSpins)
        currentSpins = redoStack.removeAt(redoStack.lastIndex)
        assertEquals(70, currentSpins.size)

        // 6. Redo retain latest 30 -> 30 spins
        undoStack.add(currentSpins)
        currentSpins = redoStack.removeAt(redoStack.lastIndex)
        assertEquals(30, currentSpins.size)
        assertEquals((91..120).map { it % 37 }, currentSpins)
    }
}
