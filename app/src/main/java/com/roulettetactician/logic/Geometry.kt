package com.roulettetactician.logic

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Handles coordinate system logic for dragging chips.
 * All logic coordinates are normalized (0.0 - 1.0) based on board canvas size.
 */
object Geometry {

    // Board Geometry Constants (Matches React GEO)
    // Canvas is split: Left(Zero)=8%, Center(Numbers)=80%, Right(2to1)=12%
    // Vertical: Main=60%, Dozens=15%, Outside=25%
    const val ZERO_W = 0.08f
    const val NUM_W = 0.80f
    const val COL_W = 0.12f
    
    const val MAIN_H = 0.60f
    const val DOZ_H = 0.15f
    const val OUT_H = 0.25f

    data class SnapTarget(
        val xPercent: Float, // Normalized X (0.0-1.0)
        val yPercent: Float, // Normalized Y (0.0-1.0)
        val betId: String,
        val description: String? = null
    )

    private fun getNumAt(row: Int, col: Int): Int {
        // row 0-2 (top, mid, bot), col 0-11
        // Grid logic: Top Row is 3, 6, 9... 
        // Logic from React: 3 - row + (col * 3)
        // row 0 (top) -> 3 + (c*3) = 3, 6, 9
        // row 1 (mid) -> 2 + (c*3) = 2, 5, 8
        // row 2 (bot) -> 1 + (c*3) = 1, 4, 7
        return 3 - row + (col * 3)
    }

