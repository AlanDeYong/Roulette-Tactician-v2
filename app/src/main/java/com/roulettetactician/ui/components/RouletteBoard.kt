package com.roulettetactician.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import com.roulettetactician.logic.Geometry
import com.roulettetactician.logic.HeatmapCalculator
import com.roulettetactician.ui.model.TableType

enum class FeltHeatMode {
    GLOBAL_HEAT,
    TREND_WEIGHTS,
    MA_TREND
}

@Composable
fun RouletteBoard(
    heatmapStats: HeatmapCalculator.HeatmapStats,
    hotOverrides: List<Int?>,
    coldOverrides: List<Int?>,
    tableType: TableType,
    onTap: (x: Float, y: Float, canvasWidth: Float, canvasHeight: Float) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit = {},
    coldBannedNumbers: Set<Int> = emptySet(),
    lastSpunNumber: Int? = null,
    heatMode: FeltHeatMode = FeltHeatMode.GLOBAL_HEAT,
    numberWeights: Map<Int, Double>? = null,
    outsideHeatMap: Map<String, HeatmapCalculator.OutsideHeatInfo>? = null,
    drawWinningStar: Boolean = false
) {
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Felt Animations: Blinking Winning Star & Glowing Cold Borders
    val infiniteTransition = rememberInfiniteTransition(label = "feltAnimations")
    val starBlinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starBlinkAlpha"
    )
    val coldGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coldGlowAlpha"
    )

    // Tooltip State
    var tooltipTarget by remember { mutableStateOf<Geometry.SnapTarget?>(null) }
    
    // Constants matching Geometry.kt
    val geo = Geometry

    Canvas(
        modifier = modifier
            .aspectRatio(2.8f) // Standard aspect ratio for the board
            .onSizeChanged { 
                boardSize = it
                onSizeChanged(it)
            }
            .pointerInput(boardSize) {
                detectTapGestures { offset ->
                    onTap(offset.x, offset.y, boardSize.width.toFloat(), boardSize.height.toFloat())
                }
            }
    ) {
        val w = size.width
        val h = size.height

        // --- 1. Draw Background (Felt) ---
        drawRect(Color(0xFF14532D)) // Green 900 base

        // --- 2. Draw Numbers Grid ---
        // Iterate Rows (0..2) and Cols (0..11)
        val numW = w * geo.NUM_W
        val numH = h * geo.MAIN_H
        val cellW = numW / 12f
        val cellH = numH / 3f
        val startX = w * geo.ZERO_W

        val redNumbers = setOf(1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36)

        for (row in 0..2) {
            for (col in 0..11) {
                val num = 3 - row + (col * 3) // Logic to get number 1-36
                
                val cx = startX + (col * cellW)
                val cy = row * cellH
                
                // Color Logic
                val baseColor = if (redNumbers.contains(num)) Color(0xFFDC2626) else Color(0xFF0F172A)
                val heatColor = if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
                    getWeightHeatColor(num, numberWeights)
                } else {
                    HeatmapCalculator.getHeatColor(num, heatmapStats, hotOverrides, coldOverrides)
                }
                
                // Draw Gradient (Top 50% Base, Bottom 50% Heat)
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to baseColor,
                        0.5f to baseColor,
                        0.5f to heatColor,
                        1.0f to heatColor,
                        startY = cy,
                        endY = cy + cellH
                    ),
                    topLeft = Offset(cx, cy),
                    size = Size(cellW, cellH)
                )

                // Cold Banned Indicator (Thick Glowing Cyan Border)
                if (coldBannedNumbers.contains(num)) {
                    drawGlowingColdBorder(
                        topLeft = Offset(cx, cy),
                        size = Size(cellW, cellH),
                        glowAlpha = coldGlowAlpha
                    )
                }

                // Grid Lines
                drawRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(cx, cy),
                    size = Size(cellW, cellH),
                    style = Stroke(width = 1f)
                )

                // Text - Size based on cell dimensions (fits "36" without touching edges)
                val textSize = minOf(cellW * 0.85f, cellH * 0.85f)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textAlign = android.graphics.Paint.Align.CENTER
                        this.textSize = textSize
                        isFakeBoldText = true
                        setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                    }
                    val fm = paint.fontMetrics
                    val textY = cy + cellH/2 - (fm.ascent + fm.descent) / 2
                    drawText(num.toString(), cx + cellW/2, textY, paint)

                    // Decimal weight badge in bottom-right corner when in TREND_WEIGHTS mode
                    if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
                        val wVal = numberWeights[num] ?: 0.0
                        if (wVal > 0.05) {
                            val wPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textAlign = android.graphics.Paint.Align.RIGHT
                                this.textSize = cellH * 0.22f
                                isFakeBoldText = true
                                alpha = 200
                            }
                            drawText(String.format("%.1f", wVal), cx + cellW - 3f, cy + cellH - 3f, wPaint)
                        }
                    }
                }

                // Last Spun Dolly Marker (★ centered in box and blinking)
                if (drawWinningStar && lastSpunNumber == num) {
                    drawBlinkingWinningStar(
                        centerX = cx + cellW / 2f,
                        centerY = cy + cellH / 2f,
                        starSize = minOf(cellW, cellH) * 0.82f,
                        blinkAlpha = starBlinkAlpha
                    )
                }
            }
        }
        
        // --- 3. Zero (Left) ---
        val zeroW = w * geo.ZERO_W
        val zeroColor = Color(0xFF15803D)
        val heatZero = if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
            getWeightHeatColor(0, numberWeights)
        } else {
            HeatmapCalculator.getHeatColor(0, heatmapStats, hotOverrides, coldOverrides)
        }
        val heatDoubleZero = if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
            getWeightHeatColor(37, numberWeights)
        } else {
            HeatmapCalculator.getHeatColor(37, heatmapStats, hotOverrides, coldOverrides)
        }
        
        if (tableType == TableType.US) {
             val isZero = true // Logic for US 0/00 Split
             // Render Top Half (00)
             drawRect(
                 brush = Brush.verticalGradient(
                     0.0f to zeroColor, 0.5f to zeroColor, 0.5f to heatDoubleZero, 1.0f to heatDoubleZero,
                     startY = 0f, endY = numH/2
                 ),
                 topLeft = Offset(0f, 0f), size = Size(zeroW, numH/2)
             )
             if (coldBannedNumbers.contains(37)) {
                  drawGlowingColdBorder(
                      topLeft = Offset(0f, 0f),
                      size = Size(zeroW, numH / 2f),
                      glowAlpha = coldGlowAlpha
                  )
              }
               drawContext.canvas.nativeCanvas.apply {
                  val paint = android.graphics.Paint().apply {
                      color = android.graphics.Color.WHITE
                      textAlign = android.graphics.Paint.Align.CENTER
                      textSize = numH * 0.2f
                      isFakeBoldText = true
                      setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                  }
                  drawText("00", zeroW/2, numH * 0.3f, paint)
                  if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
                      val wVal = numberWeights[37] ?: 0.0
                      if (wVal > 0.05) {
                          val wp = android.graphics.Paint().apply {
                              color = android.graphics.Color.WHITE
                              textAlign = android.graphics.Paint.Align.RIGHT
                              textSize = numH * 0.12f
                              isFakeBoldText = true
                              alpha = 200
                          }
                          drawText(String.format("%.1f", wVal), zeroW - 3f, numH * 0.45f, wp)
                      }
                  }
              }
              if (drawWinningStar && lastSpunNumber == 37) {
                  drawBlinkingWinningStar(
                      centerX = zeroW / 2f,
                      centerY = numH * 0.25f,
                      starSize = numH * 0.22f,
                      blinkAlpha = starBlinkAlpha
                  )
              }
              
              // Render Bottom Half (0)
              drawRect(
                  brush = Brush.verticalGradient(
                      0.0f to zeroColor, 0.5f to zeroColor, 0.5f to heatZero, 1.0f to heatZero,
                      startY = numH/2, endY = numH
                  ),
                  topLeft = Offset(0f, numH/2), size = Size(zeroW, numH/2)
              )
              if (coldBannedNumbers.contains(0)) {
                  drawGlowingColdBorder(
                      topLeft = Offset(0f, numH / 2f),
                      size = Size(zeroW, numH / 2f),
                      glowAlpha = coldGlowAlpha
                  )
              }
               drawContext.canvas.nativeCanvas.apply {
                  val paint = android.graphics.Paint().apply {
                      color = android.graphics.Color.WHITE
                      textAlign = android.graphics.Paint.Align.CENTER
                      textSize = numH * 0.2f
                      isFakeBoldText = true
                      setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                  }
                  drawText("0", zeroW/2, numH * 0.8f, paint)
                  if (heatMode == FeltHeatMode.TREND_WEIGHTS && numberWeights != null) {
                      val wVal = numberWeights[0] ?: 0.0
                      if (wVal > 0.05) {
                          val wp = android.graphics.Paint().apply {
                              color = android.graphics.Color.WHITE
                              textAlign = android.graphics.Paint.Align.RIGHT
                              textSize = numH * 0.12f
                              isFakeBoldText = true
                              alpha = 200
                          }
                          drawText(String.format("%.1f", wVal), zeroW - 3f, numH - 3f, wp)
                      }
                  }
               }
              if (drawWinningStar && lastSpunNumber == 0) {
                  drawBlinkingWinningStar(
                      centerX = zeroW / 2f,
                      centerY = numH * 0.75f,
                      starSize = numH * 0.22f,
                      blinkAlpha = starBlinkAlpha
                  )
              }
              drawRect(Color.White.copy(alpha=0.2f), Offset(0f, numH/2), Size(zeroW, 1f), style = Stroke(1f)) // Divider Divider
              
         } else {
             // EU (Single 0)
             drawRect(
                  brush = Brush.verticalGradient(
                      0.0f to zeroColor, 0.5f to zeroColor, 0.5f to heatZero, 1.0f to heatZero,
                      startY = 0f, endY = numH
                  ),
                  topLeft = Offset(0f, 0f), size = Size(zeroW, numH)
             )
             if (coldBannedNumbers.contains(0)) {
                 drawGlowingColdBorder(
                     topLeft = Offset(0f, 0f),
                     size = Size(zeroW, numH),
                     glowAlpha = coldGlowAlpha
                 )
             }
             drawContext.canvas.nativeCanvas.apply {
                 val paint = android.graphics.Paint().apply {
                     color = android.graphics.Color.WHITE
                     textAlign = android.graphics.Paint.Align.CENTER
                     textSize = numH * 0.3f
                     isFakeBoldText = true
                     setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                 }
                 drawText("0", zeroW/2, numH/2 + (numH * 0.1f), paint)
             }
             if (drawWinningStar && lastSpunNumber == 0) {
                 drawBlinkingWinningStar(
                     centerX = zeroW / 2f,
                     centerY = numH / 2f,
                     starSize = numH * 0.35f,
                     blinkAlpha = starBlinkAlpha
                 )
             }
         }
        drawRect(Color.White.copy(alpha=0.2f), Offset(0f, 0f), Size(zeroW, numH), style = Stroke(1f))


        // --- 4. 2to1 Columns (Right) ---
        val rightX = startX + numW
        val rightW = w * geo.COL_W
        val colKeys = listOf("col3", "col2", "col1") // Row 0 (col3), Row 1 (col2), Row 2 (col1)
        
        for (i in 0..2) {
             val cy = i * cellH
             val key = colKeys[i]
             val baseColor = Color(0xFF14532D)
             val heatInfo = outsideHeatMap?.get(key)
             val heatColor = heatInfo?.color ?: baseColor

             drawRect(
                 brush = Brush.verticalGradient(
                     0.0f to baseColor,
                     0.5f to baseColor,
                     0.5f to heatColor,
                     1.0f to heatColor,
                     startY = cy,
                     endY = cy + cellH
                 ),
                 topLeft = Offset(rightX, cy),
                 size = Size(rightW, cellH)
             )
             drawRect(Color.White.copy(alpha=0.2f), Offset(rightX, cy), Size(rightW, cellH), style = Stroke(1f))
             drawContext.canvas.nativeCanvas.apply {
                 val paint = android.graphics.Paint().apply {
                     color = android.graphics.Color.WHITE
                     textAlign = android.graphics.Paint.Align.CENTER
                     textSize = cellH * 0.42f
                     isFakeBoldText = true
                     setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                 }
                 val textY = if (heatInfo != null) cy + cellH * 0.42f else cy + cellH/2 + (cellH * 0.1f)
                 drawText("2 to 1", rightX + rightW/2, textY, paint)

                 if (heatInfo != null && heatInfo.badge.isNotEmpty()) {
                     drawOutsideBadge(
                         canvas = this,
                         heatInfo = heatInfo,
                         centerX = rightX + rightW / 2f,
                         baselineY = cy + cellH * 0.88f,
                         initialTextSize = cellH * 0.22f,
                         maxAvailableWidth = rightW * 0.88f
                     )
                 }
             }
        }

        // --- 5. Dozens (Bottom) ---
        val dozH = h * geo.DOZ_H
        val dozLabels = listOf("1st 12", "2nd 12", "3rd 12")
        val dozKeys = listOf("doz1", "doz2", "doz3")
        val dozY = numH
        
        for (i in 0..2) {
            val dx = startX + (i * (numW / 3))
            val dw = numW / 3
            val key = dozKeys[i]
            val baseColor = Color(0xFF14532D)
            val heatInfo = outsideHeatMap?.get(key)
            val heatColor = heatInfo?.color ?: baseColor

            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to baseColor,
                    0.5f to baseColor,
                    0.5f to heatColor,
                    1.0f to heatColor,
                    startY = dozY,
                    endY = dozY + dozH
                ),
                topLeft = Offset(dx, dozY),
                size = Size(dw, dozH)
            )
            drawRect(Color.White.copy(alpha=0.2f), Offset(dx, dozY), Size(dw, dozH), style = Stroke(1f))

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = dozH * 0.48f
                    isFakeBoldText = true
                    setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                }
                val labelY = if (heatInfo != null) dozY + dozH * 0.45f else dozY + dozH * 0.65f
                drawText(dozLabels[i], dx + dw/2, labelY, paint)

                if (heatInfo != null && heatInfo.badge.isNotEmpty()) {
                    drawOutsideBadge(
                        canvas = this,
                        heatInfo = heatInfo,
                        centerX = dx + dw / 2f,
                        baselineY = dozY + dozH * 0.88f,
                        initialTextSize = dozH * 0.25f,
                        maxAvailableWidth = dw * 0.88f
                    )
                }
            }
        }

        // --- 6. Outside Bets (Bottom Bottom) ---
        val outH = h * geo.OUT_H
        val outY = dozY + dozH
        val outLabels = listOf("1-18", "EVEN", "RED", "BLACK", "ODD", "19-36")
        val outKeys = listOf("low", "even", "red", "black", "odd", "high")
        val outW = numW / 6
        
        for (i in 0..5) {
            val ox = startX + (i * outW)
            val key = outKeys[i]
            val baseColor = when (i) {
                2 -> Color(0xFFDC2626) // Red
                3 -> Color(0xFF0F172A) // Black
                else -> Color(0xFF14532D) // Green
            }
            val heatInfo = outsideHeatMap?.get(key)
            val heatColor = heatInfo?.color ?: baseColor

            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to baseColor,
                    0.5f to baseColor,
                    0.5f to heatColor,
                    1.0f to heatColor,
                    startY = outY,
                    endY = outY + outH
                ),
                topLeft = Offset(ox, outY),
                size = Size(outW, outH)
            )
            drawRect(Color.White.copy(alpha=0.2f), Offset(ox, outY), Size(outW, outH), style = Stroke(1f))

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = outH * 0.38f
                    isFakeBoldText = true
                    setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
                }
                val labelY = if (heatInfo != null) outY + outH * 0.42f else outY + outH * 0.65f
                drawText(outLabels[i], ox + outW/2, labelY, paint)

                if (heatInfo != null && heatInfo.badge.isNotEmpty()) {
                    drawOutsideBadge(
                        canvas = this,
                        heatInfo = heatInfo,
                        centerX = ox + outW / 2f,
                        baselineY = outY + outH * 0.88f,
                        initialTextSize = outH * 0.18f,
                        maxAvailableWidth = outW * 0.86f
                    )
                }
            }
        }

        // --- 7. S-Pen Tooltip Overlay ---
        tooltipTarget?.let { target ->
            // Only show for numbers or valid bets
            if (target.betId.startsWith("num-")) {
                val numStr = target.betId.substringAfter("num-")
                val num = numStr.toIntOrNull()
                if (num != null) {
                   val stats = heatmapStats.ranks[num]
                   val text = "Num $num: Rank #${stats?.rank ?: "-"} | Score: ${(stats?.score ?: 0.0).toString().take(4)}"
                   
                   val paint = android.graphics.Paint().apply {
                       color = android.graphics.Color.BLACK
                       textSize = 30f
                   }
                   val textWidth = paint.measureText(text)
                   val padding = 10f
                   
                   // Draw background for tooltip
                   val tx = target.xPercent * w
                   val ty = target.yPercent * h
                   
                   drawRect(
                       color = Color(0xFFFFFFE0), // Light Yellow
                       topLeft = Offset(tx - textWidth/2 - padding, ty - 80f),
                       size = Size(textWidth + padding*2, 50f),
                       style = androidx.compose.ui.graphics.drawscope.Fill
                   )
                   
                   drawContext.canvas.nativeCanvas.apply {
                       drawText(text, tx, ty - 45f, paint.apply { textAlign = android.graphics.Paint.Align.CENTER })
                   }
                }
            }
        }
    }
}

