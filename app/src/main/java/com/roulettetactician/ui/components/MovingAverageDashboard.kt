package com.roulettetactician.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import com.roulettetactician.logic.MovingAverageCalculator
import com.roulettetactician.logic.MovingAverageCalculator.MAType
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

enum class MACategoryTab(val label: String) {
    ALL("All Metrics"),
    EVENS("Evens (R/B, H/L, O/E)"),
    DOZENS_COLS("Dozens & Cols")
}

@Composable
fun MovingAverageDashboard(
    spins: List<Int>, // Chronological order: [oldest, ..., newest]
    maType: MAType,
    onMaTypeChange: (MAType) -> Unit,
    period: Int,
    onPeriodChange: (Int) -> Unit,
    activeCategory: MACategoryTab,
    onActiveCategoryChange: (MACategoryTab) -> Unit,
    almaOffset: Double = 0.85,
    onAlmaOffsetChange: (Double) -> Unit = {},
    almaSigma: Double = 6.0,
    onAlmaSigmaChange: (Double) -> Unit = {},
    kamaFast: Int = 2,
    onKamaFastChange: (Int) -> Unit = {},
    kamaSlow: Int = 30,
    onKamaSlowChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showTypeMenu by remember { mutableStateOf(false) }
    var showNDialog by remember { mutableStateOf(false) }
    var hoveredPointIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(period, maType, activeCategory, spins.size) {
        val maxPoints = spins.size - period + 1
        if (hoveredPointIndex != null && (maxPoints <= 0 || hoveredPointIndex!! >= maxPoints)) {
            hoveredPointIndex = null
        }
    }

    val config = remember(maType, period, almaOffset, almaSigma, kamaFast, kamaSlow) {
        MovingAverageCalculator.MAConfig(
            type = maType,
            period = period,
            almaOffset = almaOffset,
            almaSigma = almaSigma,
            kamaFast = kamaFast,
            kamaSlow = kamaSlow
        )
    }

    val result = remember(spins, config) {
        MovingAverageCalculator.computeAll(spins, config)
    }

    // Manual Period (N) Entry Dialog
    if (showNDialog) {
        PeriodPickerDialog(
            currentPeriod = period,
            onDismiss = { showNDialog = false },
            onConfirm = { newN ->
                onPeriodChange(newN)
                showNDialog = false
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B132B))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // --- TOP ULTRA-COMPACT CONTROL BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2541), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: MA Type Selector & Period Adjustment
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // MA Type Dropdown
                Box {
                    Button(
                        onClick = { showTypeMenu = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = maType.label,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false },
                        modifier = Modifier.background(Color(0xFF1C2541))
                    ) {
                        MAType.values().forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(type.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(type.fullName, color = Color.Gray, fontSize = 9.sp)
                                    }
                                },
                                onClick = {
                                    onMaTypeChange(type)
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                // Period (N) controls with manual entry tap
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF0B132B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    // Clickable N badge to open manual entry dialog
                    Row(
                        modifier = Modifier
                            .clickable { showNDialog = true }
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text("N: ", color = Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$period",
                            color = Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit N",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(10.dp)
                        )
                    }

                    Spacer(Modifier.width(2.dp))
                    IconButton(
                        onClick = { if (period > 2) onPeriodChange(period - 1) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease N", tint = Color.LightGray, modifier = Modifier.size(10.dp))
                    }
                    IconButton(
                        onClick = { if (period < 1000) onPeriodChange(period + 1) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase N", tint = Color.LightGray, modifier = Modifier.size(10.dp))
                    }
                }

                // Preset options buttons: 20, 50, 100, 150, 200
                listOf(20, 50, 100, 150, 200).forEach { n ->
                    val isCurrent = (n == period)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isCurrent) Color(0xFF059669) else Color(0xFF334155),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onPeriodChange(n) }
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$n",
                            color = if (isCurrent) Color.White else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Right: Category Tabs
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MACategoryTab.values().forEach { tab ->
                    val isSelected = activeCategory == tab
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) Color(0xFF059669) else Color(0xFF334155),
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onActiveCategoryChange(tab) }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // --- SEQUENCE STATS STRIP ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2541), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val redCount = remember(spins) { spins.count { MovingAverageCalculator.RED_NUMBERS.contains(it) } }
            val blackCount = remember(spins) { spins.count { MovingAverageCalculator.BLACK_NUMBERS.contains(it) } }
            val zeroCount = remember(spins) { spins.count { it == 0 || it == 37 } }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Total Spins: ${spins.size}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$redCount Red / $blackCount Black",
                    color = Color(0xFFFCA5A5),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Zeroes: $zeroCount",
                    color = Color(0xFF6EE7B7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (hoveredPointIndex != null) {
                    val spinNum = period + hoveredPointIndex!!
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF78350F), RoundedCornerShape(3.dp))
                            .clickable { hoveredPointIndex = null }
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Spin #$spinNum  [LIVE ✕]",
                            color = Color(0xFFFCD34D),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Recent spins badges (REVERSED: leftmost is the most recent spin)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Last: ", color = Color.Gray, fontSize = 9.sp)
                spins.takeLast(10).reversed().forEach { num ->
                    val bg = when {
                        num == 0 || num == 37 -> Color(0xFF047857)
                        MovingAverageCalculator.RED_NUMBERS.contains(num) -> Color(0xFFDC2626)
                        else -> Color(0xFF334155)
                    }
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .background(bg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (num == 37) "00" else "$num",
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // --- CHARTS AREA (DYNAMICALLY SCALED TO FILL AVAILABLE VERTICAL SPACE) ---
        if (spins.size < period) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1C2541), RoundedCornerShape(6.dp))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Need At Least $period Spins For ${maType.label} Analysis",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Currently have ${spins.size} spins recorded. Add more spins via Keypad or load a session.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                when (activeCategory) {
                    MACategoryTab.ALL -> {
                        // Row 1: Evens (Red/Black, High/Low, Odd/Even) - 50% height
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            MAChartCard(
                                category = result.redBlack,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.highLow,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.oddEven,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }

                        // Row 2: Dozens & Columns - 50% height
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            MAChartCard(
                                category = result.dozens,
                                theoreticalBaseline = 33.3f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.columns,
                                theoreticalBaseline = 33.3f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }

                    MACategoryTab.EVENS -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            MAChartCard(
                                category = result.redBlack,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.highLow,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.oddEven,
                                theoreticalBaseline = 50f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }

                    MACategoryTab.DOZENS_COLS -> {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            MAChartCard(
                                category = result.dozens,
                                theoreticalBaseline = 33.3f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            MAChartCard(
                                category = result.columns,
                                theoreticalBaseline = 33.3f,
                                spins = spins,
                                period = period,
                                hoveredPointIndex = hoveredPointIndex,
                                onHoverChanged = { hoveredPointIndex = it },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog allowing full manual entry and quick adjustments for Period (N).
 */
@Composable
fun PeriodPickerDialog(
    currentPeriod: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf(currentPeriod.toString()) }
    val parsedN = textValue.toIntOrNull()
    val isValid = parsedN != null && parsedN in 2..1000

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C2541),
        title = {
            Text(
                text = "Set Moving Average Period (N)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Enter the number of spins to calculate the moving average window (2 - 1000):",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )

                // Input field
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() } && input.length <= 4) {
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
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF334155),
                        errorBorderColor = Color(0xFFEF4444),
                        focusedContainerColor = Color(0xFF0B132B),
                        unfocusedContainerColor = Color(0xFF0B132B)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isValid && textValue.isNotEmpty()) {
                    Text(
                        text = "Value must be between 2 and 1000 spins",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp
                    )
                }

                // Quick increment / decrement steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(-10, -5, -1, 1, 5, 10).forEach { delta ->
                        Button(
                            onClick = {
                                val current = parsedN ?: currentPeriod
                                val newN = (current + delta).coerceIn(2, 1000)
                                textValue = newN.toString()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(28.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Standard Presets row: 20, 50, 100, 150, 200
                Text(
                    text = "Quick Presets:",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(20, 50, 100, 150, 200).forEach { preset ->
                        val isSelected = parsedN == preset
                        Button(
                            onClick = { textValue = preset.toString() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) Color(0xFF059669) else Color(0xFF1E293B)
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1f).height(30.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$preset",
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontSize = 11.sp,
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
                    if (isValid && parsedN != null) {
                        onConfirm(parsedN)
                    }
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF059669),
                    disabledContainerColor = Color(0xFF334155)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("Apply (N = ${parsedN ?: currentPeriod})", color = Color.White, fontWeight = FontWeight.Bold)
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

@Composable
fun MAChartCard(
    category: MovingAverageCalculator.CategoryResult,
    theoreticalBaseline: Float? = null,
    spins: List<Int> = emptyList(),
    period: Int = 10,
    hoveredPointIndex: Int? = null,
    onHoverChanged: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Dynamically calculate min and max across all series to auto-fit the vertical canvas
    val allValues = remember(category) { category.lines.flatMap { it.values } }
    val dataMin = remember(allValues) { allValues.minOrNull() ?: 0f }
    val dataMax = remember(allValues) { allValues.maxOrNull() ?: 100f }

    // Provide dynamic padded bounds so the graph utilizes the entire card height
    val span = (dataMax - dataMin).coerceAtLeast(15f)
    val rawMinY = (dataMin - span * 0.1f).coerceAtLeast(0f)
    val rawMaxY = (dataMax + span * 0.1f).coerceAtMost(100f)
    val minY = floor(rawMinY / 5f) * 5f
    val maxY = ceil(rawMaxY / 5f) * 5f
    val effectiveSpan = (maxY - minY).coerceAtLeast(10f)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541)),
        shape = RoundedCornerShape(5.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Color(0xFF334155)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // Header: Title on left, Legend on right (Single Row with strict no-wrap)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = category.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Legend items
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    category.lines.forEach { line ->
                        val displayVal = if (hoveredPointIndex != null && hoveredPointIndex in line.values.indices) {
                            line.values[hoveredPointIndex]
                        } else {
                            line.values.lastOrNull() ?: 0f
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color(line.colorHex), CircleShape)
                            )
                            Text(
                                text = "${line.label} ${"%.1f".format(displayVal)}%",
                                color = Color(line.colorHex),
                                fontSize = if (hoveredPointIndex != null) 10.5.sp else 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(1.dp))

            val numPoints = remember(category) { category.lines.firstOrNull()?.values?.size ?: 0 }
            val padL = 10f
            val padR = 6f
            val padT = 3f
            val padB = 3f

            // Dynamic Chart Canvas Box with touch and scrub detection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(numPoints) {
                        if (numPoints <= 1 || onHoverChanged == null) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val chartW = size.width - padL - padR
                            if (chartW > 0f) {
                                val normX = ((down.position.x - padL) / chartW).coerceIn(0f, 1f)
                                val ptIdx = (normX * (numPoints - 1)).roundToInt().coerceIn(0, numPoints - 1)
                                onHoverChanged(ptIdx)
                            }
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull()
                                if (change != null && change.pressed) {
                                    val chartW = size.width - padL - padR
                                    if (chartW > 0f) {
                                        val normX = ((change.position.x - padL) / chartW).coerceIn(0f, 1f)
                                        val ptIdx = (normX * (numPoints - 1)).roundToInt().coerceIn(0, numPoints - 1)
                                        onHoverChanged(ptIdx)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                            // Keep the crosshair and spin position even after the finger is released
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    if (w <= 0f || h <= 0f) return@Canvas

                    val chartW = w - padL - padR
                    val chartH = h - padT - padB

                    // Grid lines: Top (maxY), Middle ((minY+maxY)/2), Bottom (minY)
                    val gridFractions = listOf(0f, 0.5f, 1.0f)
                    gridFractions.forEach { fraction ->
                        val y = padT + (1f - fraction) * chartH
                        drawLine(
                            color = if (fraction == 0.5f) Color(0xFF475569) else Color(0xFF334155).copy(alpha = 0.35f),
                            start = Offset(padL, y),
                            end = Offset(w - padR, y),
                            strokeWidth = if (fraction == 0.5f) 1.0f else 0.75f
                        )
                    }

                    // Optional baseline reference (e.g. 50% or 33.3%)
                    if (theoreticalBaseline != null && theoreticalBaseline in minY..maxY) {
                        val baseNormY = (theoreticalBaseline - minY) / effectiveSpan
                        val baseY = padT + (1f - baseNormY) * chartH
                        drawLine(
                            color = Color(0xFF059669).copy(alpha = 0.5f),
                            start = Offset(padL, baseY),
                            end = Offset(w - padR, baseY),
                            strokeWidth = 1.0f
                        )
                    }

                    // Draw Data Lines (Dynamically scaled across width and height)
                    category.lines.forEach { line ->
                        val values = line.values
                        if (values.size >= 2) {
                            val path = Path()

                            values.forEachIndexed { idx, v ->
                                val normX = idx.toFloat() / (numPoints - 1).toFloat()
                                val normY = (v.coerceIn(minY, maxY) - minY) / effectiveSpan

                                val x = padL + normX * chartW
                                val y = padT + (1f - normY) * chartH

                                if (idx == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = Color(line.colorHex),
                                style = Stroke(width = 1.8f)
                            )

                            // End point dot on latest value
                            val lastIdx = values.size - 1
                            val lastVal = values[lastIdx]
                            val lastX = padL + chartW
                            val lastNormY = (lastVal.coerceIn(minY, maxY) - minY) / effectiveSpan
                            val lastY = padT + (1f - lastNormY) * chartH

                            drawCircle(
                                color = Color(line.colorHex),
                                radius = 2.5f,
                                center = Offset(lastX, lastY)
                            )
                        }
                    }

                    // Synchronized vertical line and spin indicator on touch/hover
                    if (hoveredPointIndex != null && hoveredPointIndex in 0 until numPoints && numPoints > 1) {
                        val hx = padL + (hoveredPointIndex.toFloat() / (numPoints - 1).toFloat()) * chartW

                        // 1. Draw vertical crosshair guideline across the chart
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                            start = Offset(hx, padT),
                            end = Offset(hx, h - padB),
                            strokeWidth = 1.5f
                        )

                        // 2. Data intersection dots for each curve
                        category.lines.forEach { line ->
                            if (hoveredPointIndex in line.values.indices) {
                                val v = line.values[hoveredPointIndex]
                                val normY = (v.coerceIn(minY, maxY) - minY) / effectiveSpan
                                val hy = padT + (1f - normY) * chartH

                                drawCircle(
                                    color = Color(line.colorHex),
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(hx, hy)
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = 1.5.dp.toPx(),
                                    center = Offset(hx, hy)
                                )
                            }
                        }

                        // 3. Spin number badge at top of line
                        val spinNum = period + hoveredPointIndex
                        val spunNum = spins.getOrNull(spinNum - 1)
                        val spinLabel = if (spunNum != null) {
                            val numStr = if (spunNum == 37) "00" else "$spunNum"
                            "Spin #$spinNum ($numStr)"
                        } else {
                            "Spin #$spinNum"
                        }

                        val tagPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 42f // Twice as large as original 21f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val textW = tagPaint.measureText(spinLabel)
                        val badgeW = textW + 28f
                        val badgeH = 50f
                        val badgeCenterX = hx.coerceIn(padL + badgeW / 2f, w - padR - badgeW / 2f)
                        val badgeTop = padT + 2f

                        drawRoundRect(
                            color = Color(0xFF0F172A).copy(alpha = 0.95f),
                            topLeft = Offset(badgeCenterX - badgeW / 2f, badgeTop),
                            size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFF38BDF8),
                            topLeft = Offset(badgeCenterX - badgeW / 2f, badgeTop),
                            size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.5f)
                        )
                        val fontMetrics = tagPaint.fontMetrics
                        val textY = badgeTop + (badgeH - fontMetrics.bottom - fontMetrics.top) / 2f
                        drawContext.canvas.nativeCanvas.drawText(
                            spinLabel,
                            badgeCenterX,
                            textY,
                            tagPaint
                        )
                    }
                }
            }
        }
    }
}
