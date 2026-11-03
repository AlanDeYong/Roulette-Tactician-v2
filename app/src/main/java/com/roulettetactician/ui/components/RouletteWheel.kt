package com.roulettetactician.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.roulettetactician.logic.WheelLayouts
import com.roulettetactician.ui.model.TableType
import kotlin.math.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.withSave

@Composable
fun RouletteWheel(
    midpoints: List<Int>,
    hotNumbers: List<Int>,
    tableType: TableType,
    modifier: Modifier = Modifier,
    onTriangleClick: (Int) -> Unit = {},
    ) {
    val sequence = WheelLayouts.getWheelSequence(tableType)
    val redNumbers = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .pointerInput(midpoints, tableType) {
                detectTapGestures { offset ->
                    val centerX = size.width / 2.0f
                    val centerY = size.height / 2.0f
                    val radius = kotlin.math.min(size.width, size.height) / 2.0f
                    
                    val dx = offset.x - centerX
                    val dy = offset.y - centerY
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    // Check if click is near the rim (where triangle is)
                    if (dist > radius * 0.8f && dist < radius * 1.3f) {
                        var angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        angle = (angle + 360f + 90f) % 360f // Normalize to 0-360 starting from top
                        
                        val step = 360f / sequence.size
                        
                        midpoints.forEach { midpoint ->
                            val midIndex = sequence.indexOf(midpoint)
                            if (midIndex != -1) {
                                val midAngle = midIndex * step
                                val diff = kotlin.math.abs(angle - midAngle)
                                val shortestDiff = kotlin.math.min(diff, 360f - diff)
                                
                                if (shortestDiff < step * 0.8f) { // Click is close enough to midpoint triangle
                                    onTriangleClick(midpoint)
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = min(size.width, size.height) / 2
        val thickness = radius * 0.25f
        val innerRadius = radius - thickness

        // 1. Draw the wheel background/rim
        drawCircle(
            color = Color(0xFF0F172A),
            radius = radius,
            center = center,
            style = Stroke(width = thickness)
        )

        // 2. Draw segments and numbers
        val step = 360f / sequence.size
        sequence.forEachIndexed { index, num ->
            val angleDeg = index * step - 90f // Start from top
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()

            // Segment color
            val color = when {
                num == 0 || num == 37 -> Color(0xFF15803D) // Green
                redNumbers.contains(num) -> Color(0xFFDC2626) // Red
                else -> Color(0xFF0F172A) // Black
            }

            // Draw segment background
            drawArc(
                color = color,
                startAngle = angleDeg - step / 2,
                sweepAngle = step,
                useCenter = false,
                topLeft = Offset(center.x - radius + thickness/2, center.y - radius + thickness/2),
                size = Size((radius - thickness/2) * 2, (radius - thickness/2) * 2),
                style = Stroke(width = thickness)
            )

            // Draw segment lines (dividers)
            val lineStart = Offset(
                center.x + innerRadius * cos(angleRad - Math.toRadians(step / 2.0).toFloat()),
                center.y + innerRadius * sin(angleRad - Math.toRadians(step / 2.0).toFloat())
            )
            val lineEnd = Offset(
                center.x + radius * cos(angleRad - Math.toRadians(step / 2.0).toFloat()),
                center.y + radius * sin(angleRad - Math.toRadians(step / 2.0).toFloat())
            )
            drawLine(Color.White.copy(alpha = 0.3f), lineStart, lineEnd, strokeWidth = 1f)

            // Draw Number
            val textRadius = radius - (thickness / 2)
            val tx = center.x + textRadius * cos(angleRad)
            val ty = center.y + textRadius * sin(angleRad)

            drawContext.canvas.nativeCanvas.apply {
                withSave {

                    // Tangential orientation: numbers follow the curve of the wheel
                    val rotation = when {
                        num == 0 || num == 37 -> 0f
                        index > 0 && index <= 18 -> angleDeg // Right side: Rotate -90 from radial (angleDeg + 90 - 90)
                        else -> angleDeg + 180f // Left side: Rotate +90 from radial (angleDeg + 90 + 90)
                    }

                    rotate(rotation, tx, ty)
                    val paint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        this.textSize = thickness * 0.6f
                        this.textAlign = android.graphics.Paint.Align.CENTER
                        this.isFakeBoldText = true
                    }
                    val label = if (num == 37) "00" else num.toString()
                    drawText(label, tx, ty + (paint.textSize / 3), paint)
                }
            }

            // Draw Hot Number Indicator (Yellow Dot)
            if (hotNumbers.contains(num)) {
                val dotRadius = thickness * 0.1f
                val dotCenterRadius = innerRadius + (thickness * 0.15f)
                val dotX = center.x + dotCenterRadius * cos(angleRad)
                val dotY = center.y + dotCenterRadius * sin(angleRad)
                drawCircle(
                    color = Color.Yellow,
                    radius = dotRadius,
                    center = Offset(dotX, dotY)
                )
            }
        }

        // 3. Mark midpoints (hot numbers) and neighbors
        midpoints.forEach { midpoint ->
            val neighbors = WheelLayouts.getNeighbors(midpoint, tableType)
            if (neighbors.isNotEmpty()) {
                val midIndex = sequence.indexOf(midpoint)
                val midAngle = midIndex * step - 90f
                val midRad = Math.toRadians(midAngle.toDouble()).toFloat()

                // Draw triangle at midpoint (outer edge)
                val triSize = thickness * 0.4f
                val triTip = Offset(
                    center.x + (radius + triSize) * cos(midRad),
                    center.y + (radius + triSize) * sin(midRad)
                )
                val triBase1 = Offset(
                    center.x + radius * cos(midRad - Math.toRadians(10.0).toFloat()),
                    center.y + radius * sin(midRad - Math.toRadians(10.0).toFloat())
                )
                val triBase2 = Offset(
                    center.x + radius * cos(midRad + Math.toRadians(10.0).toFloat()),
                    center.y + radius * sin(midRad + Math.toRadians(10.0).toFloat())
                )
                
                val triPath = Path().apply {
                    moveTo(triTip.x, triTip.y)
                    lineTo(triBase1.x, triBase1.y)
                    lineTo(triBase2.x, triBase2.y)
                    close()
                }
                drawPath(triPath, Color.Yellow)

                // Draw curved line for neighbors
                val lineRadius = radius + (triSize * 0.5f)
                val startAngle = midAngle - (9 * step)
                val sweepAngle = 18 * step
                
                drawArc(
                    color = Color.Yellow,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - lineRadius, center.y - lineRadius),
                    size = Size(lineRadius * 2, lineRadius * 2),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // Draw Arrowheads
                drawArrowHead(center, lineRadius, startAngle, true)
                drawArrowHead(center, lineRadius, startAngle + sweepAngle, false)
            }
        }
    }
}

private fun DrawScope.drawArrowHead(center: Offset, radius: Float, angleDeg: Float, isStart: Boolean) {
    val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
    val tip = Offset(
        center.x + radius * cos(angleRad),
        center.y + radius * sin(angleRad)
    )
    
    val arrowSize = 10f
    val angleOffset = if (isStart) 0.2f else -0.2f
    val side1Angle = angleRad + angleOffset
    val side2Angle = angleRad + (angleOffset * 0.5f)
    
    // Better arrowhead calculation
    val tangentAngle = angleRad + (PI / 2).toFloat()
    val arrowAngle = if (isStart) tangentAngle else tangentAngle + PI.toFloat()
    
    val p1 = Offset(
        tip.x + arrowSize * cos(arrowAngle + 2.5f),
        tip.y + arrowSize * sin(arrowAngle + 2.5f)
    )
    val p2 = Offset(
        tip.x + arrowSize * cos(arrowAngle - 2.5f),
        tip.y + arrowSize * sin(arrowAngle - 2.5f)
    )

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, Color.Yellow)
}
