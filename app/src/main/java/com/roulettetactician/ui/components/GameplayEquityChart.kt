package com.roulettetactician.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.logic.GameplaySpinRecord
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity

private fun calculateNiceYTicks(minVal: Double, maxVal: Double, maxTickCount: Int = 7): List<Double> {
    val span = max(10.0, maxVal - minVal)
    val rawStep = span / max(2, maxTickCount - 1)
    val exp = floor(log10(rawStep))
    val fraction = rawStep / 10.0.pow(exp)

    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    val step = niceFraction * 10.0.pow(exp)

    val startTick = floor(minVal / step) * step
    val endTick = ceil(maxVal / step) * step

    val ticks = mutableListOf<Double>()
    var curr: Double = startTick
    while (curr <= endTick + (step * 0.001)) {
        if (curr >= minVal - (step * 0.3) && curr <= maxVal + (step * 0.3)) {
            ticks.add(curr)
        }
        curr += step
    }
    return if (ticks.size >= 2) ticks else listOf(minVal, (minVal + maxVal) / 2.0, maxVal)
}

private fun calculateNiceXStep(span: Int, targetCount: Int = 8): Int {
    if (span <= targetCount) return 1
    val rawStep = span.toDouble() / targetCount
    val exp = floor(log10(rawStep))
    val fraction = rawStep / 10.0.pow(exp)

    val niceFraction = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return (niceFraction * 10.0.pow(exp)).roundToInt().coerceAtLeast(1)
}

