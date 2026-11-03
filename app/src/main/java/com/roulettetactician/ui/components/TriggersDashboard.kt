package com.roulettetactician.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.logic.TriggerCalculator
import com.roulettetactician.logic.TriggerCalculator.StreakStatus
import com.roulettetactician.logic.TriggerCalculator.TriggerThresholds

@Composable
fun TriggersDashboard(
    spins: List<Int>,
    thresholds: TriggerThresholds,
    onThresholdChange: (TriggerThresholds) -> Unit,
    modifier: Modifier = Modifier
) {
    val result = remember(spins, thresholds) {
        TriggerCalculator.evaluate(spins, thresholds)
    }

    var editingThresholdCategory by remember { mutableStateOf<String?>(null) }
    var currentThresholdValue by remember { mutableStateOf(4) }

    // Dialog for manual entry of threshold
    if (editingThresholdCategory != null) {
        ThresholdPickerDialog(
            title = "Set $editingThresholdCategory Threshold",
            currentValue = currentThresholdValue,
            onDismiss = { editingThresholdCategory = null },
            onConfirm = { newVal ->
                val updated = when (editingThresholdCategory) {
                    "Red / Black Hot" -> thresholds.copy(redBlack = newVal)
                    "Red / Black Cold" -> thresholds.copy(coldRedBlack = newVal)
                    "High / Low Hot" -> thresholds.copy(highLow = newVal)
                    "High / Low Cold" -> thresholds.copy(coldHighLow = newVal)
                    "Odd / Even Hot" -> thresholds.copy(oddEven = newVal)
                    "Odd / Even Cold" -> thresholds.copy(coldOddEven = newVal)
                    "Dozens Hot" -> thresholds.copy(dozens = newVal)
                    "Dozens Cold" -> thresholds.copy(coldDozens = newVal)
                    "Columns Hot" -> thresholds.copy(columns = newVal)
                    "Columns Cold" -> thresholds.copy(coldColumns = newVal)
                    else -> thresholds
                }
                onThresholdChange(updated)
                editingThresholdCategory = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- TOP SUMMARY STRIP ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2541), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.NotificationsActive,
                    contentDescription = "Trigger Monitors",
                    tint = if (result.hasAnyTriggered) Color(0xFFEF4444) else Color(0xFF10B981),
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Streak & Cold Monitors",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp
                )
                Text(
                    text = "(${spins.size} Spins)",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp
                )
            }

            // Triggered Count Badge
            if (result.hasAnyTriggered) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulseBadge")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Box(
                    modifier = Modifier
                        .alpha(pulseAlpha)
                        .background(Color(0xFFEF4444), RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${result.totalTriggeredCount} TRIGGER${if (result.totalTriggeredCount > 1) "S" else ""} ACTIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF065F46), RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All Normal (No Triggers)",
                        color = Color(0xFF6EE7B7),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- STREAK & COLD MONITOR SQUARE TILES ---
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val totalWidth = maxWidth
            val tileSpacing = 5.dp
            val minTileSize = 146.dp
            val neededWidth = minTileSize * 5 + tileSpacing * 4

            if (totalWidth >= neededWidth) {
                // Fits all 5 square tiles side-by-side on wide landscape screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(tileSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreakMonitorTile(
                        status = result.redBlack,
                        varLabel = "X",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(redBlack = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldRedBlack = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Red / Black Cold" else "Red / Black Hot"
                            currentThresholdValue = if (isCold) thresholds.coldRedBlack else thresholds.redBlack
                        },
                        modifier = Modifier.weight(1f)
                    )

                    StreakMonitorTile(
                        status = result.highLow,
                        varLabel = "Z",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(highLow = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldHighLow = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "High / Low Cold" else "High / Low Hot"
                            currentThresholdValue = if (isCold) thresholds.coldHighLow else thresholds.highLow
                        },
                        modifier = Modifier.weight(1f)
                    )

                    StreakMonitorTile(
                        status = result.oddEven,
                        varLabel = "XX",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(oddEven = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldOddEven = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Odd / Even Cold" else "Odd / Even Hot"
                            currentThresholdValue = if (isCold) thresholds.coldOddEven else thresholds.oddEven
                        },
                        modifier = Modifier.weight(1f)
                    )

                    StreakMonitorTile(
                        status = result.dozens,
                        varLabel = "Y",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(dozens = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldDozens = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Dozens Cold" else "Dozens Hot"
                            currentThresholdValue = if (isCold) thresholds.coldDozens else thresholds.dozens
                        },
                        modifier = Modifier.weight(1f)
                    )

                    StreakMonitorTile(
                        status = result.columns,
                        varLabel = "Y",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(columns = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldColumns = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Columns Cold" else "Columns Hot"
                            currentThresholdValue = if (isCold) thresholds.coldColumns else thresholds.columns
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Horizontally scrollable row if screen is narrow, maintaining square tiles
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(tileSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StreakMonitorTile(
                        status = result.redBlack,
                        varLabel = "X",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(redBlack = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldRedBlack = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Red / Black Cold" else "Red / Black Hot"
                            currentThresholdValue = if (isCold) thresholds.coldRedBlack else thresholds.redBlack
                        },
                        modifier = Modifier.width(minTileSize)
                    )

                    StreakMonitorTile(
                        status = result.highLow,
                        varLabel = "Z",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(highLow = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldHighLow = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "High / Low Cold" else "High / Low Hot"
                            currentThresholdValue = if (isCold) thresholds.coldHighLow else thresholds.highLow
                        },
                        modifier = Modifier.width(minTileSize)
                    )

                    StreakMonitorTile(
                        status = result.oddEven,
                        varLabel = "XX",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(oddEven = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldOddEven = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Odd / Even Cold" else "Odd / Even Hot"
                            currentThresholdValue = if (isCold) thresholds.coldOddEven else thresholds.oddEven
                        },
                        modifier = Modifier.width(minTileSize)
                    )

                    StreakMonitorTile(
                        status = result.dozens,
                        varLabel = "Y",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(dozens = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldDozens = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Dozens Cold" else "Dozens Hot"
                            currentThresholdValue = if (isCold) thresholds.coldDozens else thresholds.dozens
                        },
                        modifier = Modifier.width(minTileSize)
                    )

                    StreakMonitorTile(
                        status = result.columns,
                        varLabel = "Y",
                        onHotThresholdChange = { onThresholdChange(thresholds.copy(columns = it)) },
                        onColdThresholdChange = { onThresholdChange(thresholds.copy(coldColumns = it)) },
                        onOpenManualEdit = { isCold ->
                            editingThresholdCategory = if (isCold) "Columns Cold" else "Columns Hot"
                            currentThresholdValue = if (isCold) thresholds.coldColumns else thresholds.columns
                        },
                        modifier = Modifier.width(minTileSize)
                    )
                }
            }
        }
    }
}

