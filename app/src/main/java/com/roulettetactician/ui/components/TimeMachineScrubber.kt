package com.roulettetactician.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.logic.MovingAverageCalculator
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TimeMachineScrubber(
    totalSpins: Int,
    timeTravelIndex: Int?, // null = LIVE
    currentSpunNumber: Int?,
    onIndexChange: (Int?) -> Unit,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
    onReturnToLive: () -> Unit,
    onGoToStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalSpins <= 0) return

    val currentIndex = timeTravelIndex ?: totalSpins
    val isLive = timeTravelIndex == null

    var isPlaying by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isStepBackPressed by remember { mutableStateOf(false) }
    var isStepForwardPressed by remember { mutableStateOf(false) }

    val canStepBack = currentIndex > 1
    val canStepForward = currentIndex < totalSpins
    val latestCanStepBack by rememberUpdatedState(canStepBack)
    val latestCanStepForward by rememberUpdatedState(canStepForward)
    val latestOnStepBack by rememberUpdatedState(onStepBack)
    val latestOnStepForward by rememberUpdatedState(onStepForward)
    val latestTimeTravelIndex by rememberUpdatedState(timeTravelIndex)
    val latestTotalSpins by rememberUpdatedState(totalSpins)
    val latestOnReturnToLive by rememberUpdatedState(onReturnToLive)
    val latestOnIndexChange by rememberUpdatedState(onIndexChange)

    // Fast Auto-play loop: advances spins rapidly (~16 spins/sec) until reaching LIVE
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive && isPlaying) {
            delay(60L) // Fast playback speed matching scrubbing speed
            val cur = latestTimeTravelIndex ?: latestTotalSpins
            if (cur < latestTotalSpins) {
                val next = cur + 1
                if (next >= latestTotalSpins) {
                    latestOnReturnToLive()
                    isPlaying = false
                    break
                } else {
                    latestOnIndexChange(next)
                }
            } else {
                latestOnReturnToLive()
                isPlaying = false
                break
            }
        }
    }

    // Determine pocket color
    val pocketColor = when {
        currentSpunNumber == null -> Color(0xFF334155)
        currentSpunNumber == 0 || currentSpunNumber == 37 -> Color(0xFF059669) // Green (0 / 00)
        MovingAverageCalculator.RED_NUMBERS.contains(currentSpunNumber) -> Color(0xFFDC2626) // Red
        else -> Color(0xFF1E293B) // Black
    }
    val pocketText = when (currentSpunNumber) {
        null -> "-"
        37 -> "00"
        else -> currentSpunNumber.toString()
    }

    // Pulse animation for REPLAY badge
    val infiniteTransition = rememberInfiniteTransition(label = "replayPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Blinking animation for LIVE green dot
    val liveDotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveDotAlpha"
    )

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpInputText by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
            .border(1.dp, if (!isLive) Color(0xFFF59E0B).copy(alpha = 0.7f) else Color(0xFF334155), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- 1. Compact Canvas Scrubber Slider ---
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .padding(start = 2.dp)
        ) {
            CompactTimeMachineSlider(
                value = currentIndex,
                onValueChange = {
                    isPlaying = false
                    if (it > totalSpins) {
                        onReturnToLive()
                    } else {
                        onIndexChange(it)
                    }
                },
                totalSpins = totalSpins,
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- 2. Mode Status Badge (LIVE vs REPLAY) ---
        if (isLive) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF064E3B), RoundedCornerShape(3.dp))
                    .border(1.dp, Color(0xFF059669), RoundedCornerShape(3.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .graphicsLayer { alpha = liveDotAlpha }
                            .background(Color(0xFF34D399), RoundedCornerShape(2.5.dp))
                    )
                    Text("LIVE", color = Color(0xFF34D399), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .background(Color(0xFF78350F).copy(alpha = pulseAlpha), RoundedCornerShape(3.dp))
                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(3.dp))
                    .clickable {
                        isPlaying = false
                        onReturnToLive()
                    }
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("⏱ REPLAY", color = Color(0xFFFDE68A), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Text("[Go LIVE]", color = Color(0xFFFBBF24), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // --- 3. Spin Index Label & Pocket Badge (Clickable for Manual Entry) ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                .border(0.5.dp, Color(0xFF475569), RoundedCornerShape(3.dp))
                .clickable {
                    jumpInputText = currentIndex.toString()
                    showJumpDialog = true
                }
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                "Spin",
                color = Color(0xFF94A3B8),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "$currentIndex/$totalSpins",
                color = if (isLive) Color.White else Color(0xFFFCD34D),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Text("✎", color = Color(0xFF94A3B8), fontSize = 7.5.sp)

            // Spun Pocket Badge
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .defaultMinSize(minWidth = 18.dp)
                    .background(pocketColor, RoundedCornerShape(3.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pocketText,
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // --- 4. Transport Buttons ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Jump to Start (Spin 1)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(2.dp))
                    .clickable(enabled = currentIndex > 1) {
                        isPlaying = false
                        onGoToStart()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("⏮", fontSize = 9.sp, color = if (currentIndex > 1) Color.White else Color(0xFF64748B))
            }

            // Step Back 1 Spin (Click for 1 spin, or Hold to rapidly scrub backward)
            val stepBackBg = when {
                !canStepBack -> Color(0xFF1E293B).copy(alpha = 0.5f)
                isStepBackPressed -> Color(0xFF334155)
                else -> Color(0xFF1E293B)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(stepBackBg, RoundedCornerShape(2.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!latestCanStepBack) return@detectTapGestures
                                isPlaying = false
                                isStepBackPressed = true
                                latestOnStepBack() // Step 1 immediately on tap/down
                                val job = coroutineScope.launch {
                                    delay(450L) // Standard hold delay (prevents accidental 2nd step on single tap)
                                    while (isActive && latestCanStepBack) {
                                        latestOnStepBack()
                                        delay(50L) // Ultra fast continuous repeat (20 spins/sec)
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    job.cancel()
                                    isStepBackPressed = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("◀", fontSize = 9.sp, color = if (canStepBack) Color.White else Color(0xFF64748B))
            }

            // Play / Pause Auto-Playback
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(if (isPlaying) Color(0xFFB45309) else Color(0xFF1E293B), RoundedCornerShape(2.dp))
                    .clickable {
                        if (!isPlaying && (timeTravelIndex == null || currentIndex >= totalSpins)) {
                            onGoToStart()
                        }
                        isPlaying = !isPlaying
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    fontSize = 9.sp,
                    color = if (isPlaying) Color(0xFFFDE68A) else Color.White
                )
            }

            // Step Forward 1 Spin (Click for 1 spin, or Hold to rapidly scrub forward)
            val stepForwardBg = when {
                !canStepForward -> Color(0xFF1E293B).copy(alpha = 0.5f)
                isStepForwardPressed -> Color(0xFF334155)
                else -> Color(0xFF1E293B)
            }
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(stepForwardBg, RoundedCornerShape(2.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                if (!latestCanStepForward) return@detectTapGestures
                                isPlaying = false
                                isStepForwardPressed = true
                                latestOnStepForward() // Step 1 immediately on tap/down
                                val job = coroutineScope.launch {
                                    delay(450L) // Standard hold delay (prevents accidental 2nd step on single tap)
                                    while (isActive && latestCanStepForward) {
                                        latestOnStepForward()
                                        delay(50L) // Ultra fast continuous repeat (20 spins/sec)
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    job.cancel()
                                    isStepForwardPressed = false
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("▶", fontSize = 9.sp, color = if (canStepForward) Color.White else Color(0xFF64748B))
            }
        }
    }

    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Spin", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter spin number (1 to $totalSpins):",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = jumpInputText,
                        onValueChange = { jumpInputText = it.filter { ch -> ch.isDigit() } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF475569)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = jumpInputText.toIntOrNull()
                        if (target != null) {
                            isPlaying = false
                            val clamped = target.coerceIn(1, totalSpins)
                            onIndexChange(clamped)
                        }
                        showJumpDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) {
                    Text("Jump", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
private fun CompactTimeMachineSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    totalSpins: Int,
    modifier: Modifier = Modifier
) {
    if (totalSpins <= 1) return
    var widthPx by remember { mutableFloatStateOf(1f) }

    fun updateFromX(x: Float) {
        val frac = (x / widthPx).coerceIn(0f, 1f)
        val idx = 1 + (frac * (totalSpins - 1)).roundToInt()
        onValueChange(idx.coerceIn(1, totalSpins))
    }

    Canvas(
        modifier = modifier
            .pointerInput(totalSpins) {
                detectTapGestures { offset ->
                    updateFromX(offset.x)
                }
            }
            .pointerInput(totalSpins) {
                detectDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            }
    ) {
        widthPx = size.width
        val trackH = 3.dp.toPx()
        val centerY = size.height / 2f
        val thumbRadius = 5.5.dp.toPx()

        val fraction = ((value - 1).toFloat() / (totalSpins - 1).toFloat()).coerceIn(0f, 1f)
        val thumbX = thumbRadius + fraction * (size.width - thumbRadius * 2)

        // Inactive track
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(0f, centerY - trackH / 2),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
        )
        // Active track (Cyan or Amber when in replay)
        drawRoundRect(
            color = if (value < totalSpins) Color(0xFFF59E0B) else Color(0xFF0284C7),
            topLeft = Offset(0f, centerY - trackH / 2),
            size = androidx.compose.ui.geometry.Size(thumbX, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
        )
        // Thumb
        drawCircle(
            color = if (value < totalSpins) Color(0xFFFBBF24) else Color(0xFF38BDF8),
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}