fun DrawScope.drawCenteredText(text: String, x: Float, y: Float, size: Float) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = size
            isFakeBoldText = true
            setShadowLayer(15f, 5f, 5f, android.graphics.Color.BLACK)
        }
        drawText(text, x, y + (size * 0.3f), paint)
    }
}

private fun getWeightHeatColor(number: Int, weights: Map<Int, Double>): Color {
    val weight = weights[number] ?: 0.0
    val maxWeight = (weights.values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val norm = (weight / maxWeight).toFloat().coerceIn(0f, 1f)
    return when {
        norm < 0.25f -> interpolateColor(Color(0xFF334155), Color(0xFF06B6D4), norm / 0.25f)
        norm < 0.65f -> interpolateColor(Color(0xFF06B6D4), Color(0xFF10B981), (norm - 0.25f) / 0.40f)
        else -> interpolateColor(Color(0xFF10B981), Color(0xFFF59E0B), (norm - 0.65f) / 0.35f)
    }
}

private fun interpolateColor(c1: Color, c2: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = c1.red + (c2.red - c1.red) * f,
        green = c1.green + (c2.green - c1.green) * f,
        blue = c1.blue + (c2.blue - c1.blue) * f,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * f
    )
}

/**
 * Draws a prominent, thicker cold banned indicator border that glows on and off.
 * Uses a double-layered stroke: an outer neon cyan glow and an inner high-intensity ice-cyan core.
 */