/**
 * Compact Square Monitor Tile.
 * Squeezes Category Name, Variable Label, Hot Streak Tracker (hits, limit, progress),
 * Cold Streak Tracker (sleepers, limit, progress), Cold Breakdown, and Alert notifications.
 */
@Composable
fun StreakMonitorTile(
    status: StreakStatus,
    varLabel: String,
    onHotThresholdChange: (Int) -> Unit,
    onColdThresholdChange: (Int) -> Unit,
    onOpenManualEdit: (isCold: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTriggered = status.isAnyTriggered

    // Flashing Animation for Triggered state
    val infiniteTransition = rememberInfiniteTransition(label = "flashTransition")
    val flashAlpha by if (isTriggered) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "flashAlpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    val cardBg = if (isTriggered) Color(0xFF281018) else Color(0xFF161F38)
    val borderColor = if (isTriggered) Color(0xFFEF4444).copy(alpha = flashAlpha) else Color(0xFF334155)

    val outcomeColor = when (status.currentOutcome) {
        "Red" -> Color(0xFFDC2626)
        "Black" -> Color(0xFF1E293B)
        "High" -> Color(0xFF2563EB)
        "Low" -> Color(0xFF059669)
        "Odd" -> Color(0xFF7C3AED)
        "Even" -> Color(0xFFD97706)
        "1st Dozen", "1st Column" -> Color(0xFF0284C7)
        "2nd Dozen", "2nd Column" -> Color(0xFF9333EA)
        "3rd Dozen", "3rd Column" -> Color(0xFFEA580C)
        "Zero" -> Color(0xFF047857)
        else -> Color(0xFF475569)
    }

    Card(
        modifier = modifier
            .aspectRatio(1f)
            .border(if (isTriggered) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP HEADER: Category Title + Variable Symbol & Trigger Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = status.categoryName.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "($varLabel)",
                        color = Color(0xFF94A3B8),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isTriggered) {
                    Box(
                        modifier = Modifier
                            .alpha(flashAlpha)
                            .background(Color(0xFFEF4444), RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (status.isHotTriggered && status.isColdTriggered) "HOT+COLD"
                                   else if (status.isHotTriggered) "HOT TRIG"
                                   else "COLD TRIG",
                            color = Color.White,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                }
            }

            // 2. HOT REPEAT SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, if (status.isHotTriggered) Color(0xFFEF4444) else Color(0xFF334155), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Header row: Label + Limit stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HOT REPEAT",
                        color = Color(0xFFF87171),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text("Limit: ", color = Color(0xFF64748B), fontSize = 7.sp)
                        Text(
                            text = "${status.threshold}",
                            color = Color(0xFFFCD34D),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onOpenManualEdit(false) }
                                .padding(horizontal = 1.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                                .clickable { if (status.threshold > 1) onHotThresholdChange(status.threshold - 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                                .clickable { if (status.threshold < 99) onHotThresholdChange(status.threshold + 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Data row: Outcome + Hits + Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(outcomeColor, RoundedCornerShape(2.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = status.currentOutcome,
                                color = Color.White,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${status.currentStreak}x",
                            color = if (status.isHotTriggered) Color(0xFFEF4444) else Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val hotProgress = (status.currentStreak.toFloat() / status.threshold.toFloat()).coerceIn(0f, 1f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.5.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(hotProgress)
                                    .background(
                                        if (status.isHotTriggered) Color(0xFFEF4444) else Color(0xFF10B981),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                        Text(
                            text = "${status.currentStreak}/${status.threshold}",
                            color = if (status.isHotTriggered) Color(0xFFEF4444) else Color(0xFF94A3B8),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 3. COLD SLEEPER SECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .border(0.5.dp, if (status.isColdTriggered) Color(0xFF38BDF8) else Color(0xFF334155), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                // Header row: Label + Limit stepper
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COLD SLEEPER",
                        color = Color(0xFF38BDF8),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text("Limit: ", color = Color(0xFF64748B), fontSize = 7.sp)
                        Text(
                            text = "${status.coldThreshold}",
                            color = Color(0xFF38BDF8),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clickable { onOpenManualEdit(true) }
                                .padding(horizontal = 1.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                                .clickable { if (status.coldThreshold > 1) onColdThresholdChange(status.coldThreshold - 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("-", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                                .clickable { if (status.coldThreshold < 99) onColdThresholdChange(status.coldThreshold + 1) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Data row: Coldest Outcome + Misses + Progress
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1E293B), RoundedCornerShape(2.dp))
                                .border(0.5.dp, Color(0xFF38BDF8), RoundedCornerShape(2.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = status.coldestOutcome,
                                color = Color(0xFF38BDF8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = "${status.coldestStreak} cold",
                            color = if (status.isColdTriggered) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                            fontSize = 8.sp,
                            fontWeight = if (status.isColdTriggered) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    val coldProgress = (status.coldestStreak.toFloat() / status.coldThreshold.toFloat()).coerceIn(0f, 1f)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(36.dp)
                                .height(3.5.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(coldProgress)
                                    .background(
                                        if (status.isColdTriggered) Color(0xFF38BDF8) else Color(0xFF0284C7),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                        Text(
                            text = "${status.coldestStreak}/${status.coldThreshold}",
                            color = if (status.isColdTriggered) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // 4. COLD BREAKDOWN STRIP (Bottom)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B132B), RoundedCornerShape(2.5.dp))
                    .padding(horizontal = 3.dp, vertical = 1.5.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                status.coldBreakdown.forEach { item ->
                    val isColdest = item.coldSpins == status.coldestStreak && item.coldSpins > 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "${item.label}:",
                            color = Color(0xFF64748B),
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${item.coldSpins}",
                            color = if (isColdest) Color(0xFFFCD34D) else Color(0xFF94A3B8),
                            fontSize = 7.5.sp,
                            fontWeight = if (isColdest) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog allowing manual entry for any streak or cold threshold.
 */
@Composable
fun ThresholdPickerDialog(
    title: String,
    currentValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentValue.toString()) }
    val parsedVal = textValue.toIntOrNull()
    val isValid = parsedVal != null && parsedVal in 1..100

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2541),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enter the threshold count to trigger an alert (1 - 100):",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 3) {
                            textValue = input
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isValid && textValue.isNotEmpty(),
                    textStyle = LocalTextStyle.current.copy(
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        errorBorderColor = Color(0xFFEF4444),
                        focusedContainerColor = Color(0xFF0B132B),
                        unfocusedContainerColor = Color(0xFF0B132B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(3, 4, 5, 6, 8, 10, 12).forEach { preset ->
                        val isSelected = parsedVal == preset
                        Button(
                            onClick = { textValue = preset.toString() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)
                            ),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(28.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$preset",
                                color = if (isSelected) Color(0xFF0B132B) else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid && parsedVal != null) {
                        onConfirm(parsedVal)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF059669),
                    disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Apply (${parsedVal ?: currentValue})", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancel")
            }
        }
    )
}