    /**
     * Converts drag coordinates to a precise SnapTarget.
     * x, y, WIDTH, HEIGHT are absolute pixel values from the touch event/canvas.
     */
    fun findSnapTarget(x: Float, y: Float, width: Float, height: Float, isUSTable: Boolean = false): SnapTarget? {
        val nx = x / width
        val ny = y / height

        val gridLeft = ZERO_W
        val gridBot = MAIN_H

        // --- 1. Main Number Grid (Inside Bets) ---
        if (nx > gridLeft && nx < (gridLeft + NUM_W) && ny < (gridBot + 0.05f)) {
            // Convert to Grid Coordinates (0-12 Cols, 0-3 Rows)
            val rawCol = (nx - gridLeft) / NUM_W * 12
            val rawRow = (ny / gridBot) * 3

            val baseC = kotlin.math.floor(rawCol).toInt()
            val baseR = kotlin.math.floor(rawRow).toInt()

            val candidates = mutableListOf<Candidate>()

            fun add(offsetC: Float, offsetR: Float, type: String, idFn: () -> String) {
                val hitCX = baseC + offsetC
                val hitRY = baseR + offsetR
                val dist = sqrt((rawCol - hitCX).pow(2) + (rawRow - hitRY).pow(2))
                candidates.add(Candidate(dist, hitCX, hitRY, type, idFn))
            }

            // Straight (Center of cell)
            if (baseC in 0..11 && baseR in 0..2) {
                add(0.5f, 0.5f, "straight") { "num-${getNumAt(baseR, baseC)}" }
            }

            // Corners (Intersection of 4 numbers) & Lines (Intersection of 2 numbers on top edge? No, Line is 6 numbers usually called Double Street)
            // React logic calls "Line" the top intersections (Street + Street)
            // React loops i=0..1, j=0..1 to check nearby intersections
            for (i in 0..1) {
                for (j in 0..1) {
                    val checkC = baseC + i
                    val checkR = baseR + j
                    
                    if (checkR == 0) {
                        // Top Edge (Line Bet / Double Street) - covers 6 numbers
                        if (checkC > 0 && checkC < 12) {
                            add(i.toFloat(), 0f, "line") { 
                                "line-${getNumAt(0, checkC - 1)}-${getNumAt(0, checkC)}" 
                            }
                        }
                    } else if (checkR in 1..2) {
                        // Inner Corners (Intersection of 4)
                        if (checkC > 0 && checkC < 12) {
                            add(i.toFloat(), j.toFloat(), "corner") {
                                "corn-${getNumAt(checkR - 1, checkC - 1)}-${getNumAt(checkR, checkC)}"
                            }
                        }
                    }
                }
            }

            // Splits (Vertical)
            if (baseC in 1..11 && baseR in 0..2) {
                add(0f, 0.5f, "split-v") {
                    val n1 = getNumAt(baseR, baseC - 1)
                    val n2 = getNumAt(baseR, baseC)
                    "split-${minOf(n1, n2)}-${maxOf(n1, n2)}"
                }
            }
            else if (baseC == 0 && baseR in 0..2) {
                // Split with Zero
                add(0f, 0.5f, "split-zero") { "split-0-${getNumAt(baseR, 0)}" }
            }
            
            if (baseC < 11 && baseR in 0..2) {
                 add(1f, 0.5f, "split-v") {
                     val n1 = getNumAt(baseR, baseC)
                     val n2 = getNumAt(baseR, baseC + 1)
                     "split-${minOf(n1, n2)}-${maxOf(n1, n2)}"
                 }
            }

            // Splits (Horizontal)
            if (baseR in 1..2 && baseC in 0..11) {
                add(0.5f, 0f, "split-h") {
                    val n1 = getNumAt(baseR - 1, baseC)
                    val n2 = getNumAt(baseR, baseC)
                    "split-${minOf(n1, n2)}-${maxOf(n1, n2)}"
                }
            }
            if (baseR < 2 && baseC in 0..11) {
                add(0.5f, 1f, "split-h") {
                    val n1 = getNumAt(baseR, baseC)
                    val n2 = getNumAt(baseR + 1, baseC)
                    "split-${minOf(n1, n2)}-${maxOf(n1, n2)}"
                }
            }

            // Street (Top edge of a column) - Covers 3 numbers (Named after lowest/bottom number e.g. street-1)
            if (baseR == 0 && baseC in 0..11) {
                add(0.5f, 0f, "street") { "street-${getNumAt(2, baseC)}" }
            }
            // Street (Bottom edge of a column) - Covers 3 numbers
             if (baseR == 2 && baseC in 0..11) {
                 add(0.5f, 1f, "street") { "street-${getNumAt(2, baseC)}" }
             }
             
             // Line (Bottom edge between columns)
             if (baseR == 2 && baseC < 11) {
                 // Right corner (intersection with next column)
                 add(1f, 1f, "line") { "line-${getNumAt(0, baseC)}-${getNumAt(0, baseC + 1)}" }
             }
             if (baseR == 2 && baseC > 0) {
                 // Left corner (intersection with prev column)
                 add(0f, 1f, "line") { "line-${getNumAt(0, baseC - 1)}-${getNumAt(0, baseC)}" }
             }

             // --- Buffer Zone Snaps (Dragging slightly below the grid, baseR == 3) ---
             if (baseR == 3 && baseC in 0..11) {
                 // Street Bottom (from below)
                 add(0.5f, 0f, "street") { "street-${getNumAt(2, baseC)}" }
             }
             if (baseR == 3 && baseC < 11) {
                 // Line Bottom Right (from below) relative to Col C
                 add(1f, 0f, "line") { "line-${getNumAt(0, baseC)}-${getNumAt(0, baseC + 1)}" }
             }
             if (baseR == 3 && baseC > 0) {
                 // Line Bottom Left (from below) relative to Col C
                 add(0f, 0f, "line") { "line-${getNumAt(0, baseC - 1)}-${getNumAt(0, baseC)}" }
             }

             // --- Basket / First Four (0-1-2-3) at Bottom-Left Corner ---
             // Allow snap at bottom-left of Number 1 (Row 2, Col 0)
             if (baseC == 0 && (baseR == 2 || baseR == 3)) {
                 // Row 2: Bottom-Left (0f, 1f). Row 3: Top-Left (0f, 0f) -> Same absolute point
                 val offR = if (baseR == 2) 1f else 0f
                 add(0f, offR, "corner") { "corner-0-1-2-3" }
             }

            candidates.sortBy { it.dist }
            val winner = candidates.firstOrNull()

            if (winner != null && winner.dist < 0.6) {
                val finalX = (gridLeft + (winner.cx / 12f * NUM_W))
                val finalY = (winner.ry / 3f * gridBot)
                return SnapTarget(finalX, finalY, winner.idFn())
            }
        }

        // --- 2. Zero Section (Left) ---
        if (nx < ZERO_W && ny < MAIN_H) {
            if (isUSTable) {
                val isZero = ny < (MAIN_H / 2f)
                val targetY = if (isZero) MAIN_H * 0.25f else MAIN_H * 0.75f
                return SnapTarget(
                    (ZERO_W / 2f),
                    targetY,
                    if (isZero) "num-0" else "num-00"
                )
            } else {
                return SnapTarget(
                    (ZERO_W / 2f),
                    (MAIN_H / 2f),
                    "num-0"
                )
            }
        }

        // --- 3. 2 to 1 Section (Right) ---
        if (nx > (ZERO_W + NUM_W) && ny < MAIN_H) {
             val rowH = MAIN_H / 3f
             var rIdx = 0
             if (ny > rowH * 2) rIdx = 2
             else if (ny > rowH) rIdx = 1
             
             val ids = listOf("2to1-top", "2to1-mid", "2to1-bot")
             return SnapTarget(
                 (ZERO_W + NUM_W + (COL_W * 0.5f)),
                 (rIdx + 0.5f) * rowH,
                 ids[rIdx]
             )
        }

        // --- 4. Dozens (Bottom 1) ---
        if (ny > MAIN_H && ny < (MAIN_H + DOZ_H)) {
            if (nx > ZERO_W && nx < (ZERO_W + NUM_W)) {
                val dIdx = ((nx - ZERO_W) / NUM_W * 3).toInt()
                if (dIdx in 0..2) {
                    return SnapTarget(
                        (ZERO_W + ((dIdx + 0.5f) / 3f * NUM_W)),
                        (MAIN_H + DOZ_H / 2f),
                        "doz-${dIdx+1}"
                    )
                }
            }
        }

        // --- 5. Outside Bets (Bottom 2: Even/Odd etc) ---
        if (ny > (MAIN_H + DOZ_H)) {
             if (nx > ZERO_W && nx < (ZERO_W + NUM_W)) {
                 val zIdx = ((nx - ZERO_W) / NUM_W * 6).toInt()
                 val zones = listOf("low", "even", "red", "black", "odd", "high")
                 if (zIdx in 0..5) {
                     return SnapTarget(
                         (ZERO_W + ((zIdx + 0.5f) / 6f * NUM_W)),
                         (MAIN_H + DOZ_H + OUT_H / 2f),
                         zones[zIdx]
                     )
                 }
             }
        }

        return null
    }

