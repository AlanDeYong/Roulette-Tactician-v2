package com.roulettetactician.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import com.roulettetactician.logic.WheelLayouts
import com.roulettetactician.ui.model.TableType
import kotlin.math.*

/**
 * Visual Wheel-Rotor Radar Card.
 * Displays all pockets arranged in true physical wheel sequence with radial heat spikes
 * and glowing arcs proportional to each number's accumulated Bet Trend weight.
 */
@Composable
fun WheelRotorRadarCard(
    tableType: TableType,
    numberWeights: Map<Int, Double>,
    coldBannedNumbers: Set<Int>,
    lastSpunNumber: Int?,
    modifier: Modifier = Modifier
) {
    val sequence = remember(tableType) { WheelLayouts.getWheelSequence(tableType) }
    val maxWeight = remember(numberWeights) { max(0.1, numberWeights.values.maxOrNull() ?: 1.0) }
    val topNumber = remember(numberWeights) {
        numberWeights.entries.maxByOrNull { it.value }?.key
    }

    var selectedPocket by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Wheel Rotor Heat Radar",
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (tableType == TableType.US) "(US 38-Pocket)" else "(EU 37-Pocket)",
                        color = Color(0xFF94A3B8),
                        fontSize = 8.sp
                    )
                }

                if (topNumber != null) {
                    val topW = numberWeights[topNumber] ?: 0.0
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "Top: #${if (topNumber == 37) "00" else topNumber} (${String.format("%.2f", topW)})",
                            color = Color(0xFFFBBF24),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Radar Circular Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center
            ) {
                WheelRotorCanvas(
                    sequence = sequence,
                    numberWeights = numberWeights,
                    maxWeight = maxWeight,
                    coldBannedNumbers = coldBannedNumbers,
                    lastSpunNumber = lastSpunNumber,
                    selectedPocket = selectedPocket,
                    onPocketTapped = { pocket ->
                        selectedPocket = if (selectedPocket == pocket) null else pocket
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Interactive Center Hub Info Display
                CenterHubInfo(
                    selectedPocket = selectedPocket,
                    numberWeights = numberWeights,
                    maxWeight = maxWeight,
                    tableType = tableType,
                    onClearSelection = { selectedPocket = null }
                )
            }
        }
    }
}

