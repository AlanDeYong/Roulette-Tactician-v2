package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.roulettetactician.logic.BetTrendCalculator
import com.roulettetactician.logic.GameplaySpinRecord
import com.roulettetactician.ui.model.PlacedChip
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resizable Bet Ledger Card for Game Play Sessions on the Felt Tab.
 * Lists each recorded spin, winning number, bets placed summary, stake, payout, net profit/loss, and ending bankroll.
 * Tapping any row selects that spin in the scrubber.
 */
@Composable
fun GameplayLedgerCard(
    records: List<GameplaySpinRecord>,
    scrubberSpinIndex: Int? = null,
    onSelectSpin: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    initialHeight: Dp = 200.dp
) {
    var ledgerHeight by remember { mutableStateOf(initialHeight) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ledgerHeight)
            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            // 1. Header with Title and Height Resizer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Game Play Bet Ledger",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "(${records.size} Spins)",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(3.dp))
                            .clickable {
                                if (ledgerHeight - 40.dp >= 120.dp) ledgerHeight -= 40.dp
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(3.dp))
                            .clickable {
                                if (ledgerHeight + 40.dp <= 500.dp) ledgerHeight += 40.dp
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. Table Column Headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF182234))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spin", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                Text("Hit", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                Text("Bets Placed", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("Stake", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                Text("Payout", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                Text("P/L", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                Text("Result", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp), textAlign = TextAlign.Center)
                Text("Bank", color = Color.Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(46.dp), textAlign = TextAlign.End)
            }

            // 3. Table Rows
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No spins recorded yet in this game play session.",
                        color = Color(0xFF64748B),
                        fontSize = 9.5.sp
                    )
                }
            } else {
                val displayRecords = remember(records) { records.asReversed() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    itemsIndexed(displayRecords, key = { index, record -> "${record.spinIndex}_$index" }) { _, record ->
                        val isHighlighted = record.spinIndex == scrubberSpinIndex
                        GameplayLedgerRow(
                            record = record,
                            isHighlighted = isHighlighted,
                            onClick = { onSelectSpin?.invoke(record.spinIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameplayLedgerRow(
    record: GameplaySpinRecord,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val isGreen = record.spunNumber == 0 || record.spunNumber == 37
    val isRed = !isGreen && BetTrendCalculator.RED_NUMS.contains(record.spunNumber)
    val numBg = if (isGreen) Color(0xFF15803D) else if (isRed) Color(0xFFDC2626) else Color(0xFF1E293B)

    val isWin = record.netProfit > 0
    val isLoss = record.netProfit < 0
    val outcomeLabel = if (isWin) "WIN" else if (isLoss) "LOSS" else "PUSH"
    val outcomeBg = if (isWin) Color(0xFF065F46) else if (isLoss) Color(0xFF991B1B) else Color(0xFF334155)
    val outcomeTxt = if (isWin) Color(0xFF34D399) else if (isLoss) Color(0xFFF87171) else Color(0xFF94A3B8)
    val netTxt = if (isWin) Color(0xFF34D399) else if (isLoss) Color(0xFFF87171) else Color.Gray

    val rowBg = if (isHighlighted) Color(0xFF78350F).copy(alpha = 0.45f) else Color.Transparent

    val summaryText = formatBetsSummary(record.chips)
    var showTooltip by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Spin Index
        Text(
            "#${record.spinIndex}",
            color = if (isHighlighted) Color(0xFFFCD34D) else Color(0xFF94A3B8),
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            fontSize = 8.5.sp,
            modifier = Modifier.width(30.dp)
        )

        // Spun Number Pill
        Box(
            modifier = Modifier
                .size(17.dp)
                .background(numBg, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (record.spunNumber == 37) "00" else "${record.spunNumber}",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(8.dp))

        // Bets Placed Summary with Press-and-Hold Tooltip
        Box(
            modifier = Modifier
                .weight(1f)
                .pointerInput(record) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        showTooltip = true
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        showTooltip = false
                    }
                }
        ) {
            Text(
                summaryText,
                color = Color(0xFFE2E8F0),
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            if (showTooltip) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, -90),
                    properties = PopupProperties(focusable = false)
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(min = 180.dp, max = 320.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Spin #${record.spinIndex} Bets",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Stake: $${record.totalStake.roundToInt()} | Payout: $${record.totalPayout.roundToInt()}",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = formatDetailedBetsList(record.chips),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(4.dp))

        // Stake
        Text(
            "$${record.totalStake.roundToInt()}",
            color = Color(0xFFFCD34D),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )

        // Payout
        Text(
            "$${record.totalPayout.roundToInt()}",
            color = Color(0xFFE2E8F0),
            fontSize = 8.5.sp,
            modifier = Modifier.width(38.dp),
            textAlign = TextAlign.End
        )

        // Net Profit/Loss
        val netStr = "${if (record.netProfit >= 0) "+$" else "-$"}${abs(record.netProfit.roundToInt())}"
        Text(
            netStr,
            color = netTxt,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End
        )

        Spacer(Modifier.width(4.dp))

        // Outcome Badge
        Box(
            modifier = Modifier
                .width(38.dp)
                .background(outcomeBg, RoundedCornerShape(2.dp))
                .padding(vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                outcomeLabel,
                color = outcomeTxt,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Bankroll
        Text(
            "$${record.bankroll.roundToInt()}",
            color = Color.White,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Formats a list of placed chips into a clean, human-readable summary string.
 */
fun formatBetsSummary(chips: List<PlacedChip>): String {
    if (chips.isEmpty()) return "No bets placed"
    if (chips.size <= 2) {
        return chips.joinToString(", ") { "${formatBetIdFriendly(it.betId)} ($${it.value})" }
    }
    val firstTwo = chips.take(2).joinToString(", ") { "${formatBetIdFriendly(it.betId)} ($${it.value})" }
    return "${chips.size} Bets: $firstTwo, +${chips.size - 2} more"
}

/**
 * Detailed multi-line string for tooltips.
 */
fun formatDetailedBetsList(chips: List<PlacedChip>): String {
    if (chips.isEmpty()) return "None"
    return chips.joinToString("\n") { "• ${formatBetIdFriendly(it.betId)}: $${it.value}" }
}

/**
 * Formats a betId into a user-friendly roulette bet name.
 */
fun formatBetIdFriendly(betId: String): String {
    return when {
        betId.startsWith("num-") -> "Straight ${betId.removePrefix("num-")}"
        betId.startsWith("split-") -> "Split ${betId.removePrefix("split-").replace("-", "/")}"
        betId.startsWith("street-") -> {
            val num = betId.removePrefix("street-").toIntOrNull() ?: 1
            "Street $num-${num + 2}"
        }
        betId.startsWith("corn-") -> "Corner ${betId.removePrefix("corn-").replace("-", "/")}"
        betId.startsWith("corner-") -> {
            if (betId == "corner-0-1-2-3") "Basket 0-3"
            else "Corner ${betId.removePrefix("corner-").replace("-", "/")}"
        }
        betId.startsWith("line-") -> "Line ${betId.removePrefix("line-").replace("-", "/")}"
        betId == "doz-1" -> "1st 12"
        betId == "doz-2" -> "2nd 12"
        betId == "doz-3" -> "3rd 12"
        betId == "2to1-top" -> "Col 3"
        betId == "2to1-mid" -> "Col 2"
        betId == "2to1-bot" -> "Col 1"
        betId == "red" -> "Red"
        betId == "black" -> "Black"
        betId == "even" -> "Even"
        betId == "odd" -> "Odd"
        betId == "low" -> "1-18"
        betId == "high" -> "19-36"
        else -> betId
    }
}