    /**
     * Returns the SnapTarget (coordinates and ID) for a specific number.
     * Useful for programmatic chip placement.
     */
    fun getNumberTarget(num: Int, isUSTable: Boolean = false): SnapTarget {
        if (num == 0) {
            return if (isUSTable) {
                SnapTarget(ZERO_W / 2f, MAIN_H * 0.25f, "num-0")
            } else {
                SnapTarget(ZERO_W / 2f, MAIN_H / 2f, "num-0")
            }
        }
        
        if (num == 37) { // 00
            return SnapTarget(ZERO_W / 2f, MAIN_H * 0.75f, "num-00")
        }

        // col = (num - 1) / 3
        // num % 3: 1 -> row 2 (bottom), 2 -> row 1 (mid), 0 -> row 0 (top)
        val col = (num - 1) / 3
        val row = when (num % 3) {
            1 -> 2
            2 -> 1
            else -> 0
        }

        val nx = ZERO_W + ((col + 0.5f) / 12f * NUM_W)
        val ny = (row + 0.5f) / 3f * MAIN_H
        
        return SnapTarget(nx, ny, "num-$num")
    }

    private data class Candidate(
        val dist: Float,
        val cx: Float,
        val ry: Float,
        val type: String,
        val idFn: () -> String
    )
}