@Composable
private fun CenterHubInfo(
    selectedPocket: Int?,
    numberWeights: Map<Int, Double>,
    maxWeight: Double,
    tableType: TableType,
    onClearSelection: () -> Unit
) {
    val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Color(0xFF090D16), CircleShape)
            .border(1.5.dp, if (selectedPocket != null) Color(0xFF38BDF8) else Color(0xFF334155), CircleShape)
            .clickable { onClearSelection() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (selectedPocket != null) {
            val w = numberWeights[selectedPocket] ?: 0.0
            val isGreen = selectedPocket == 0 || selectedPocket == 37
            val isRed = !isGreen && redNumbers.contains(selectedPocket)
            val numBg = if (isGreen) Color(0xFF15803D) else if (isRed) Color(0xFFDC2626) else Color(0xFF1E293B)
            val numLabel = if (selectedPocket == 37) "00" else selectedPocket.toString()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pocket badge
                Box(
                    modifier = Modifier
                        .size(19.dp)
                        .background(numBg, RoundedCornerShape(3.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        numLabel,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "w: ${String.format("%.2f", w)}",
                    color = Color(0xFFFCD34D),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (tableType == TableType.US) "US ROTOR" else "EU ROTOR",
                    color = Color(0xFF94A3B8),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "ROTOR",
                    color = Color(0xFF64748B),
                    fontSize = 6.5.sp
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    "Peak: ${String.format("%.1f", maxWeight)}",
                    color = Color(0xFF34D399),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WheelRotorCanvas(
    sequence: List<Int>,
    numberWeights: Map<Int, Double>,
    maxWeight: Double,
    coldBannedNumbers: Set<Int>,
    lastSpunNumber: Int?,
    selectedPocket: Int?,
    onPocketTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    Canvas(
        modifier = modifier.pointerInput(sequence) {
            detectTapGestures { offset ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val dx = offset.x - cx
                val dy = offset.y - cy
                val dist = sqrt(dx * dx + dy * dy)
                val outerR = min(cx, cy) * 0.95f
                val innerR = outerR * 0.40f

                if (dist in innerR..outerR) {
                    var angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    angleDeg = (angleDeg + 360f + 90f) % 360f // Normalized from top (0 deg)
                    val step = 360f / sequence.size
                    val idx = (floor(angleDeg / step).toInt()).coerceIn(0, sequence.size - 1)
                    onPocketTapped(sequence[idx])
                }
            }
        }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val maxR = min(cx, cy) - 4f

        val spokeMaxLen = maxR * 0.22f
        val rimOuterR = maxR - spokeMaxLen
        val rimThickness = rimOuterR * 0.26f
        val rimInnerR = rimOuterR - rimThickness

        val step = 360f / sequence.size

        // 1. Draw Outer Heat Spikes & Radar Arcs
        sequence.forEachIndexed { i, num ->
            val angleDeg = i * step - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            val w = numberWeights[num] ?: 0.0
            val frac = (w / maxWeight).toFloat().coerceIn(0f, 1f)

            // Radial spike length
            val spikeLen = max(3f, spokeMaxLen * frac)
            val heatColor = getWeightColor(frac)

            // Outer Heat Arc / Spike
            val spokeStart = Offset(
                cx + rimOuterR * cos(angleRad),
                cy + rimOuterR * sin(angleRad)
            )
            val spokeEnd = Offset(
                cx + (rimOuterR + spikeLen) * cos(angleRad),
                cy + (rimOuterR + spikeLen) * sin(angleRad)
            )

            // Radial spike line
            drawLine(
                color = heatColor.copy(alpha = if (frac > 0.1f) 0.9f else 0.35f),
                start = spokeStart,
                end = spokeEnd,
                strokeWidth = max(2f, (step * (PI / 180.0) * rimOuterR).toFloat() * 0.75f)
            )

            // Outer curved cap for high weights
            if (frac > 0.4f) {
                drawArc(
                    color = heatColor,
                    startAngle = angleDeg - (step / 2f),
                    sweepAngle = step,
                    useCenter = false,
                    topLeft = Offset(cx - (rimOuterR + spikeLen), cy - (rimOuterR + spikeLen)),
                    size = Size((rimOuterR + spikeLen) * 2f, (rimOuterR + spikeLen) * 2f),
                    style = Stroke(width = 2.5f)
                )
            }
        }

        // 2. Draw Wheel Rim Segments and Pocket Numbers
        sequence.forEachIndexed { i, num ->
            val angleDeg = i * step - 90f
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            val pocketColor = when {
                num == 0 || num == 37 -> Color(0xFF15803D) // Green
                redNumbers.contains(num) -> Color(0xFFDC2626) // Red
                else -> Color(0xFF0F172A) // Black
            }

            // Draw segment background arc
            drawArc(
                color = pocketColor,
                startAngle = angleDeg - (step / 2f),
                sweepAngle = step,
                useCenter = false,
                topLeft = Offset(cx - rimOuterR + rimThickness / 2f, cy - rimOuterR + rimThickness / 2f),
                size = Size((rimOuterR - rimThickness / 2f) * 2f, (rimOuterR - rimThickness / 2f) * 2f),
                style = Stroke(width = rimThickness)
            )

            // Draw divider line
            val divRad = Math.toRadians((angleDeg - step / 2f).toDouble()).toFloat()
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(cx + rimInnerR * cos(divRad), cy + rimInnerR * sin(divRad)),
                end = Offset(cx + rimOuterR * cos(divRad), cy + rimOuterR * sin(divRad)),
                strokeWidth = 1f
            )

            // Draw Number
            val textR = rimInnerR + (rimThickness * 0.5f)
            val tx = cx + textR * cos(angleRad)
            val ty = cy + textR * sin(angleRad)

            drawContext.canvas.nativeCanvas.apply {
                withSave {
                    val rotation = when {
                        num == 0 || num == 37 -> 0f
                        i > 0 && i <= sequence.size / 2 -> angleDeg
                        else -> angleDeg + 180f
                    }
                    rotate(rotation, tx, ty)

                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = rimThickness * 0.52f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                    val label = if (num == 37) "00" else num.toString()
                    drawText(label, tx, ty + (paint.textSize / 3f), paint)
                }
            }

            // Cold Banned Indicator (Cyan outer notch)
            if (coldBannedNumbers.contains(num)) {
                val dotR = rimOuterR + 3f
                drawCircle(
                    color = Color(0xFF06B6D4),
                    radius = 2.5f,
                    center = Offset(cx + dotR * cos(angleRad), cy + dotR * sin(angleRad))
                )
            }

            // Last Spun Indicator (Gold Dolly Star ★)
            if (num == lastSpunNumber) {
                val starR = rimInnerR + (rimThickness * 0.18f)
                val sx = cx + starR * cos(angleRad)
                val sy = cy + starR * sin(angleRad)
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = android.graphics.Color.YELLOW
                        textSize = rimThickness * 0.45f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText("★", sx, sy + 3f, p)
                }
            }

            // Highlight selected pocket
            if (num == selectedPocket) {
                drawArc(
                    color = Color(0xFF38BDF8),
                    startAngle = angleDeg - (step / 2f),
                    sweepAngle = step,
                    useCenter = false,
                    topLeft = Offset(cx - rimOuterR - 2f, cy - rimOuterR - 2f),
                    size = Size((rimOuterR + 2f) * 2f, (rimOuterR + 2f) * 2f),
                    style = Stroke(width = 3f)
                )
            }
        }

        // Inner and Outer border circles
        drawCircle(
            color = Color(0xFF334155),
            radius = rimOuterR,
            center = center,
            style = Stroke(width = 1f)
        )
        drawCircle(
            color = Color(0xFF1E293B),
            radius = rimInnerR,
            center = center,
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * Maps weight fraction (0.0 to 1.0) to heat color:
 * Dark Slate (0%) -> Cyan (33%) -> Emerald (66%) -> Vibrant Amber/Gold (100%)
 */
internal fun getWeightColor(frac: Float): Color {
    val f = frac.coerceIn(0f, 1f)
    return when {
        f < 0.33f -> {
            val t = f / 0.33f
            interpolateColor(Color(0xFF334155), Color(0xFF06B6D4), t)
        }
        f < 0.66f -> {
            val t = (f - 0.33f) / 0.33f
            interpolateColor(Color(0xFF06B6D4), Color(0xFF10B981), t)
        }
        else -> {
            val t = (f - 0.66f) / 0.34f
            interpolateColor(Color(0xFF10B981), Color(0xFFF59E0B), t)
        }
    }
}

private fun interpolateColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = 1f
    )
}