private fun DrawScope.drawGlowingColdBorder(
    topLeft: Offset,
    size: Size,
    glowAlpha: Float
) {
    val clampedAlpha = glowAlpha.coerceIn(0f, 1f)
    val safeWidth = (size.width - 4f).coerceAtLeast(1f)
    val safeHeight = (size.height - 4f).coerceAtLeast(1f)

    // Layer 1: Outer diffuse neon cyan glow (thicker: 8.5f, pulsating with glowAlpha)
    drawRect(
        color = Color(0xFF06B6D4).copy(alpha = clampedAlpha * 0.45f),
        topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
        size = Size(safeWidth, safeHeight),
        style = Stroke(width = 8.5f)
    )
    // Layer 2: Inner high-intensity ice-cyan core (thicker: 5.0f vs previous 3.0f)
    drawRect(
        color = Color(0xFF67E8F9).copy(alpha = (0.35f + 0.65f * clampedAlpha).coerceIn(0f, 1f)),
        topLeft = Offset(topLeft.x + 2f, topLeft.y + 2f),
        size = Size(safeWidth, safeHeight),
        style = Stroke(width = 5.0f)
    )
}

/**
 * Computes exact center coordinates and size for the winning number marker on the roulette felt.
 */
internal fun getWinningStarCoordinates(num: Int, w: Float, h: Float, isUSTable: Boolean): Triple<Float, Float, Float>? {
    val geo = Geometry
    val zeroW = w * geo.ZERO_W
    val numW = w * geo.NUM_W
    val numH = h * geo.MAIN_H
    val cellW = numW / 12f
    val cellH = numH / 3f
    val startX = zeroW

    if (num == 0) {
        return if (isUSTable) {
            Triple(zeroW / 2f, numH * 0.75f, numH * 0.22f)
        } else {
            Triple(zeroW / 2f, numH / 2f, numH * 0.35f)
        }
    }
    if (num == 37) { // 00
        return if (isUSTable) {
            Triple(zeroW / 2f, numH * 0.25f, numH * 0.22f)
        } else null
    }
    if (num in 1..36) {
        val col = (num - 1) / 3
        val row = when (num % 3) {
            1 -> 2 // bottom row
            2 -> 1 // middle row
            else -> 0 // top row
        }
        val cx = startX + (col * cellW)
        val cy = row * cellH
        val centerX = cx + cellW / 2f
        val centerY = cy + cellH / 2f
        val starSize = minOf(cellW, cellH) * 0.82f
        return Triple(centerX, centerY, starSize)
    }
    return null
}

