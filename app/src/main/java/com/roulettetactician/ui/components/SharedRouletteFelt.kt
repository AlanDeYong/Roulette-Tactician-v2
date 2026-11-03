package com.roulettetactician.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.roulettetactician.logic.Geometry
import com.roulettetactician.logic.HeatmapCalculator
import com.roulettetactician.ui.model.PlacedChip
import com.roulettetactician.ui.model.TableType
import kotlin.math.roundToInt

/**
 * Modular Shared Roulette Felt Component.
 * Acts as the single source of truth for Felt rendering, coordinate projection,
 * and chip placements across both the "Felt" tab and "Bet Trend" tab.
 */
@Composable
fun SharedRouletteFelt(
    tableType: TableType,
    heatmapStats: HeatmapCalculator.HeatmapStats,
    hotOverrides: List<Int?>,
    coldOverrides: List<Int?>,
    chips: List<PlacedChip>,
    modifier: Modifier = Modifier,
    coldBannedNumbers: Set<Int> = emptySet(),
    lastSpunNumber: Int? = null,
    isInteractive: Boolean = true,
    chipSize: Dp = 18.dp,
    chipLabels: Map<Long, String> = emptyMap(),
    heatMode: FeltHeatMode = FeltHeatMode.GLOBAL_HEAT,
    numberWeights: Map<Int, Double>? = null,
    outsideHeatMap: Map<String, HeatmapCalculator.OutsideHeatInfo>? = null,
    onTapPlaceChip: ((xPercent: Float, yPercent: Float, betId: String) -> Unit)? = null,
    onChipMoved: ((id: Long, xPercent: Float, yPercent: Float, betId: String) -> Unit)? = null,
    onChipRemoved: ((id: Long) -> Unit)? = null,
    onSizeChanged: (IntSize) -> Unit = {}
) {
    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    // Felt Animations: Blinking Winning Star (Positioned above chip layer)
    val infiniteTransition = rememberInfiniteTransition(label = "feltWinningStar")
    val starBlinkAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starBlinkAlpha"
    )

    Box(
        modifier = modifier
            .aspectRatio(2.8f)
            .onSizeChanged {
                boardSize = it
                onSizeChanged(it)
            }
    ) {
        // 1. Core Felt Board Canvas
        RouletteBoard(
            heatmapStats = heatmapStats,
            hotOverrides = hotOverrides,
            coldOverrides = coldOverrides,
            tableType = tableType,
            coldBannedNumbers = coldBannedNumbers,
            lastSpunNumber = lastSpunNumber,
            heatMode = heatMode,
            numberWeights = numberWeights,
            outsideHeatMap = outsideHeatMap,
            onTap = { x, y, canvasWidth, canvasHeight ->
                if (isInteractive && onTapPlaceChip != null && canvasWidth > 0 && canvasHeight > 0) {
                    val target = Geometry.findSnapTarget(x, y, canvasWidth, canvasHeight, tableType == TableType.US)
                    if (target != null) {
                        onTapPlaceChip(target.xPercent, target.yPercent, target.betId)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Chip Overlay Layer (Aligned with canvas coordinates)
        chips.forEach { chip ->
            var currentDragOffset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .size(chipSize)
                    .offset {
                        if (boardSize.width > 0 && boardSize.height > 0) {
                            val boxCenterX = chip.xPercent * boardSize.width
                            val boxCenterY = chip.yPercent * boardSize.height

                            val chipSizePx = with(density) { chipSize.toPx() }
                            val halfChipSize = chipSizePx / 2f

                            val px = (boxCenterX - halfChipSize + currentDragOffset.x).roundToInt()
                            val py = (boxCenterY - halfChipSize + currentDragOffset.y).roundToInt()

                            IntOffset(px, py)
                        } else {
                            IntOffset.Zero
                        }
                    }
                    .then(
                        if (isInteractive) {
                            Modifier
                                .pointerInput(chip.id) {
                                    detectTapGestures(
                                        onTap = {
                                            onTapPlaceChip?.invoke(chip.xPercent, chip.yPercent, chip.betId)
                                        }
                                    )
                                }
                                .then(
                                    if (onChipMoved != null) {
                                        Modifier.pointerInput(chip.id) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    val w = boardSize.width.toFloat()
                                                    val h = boardSize.height.toFloat()
                                                    val currentX = (chip.xPercent * w) + currentDragOffset.x
                                                    val currentY = (chip.yPercent * h) + currentDragOffset.y
                                                    val target = Geometry.findSnapTarget(
                                                        currentX,
                                                        currentY,
                                                        w,
                                                        h,
                                                        tableType == TableType.US
                                                    )
                                                    if (target != null) {
                                                        onChipMoved(chip.id, target.xPercent, target.yPercent, target.betId)
                                                    } else {
                                                        onChipRemoved?.invoke(chip.id)
                                                    }
                                                    currentDragOffset = Offset.Zero
                                                }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                currentDragOffset += dragAmount
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Chip(
                    value = chip.value,
                    label = chipLabels[chip.id] ?: "$${chip.value}",
                    size = chipSize,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 3. Winning Star Overlay Layer (Rendered ABOVE chip layer so winning number is clearly visible even with placed chips)
        if (lastSpunNumber != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val coords = getWinningStarCoordinates(
                    num = lastSpunNumber,
                    w = size.width,
                    h = size.height,
                    isUSTable = tableType == TableType.US
                )
                if (coords != null) {
                    drawBlinkingWinningStar(
                        centerX = coords.first,
                        centerY = coords.second,
                        starSize = coords.third,
                        blinkAlpha = starBlinkAlpha
                    )
                }
            }
        }
    }
}