@Composable
fun GameplayEquityCard(
    records: List<GameplaySpinRecord>,
    startBankroll: Double,
    scrubberSpinIndex: Int? = null,
    onScrubberChange: ((Int) -> Unit)? = null,
    chartHeight: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    val currentBankroll = records.lastOrNull()?.bankroll ?: startBankroll
    val netProfit = currentBankroll - startBankroll
    val winCount = records.count { it.netProfit > 0 }
    val winRate = if (records.isNotEmpty()) (winCount.toDouble() / records.size * 100.0) else 0.0

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Header with Title, Zoom Controls, Bankroll and P/L
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title & Spin count & Zoom Controls
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Game Play Progress",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "(${records.size} Spins)",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp
                    )

                    Spacer(Modifier.width(4.dp))

                    // Zoom Controls Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(1.dp)
                    ) {
                        // Zoom Out [-]
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(enabled = zoomScale > 1f) {
                                    val newZoom = (zoomScale / 1.5f).coerceAtLeast(1f)
                                    zoomScale = newZoom
                                    if (newZoom == 1f) panOffset = 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "−",
                                color = if (zoomScale > 1f) Color(0xFF38BDF8) else Color(0xFF475569),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Pan Earlier [◀] when zoomed in
                        if (zoomScale > 1f) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        panOffset = (panOffset + 120f).coerceAtMost(0f)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "◀",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Zoom Level Indicator / Reset [1.0x]
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .background(
                                    if (zoomScale > 1f) Color(0xFF2563EB).copy(alpha = 0.25f) else Color.Transparent,
                                    RoundedCornerShape(2.dp)
                                )
                                .clickable {
                                    zoomScale = 1f
                                    panOffset = 0f
                                }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (zoomScale > 1f) "${String.format("%.1f", zoomScale)}x" else "1.0x",
                                color = if (zoomScale > 1f) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Pan Later [▶] when zoomed in
                        if (zoomScale > 1f) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        panOffset = panOffset - 120f
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "▶",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Zoom In [+]
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(enabled = zoomScale < 8f) {
                                    zoomScale = (zoomScale * 1.5f).coerceAtMost(8f)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "+",
                                color = if (zoomScale < 8f) Color(0xFF38BDF8) else Color(0xFF475569),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Financial Summary
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Base: $${startBankroll.roundToInt()} | Bank: $${currentBankroll.roundToInt()}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${if (netProfit >= 0) "+$" else "-$"}${abs(netProfit).roundToInt()} (${String.format("%.1f", winRate)}% Win)",
                        color = if (netProfit >= 0) Color(0xFF10B981) else Color(0xFFF43F5E),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Chart area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                GameplayEquityChart(
                    records = records,
                    startBankroll = startBankroll,
                    scrubberSpinIndex = scrubberSpinIndex,
                    onScrubberChange = onScrubberChange,
                    zoomScale = zoomScale,
                    panOffset = panOffset,
                    onZoomChange = { newZoom, newPan ->
                        zoomScale = newZoom
                        panOffset = newPan
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun GameplayEquityChart(
    records: List<GameplaySpinRecord>,
    startBankroll: Double,
    scrubberSpinIndex: Int? = null,
    onScrubberChange: ((Int) -> Unit)? = null,
    zoomScale: Float,
    panOffset: Float,
    onZoomChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No spins recorded yet in this session", color = Color.Gray, fontSize = 10.sp)
        }
        return
    }

    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentPanOffset by rememberUpdatedState(panOffset)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)
    val currentOnScrubberChange by rememberUpdatedState(onScrubberChange)

    var activeRecord by remember { mutableStateOf<GameplaySpinRecord?>(null) }

    val density = LocalDensity.current
    val padLeft = with(density) { 54.dp.toPx() }
    val padRight = with(density) { 14.dp.toPx() }
    val padTop = with(density) { 12.dp.toPx() }
    val padBottom = with(density) { 26.dp.toPx() }

    fun findClosestRecord(touchX: Float, width: Float): GameplaySpinRecord {
        if (records.size <= 1) return records.first()
        val chartWidth = width - padLeft - padRight
        val virtualWidth = chartWidth * currentZoomScale
        val xInVirtual = (touchX - padLeft - currentPanOffset).coerceIn(0f, virtualWidth)
        val frac = xInVirtual / virtualWidth
        val idx = (frac * (records.size - 1)).roundToInt().coerceIn(0, records.size - 1)
        return records[idx]
    }

    Box(
        modifier = modifier
            .pointerInput(records.size) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isMultiTouch = false
                    var totalDragX = 0f
                    var localPan = currentPanOffset

                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size >= 2) {
                            isMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            if (zoomChange != 1f || panChange.x != 0f) {
                                val chartWidth = size.width.toFloat() - padLeft - padRight
                                val newZoom = (currentZoomScale * zoomChange).coerceIn(1f, 8f)
                                val newMaxPan = chartWidth * (newZoom - 1f)
                                val centroidX = (event.calculateCentroid().x - padLeft).coerceIn(0f, chartWidth)
                                val panAdjustment = centroidX * (1f - zoomChange)
                                localPan = (localPan + panChange.x + panAdjustment).coerceIn(-newMaxPan, 0f)
                                currentOnZoomChange(newZoom, localPan)
                                event.changes.forEach { it.consume() }
                            }
                        } else if (event.changes.size == 1 && !isMultiTouch) {
                            val change = event.changes.first()
                            if (change.pressed) {
                                val dragAmount = change.position.x - change.previousPosition.x
                                totalDragX += abs(dragAmount)

                                if (currentZoomScale > 1f) {
                                    val chartWidth = size.width.toFloat() - padLeft - padRight
                                    val maxPan = chartWidth * (currentZoomScale - 1f)
                                    localPan = (localPan + dragAmount).coerceIn(-maxPan, 0f)
                                    currentOnZoomChange(currentZoomScale, localPan)
                                    change.consume()
                                } else {
                                    val pt = findClosestRecord(change.position.x, size.width.toFloat())
                                    activeRecord = pt
                                    currentOnScrubberChange?.invoke(pt.spinIndex)
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!isMultiTouch && totalDragX < 10f) {
                        val pt = findClosestRecord(down.position.x, size.width.toFloat())
                        activeRecord = pt
                        currentOnScrubberChange?.invoke(pt.spinIndex)
                    }
                    activeRecord = null
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val chartWidth = w - padLeft - padRight
            val virtualWidth = chartWidth * currentZoomScale

            // Enforce panOffset bounds
            val maxPan = chartWidth * (currentZoomScale - 1f)
            val effectivePan = currentPanOffset.coerceIn(-maxPan, 0f)

            fun getX(idx: Int): Float {
                val frac = idx.toFloat() / max(1, records.size - 1)
                return padLeft + (frac * virtualWidth) + effectivePan
            }

            // Determine visible records range for dynamic Y-scaling
            val visibleIndices = records.indices.filter { idx ->
                val px = getX(idx)
                px >= (padLeft - 15f) && px <= (w - padRight + 15f)
            }.ifEmpty { records.indices.toList() }

            val visibleRecords = visibleIndices.map { records[it] }
            val bankrolls = visibleRecords.map { it.bankroll }
            val minB = minOf(startBankroll, bankrolls.minOrNull() ?: startBankroll)
            val maxB = maxOf(startBankroll, bankrolls.maxOrNull() ?: startBankroll)
            val ySpan = max(20.0, maxB - minB)

            val yMin = minB - (ySpan * 0.08)
            val yMax = maxB + (ySpan * 0.08)
            val yRange = max(10.0, yMax - yMin)

            fun getY(b: Double): Float {
                return (h - padBottom) - (((b - yMin) / yRange) * (h - padTop - padBottom)).toFloat()
            }

            // 1. Draw Y-axis grid lines and labels
            val yTicks = calculateNiceYTicks(minB, maxB, maxTickCount = 7)
            val yTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = with(density) { 9.sp.toPx() }
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }

            yTicks.forEach { tickVal ->
                val ty = getY(tickVal)
                if (ty >= padTop && ty <= h - padBottom) {
                    val isBase = abs(tickVal - startBankroll) < 0.001
                    drawLine(
                        color = if (isBase) Color(0xFFEAB308).copy(alpha = 0.55f) else Color(0xFF334155).copy(alpha = 0.35f),
                        start = Offset(padLeft, ty),
                        end = Offset(w - padRight, ty),
                        strokeWidth = if (isBase) 1.2f else 1f
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "$${tickVal.roundToInt()}",
                        padLeft - with(density) { 6.dp.toPx() },
                        ty + with(density) { 3.dp.toPx() },
                        yTextPaint
                    )
                }
            }

            // Draw Base Bankroll reference line if not matching a tick
            val hasBaseInTicks = yTicks.any { abs(it - startBankroll) < 0.001 }
            if (!hasBaseInTicks) {
                val baseY = getY(startBankroll)
                if (baseY >= padTop && baseY <= h - padBottom) {
                    drawLine(
                        color = Color(0xFFEAB308).copy(alpha = 0.5f),
                        start = Offset(padLeft, baseY),
                        end = Offset(w - padRight, baseY),
                        strokeWidth = 1.2f
                    )
                }
            }

            // 2. Draw X-axis grid lines and spin milestone labels
            val xTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = with(density) { 8.5.sp.toPx() }
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val minSpin = visibleRecords.first().spinIndex
            val maxSpin = visibleRecords.last().spinIndex
            val spinSpan = max(1, maxSpin - minSpin)

            val xStep = calculateNiceXStep(spinSpan, targetCount = 8)
            val firstTickSpin = ((minSpin + xStep - 1) / xStep) * xStep

            val xTickSpins = mutableListOf<Int>()
            var curSpin = firstTickSpin
            while (curSpin <= maxSpin) {
                xTickSpins.add(curSpin)
                curSpin += xStep
            }

            if (minSpin == records.first().spinIndex && (xTickSpins.isEmpty() || xTickSpins.first() - minSpin >= xStep * 0.6)) {
                xTickSpins.add(0, minSpin)
            }

            xTickSpins.distinct().forEach { s ->
                val recIdx = records.indexOfFirst { it.spinIndex == s }
                val px = if (recIdx >= 0) {
                    getX(recIdx)
                } else {
                    val frac = (s - 1).toFloat() / max(1, records.size - 1)
                    padLeft + (frac * virtualWidth) + effectivePan
                }

                if (px >= padLeft + with(density) { 4.dp.toPx() } && px <= w - padRight - with(density) { 4.dp.toPx() }) {
                    // Subtle vertical grid line running across plot area
                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.55f),
                        start = Offset(px, padTop),
                        end = Offset(px, h - padBottom),
                        strokeWidth = 1f
                    )

                    // Milestone label below axis
                    drawContext.canvas.nativeCanvas.drawText(
                        "#$s",
                        px,
                        h - with(density) { 8.dp.toPx() },
                        xTextPaint
                    )
                }
            }

            // 3. Plot curves and fills clipped to the plot area
            clipRect(left = padLeft, top = padTop - 4f, right = w - padRight, bottom = h - padBottom + 1f) {
                val path = Path()
                val fillPath = Path()

                records.forEachIndexed { idx, rec ->
                    val px = getX(idx)
                    val py = getY(rec.bankroll)
                    if (idx == 0) {
                        path.moveTo(px, py)
                        fillPath.moveTo(px, h - padBottom)
                        fillPath.lineTo(px, py)
                    } else {
                        path.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }
                }

                val lastX = getX(records.size - 1)
                fillPath.lineTo(lastX, h - padBottom)
                fillPath.close()

                val endBankroll = records.last().bankroll
                val isPositive = endBankroll >= startBankroll
                val strokeColor = if (isPositive) Color(0xFF10B981) else Color(0xFFF43F5E)

                // Fill gradient below line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            strokeColor.copy(alpha = 0.28f),
                            Color.Transparent
                        ),
                        startY = padTop,
                        endY = h - padBottom
                    )
                )

                // Main trajectory line
                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Draw Scrubber Target Dot if active or matched
                val highlightedSpin = scrubberSpinIndex ?: activeRecord?.spinIndex
                if (highlightedSpin != null) {
                    val idx = records.indexOfFirst { it.spinIndex == highlightedSpin }
                    if (idx >= 0) {
                        val dotX = getX(idx)
                        val dotY = getY(records[idx].bankroll)

                        // Vertical guide line
                        drawLine(
                            color = Color(0xFFF59E0B).copy(alpha = 0.7f),
                            start = Offset(dotX, padTop),
                            end = Offset(dotX, h - padBottom),
                            strokeWidth = 1.2f
                        )

                        drawCircle(
                            color = Color(0xFFF59E0B),
                            radius = 5.5f,
                            center = Offset(dotX, dotY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5f,
                            center = Offset(dotX, dotY)
                        )
                    }
                }
            }

            // 4. Mini viewport indicator at bottom edge when zoomed in
            if (currentZoomScale > 1f) {
                val trackY = h - with(density) { 2.dp.toPx() }
                val trackThickness = with(density) { 2.dp.toPx() }
                drawLine(
                    color = Color(0xFF334155),
                    start = Offset(padLeft, trackY),
                    end = Offset(w - padRight, trackY),
                    strokeWidth = trackThickness
                )
                val viewFracStart = (-effectivePan / virtualWidth).coerceIn(0f, 1f)
                val viewFracEnd = ((-effectivePan + chartWidth) / virtualWidth).coerceIn(0f, 1f)
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(padLeft + viewFracStart * chartWidth, trackY),
                    end = Offset(padLeft + viewFracEnd * chartWidth, trackY),
                    strokeWidth = trackThickness + with(density) { 1.dp.toPx() }
                )
            }
        }
    }
}