/**
 * Draws the winning number marker (★) centered precisely in the cell box,
 * blinking on and off with a radiant gold shadow aura.
 */
internal fun DrawScope.drawBlinkingWinningStar(
    centerX: Float,
    centerY: Float,
    starSize: Float,
    blinkAlpha: Float
) {
    val clampedBlink = blinkAlpha.coerceIn(0f, 1f)
    drawContext.canvas.nativeCanvas.apply {
        val fillPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.YELLOW
            alpha = (clampedBlink * 245).toInt().coerceIn(0, 255)
            this.textSize = starSize
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(
                22f,
                0f,
                0f,
                android.graphics.Color.argb((clampedBlink * 230).toInt().coerceIn(0, 255), 255, 215, 0)
            )
        }
        val fm = fillPaint.fontMetrics
        val starY = centerY - (fm.ascent + fm.descent) / 2
        drawText("★", centerX, starY, fillPaint)
    }
}

/**
 * Draws the outside bet badge, rendering a dynamic triangular trend indicator
 * whose size and color saturation scale with trend momentum (Weak = small, Moderate = medium, Strong = large)
 * when in MA_TREND mode, or regular text when neutral/other modes.
 */
private fun drawOutsideBadge(
    canvas: android.graphics.Canvas,
    heatInfo: HeatmapCalculator.OutsideHeatInfo,
    centerX: Float,
    baselineY: Float,
    initialTextSize: Float,
    maxAvailableWidth: Float
) {
    if (heatInfo.badge.isEmpty()) return

    val badgePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = initialTextSize
        isFakeBoldText = true
        setShadowLayer(8f, 2f, 2f, android.graphics.Color.BLACK)
    }

    if (heatInfo.trendDirection == HeatmapCalculator.TrendDirection.NONE) {
        badgePaint.textAlign = android.graphics.Paint.Align.CENTER
        val measured = badgePaint.measureText(heatInfo.badge)
        if (measured > maxAvailableWidth && measured > 0f) {
            badgePaint.textSize *= (maxAvailableWidth / measured)
        }
        canvas.drawText(heatInfo.badge, centerX, baselineY, badgePaint)
        return
    }

    // Dynamic triangular trend indicator (Option 1: 3-tier size based on momentum/acceleration)
    val baseTextSize = badgePaint.textSize
    var (triH, triW) = when (heatInfo.trendStrength) {
        HeatmapCalculator.TrendStrength.WEAK -> Pair(baseTextSize * 0.65f, baseTextSize * 0.70f)
        HeatmapCalculator.TrendStrength.MODERATE -> Pair(baseTextSize * 1.05f, baseTextSize * 1.10f)
        HeatmapCalculator.TrendStrength.STRONG -> Pair(baseTextSize * 1.55f, baseTextSize * 1.60f)
        HeatmapCalculator.TrendStrength.NONE -> Pair(baseTextSize * 0.90f, baseTextSize * 0.95f)
    }

    val triColor = when (heatInfo.trendDirection) {
        HeatmapCalculator.TrendDirection.UP -> when (heatInfo.trendStrength) {
            HeatmapCalculator.TrendStrength.STRONG -> android.graphics.Color.rgb(74, 222, 128) // Neon / bright lime green
            HeatmapCalculator.TrendStrength.MODERATE -> android.graphics.Color.rgb(34, 197, 94) // Emerald green
            HeatmapCalculator.TrendStrength.WEAK -> android.graphics.Color.argb(210, 134, 239, 172) // Soft pastel green
            else -> android.graphics.Color.rgb(34, 197, 94)
        }
        HeatmapCalculator.TrendDirection.DOWN -> when (heatInfo.trendStrength) {
            HeatmapCalculator.TrendStrength.STRONG -> android.graphics.Color.rgb(255, 30, 66) // Intense hot red
            HeatmapCalculator.TrendStrength.MODERATE -> android.graphics.Color.rgb(239, 68, 68) // Vivid red
            HeatmapCalculator.TrendStrength.WEAK -> android.graphics.Color.argb(210, 252, 165, 165) // Soft coral red
            else -> android.graphics.Color.rgb(239, 68, 68)
        }
        HeatmapCalculator.TrendDirection.NONE -> android.graphics.Color.WHITE
    }

    val displayText = heatInfo.badge.removePrefix("▲ ").removePrefix("▼ ").removePrefix("— ")
    val textW = badgePaint.measureText(displayText)
    var spacing = triW * 0.45f
    var totalW = triW + spacing + textW

    if (totalW > maxAvailableWidth && totalW > 0f) {
        val scale = maxAvailableWidth / totalW
        badgePaint.textSize *= scale
        triW *= scale
        triH *= scale
        spacing *= scale
        totalW = maxAvailableWidth
    }

    val startX = centerX - totalW / 2f
    val triCenterX = startX + triW / 2f
    val textStartX = startX + triW + spacing

    val fontMetrics = badgePaint.fontMetrics
    val textCenterY = baselineY - (-fontMetrics.ascent * 0.38f)

    val path = android.graphics.Path().apply {
        if (heatInfo.trendDirection == HeatmapCalculator.TrendDirection.UP) {
            moveTo(triCenterX, textCenterY - triH / 2f)
            lineTo(triCenterX + triW / 2f, textCenterY + triH / 2f)
            lineTo(triCenterX - triW / 2f, textCenterY + triH / 2f)
            close()
        } else {
            moveTo(triCenterX - triW / 2f, textCenterY - triH / 2f)
            lineTo(triCenterX + triW / 2f, textCenterY - triH / 2f)
            lineTo(triCenterX, textCenterY + triH / 2f)
            close()
        }
    }

    // Outer dark stroke for maximum contrast
    val strokePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(200, 0, 0, 0)
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = if (heatInfo.trendStrength == HeatmapCalculator.TrendStrength.STRONG) 2.5f else 1.5f
        isAntiAlias = true
    }
    canvas.drawPath(path, strokePaint)

    // Inner fill with optional glow for strong momentum
    val fillPaint = android.graphics.Paint().apply {
        color = triColor
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
        if (heatInfo.trendStrength == HeatmapCalculator.TrendStrength.STRONG) {
            setShadowLayer(8f, 0f, 0f, triColor)
        }
    }
    canvas.drawPath(path, fillPaint)

    // Draw text right after triangle
    badgePaint.textAlign = android.graphics.Paint.Align.LEFT
    canvas.drawText(displayText, textStartX, baselineY, badgePaint)
}


