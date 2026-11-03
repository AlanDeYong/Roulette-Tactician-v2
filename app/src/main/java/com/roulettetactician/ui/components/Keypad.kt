package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// DraggableDialog is used instead of Dialog

@Composable
fun Keypad(
    tableType: com.roulettetactician.ui.model.TableType = com.roulettetactician.ui.model.TableType.EU,
    onNumberSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var inputBuffer by remember { mutableStateOf("") }

    DraggableDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(180.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header / Display with Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enter Spin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(32.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inputBuffer.ifEmpty { "--" },
                        color = if(inputBuffer.isNotEmpty()) Color.White else Color.Gray,
                        fontSize = 20.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                // Close X button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF7F1D1D), RoundedCornerShape(4.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (tableType == com.roulettetactician.ui.model.TableType.US) 4 else 3),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items((1..9).toList()) { num ->
                    KeypadButton(text = num.toString()) {
                        if (inputBuffer.length < 2) inputBuffer += num
                    }
                }
                
                // Row 4
                item { 
                    KeypadButton(text = "DEL", color = Color(0xFF7F1D1D)) {
                        if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1)
                    } 
                }
                item { 
                    KeypadButton(text = "0") {
                        if (inputBuffer.length < 2) inputBuffer += "0" 
                    } 
                }
                if (tableType == com.roulettetactician.ui.model.TableType.US) {
                    item {
                        KeypadButton(text = "00") {
                            onNumberSelected(37)
                            inputBuffer = ""
                        }
                    }
                }
                item { 
                    KeypadButton(text = "OK", color = Color(0xFF16A34A)) {
                        val num = inputBuffer.toIntOrNull()
                        if (num != null && num in 0..36) {
                            onNumberSelected(num)
                            inputBuffer = "" // Clear for next entry
                        }
                    } 
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    text: String,
    color: Color = Color(0xFF334155),
    modifier: Modifier = Modifier.size(44.dp),
    fontSize: androidx.compose.ui.unit.TextUnit = 18.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EditKeypad(
    currentNumber: Int,
    spinLabel: String = "Edit Spin",
    tableType: com.roulettetactician.ui.model.TableType = com.roulettetactician.ui.model.TableType.EU,
    onNumberSelected: (Int) -> Unit,
    onInsertBefore: (Int) -> Unit,
    onInsertAfter: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val displayCurrent = if (currentNumber == 37) "00" else currentNumber.toString()
    var inputBuffer by remember { mutableStateOf(displayCurrent) }

    val parsedNum = if (inputBuffer == "00" && tableType == com.roulettetactician.ui.model.TableType.US) 37 else inputBuffer.toIntOrNull()
    val isValidInput = parsedNum != null && (parsedNum in 0..36 || (tableType == com.roulettetactician.ui.model.TableType.US && parsedNum == 37))

    DraggableDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(200.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(14.dp))
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header / Display with Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(spinLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Current: $displayCurrent", color = Color(0xFF94A3B8), fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(28.dp)
                        .background(Color.Black, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = inputBuffer.ifEmpty { "--" },
                        color = if (isValidInput) Color.White else Color.Gray,
                        fontSize = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                // Close X button
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(Color(0xFF7F1D1D), RoundedCornerShape(4.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("X", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            // Compact Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (tableType == com.roulettetactician.ui.model.TableType.US) 4 else 3),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items((1..9).toList()) { num ->
                    KeypadButton(
                        text = num.toString(),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        fontSize = 15.sp
                    ) {
                        if (inputBuffer.length < 2) inputBuffer += num
                    }
                }
                
                // Row 4
                item { 
                    KeypadButton(
                        text = "DEL",
                        color = Color(0xFF7F1D1D),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        fontSize = 13.sp
                    ) {
                        if (inputBuffer.isNotEmpty()) inputBuffer = inputBuffer.dropLast(1)
                    } 
                }
                item { 
                    KeypadButton(
                        text = "0",
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        fontSize = 15.sp
                    ) {
                        if (inputBuffer.length < 2) inputBuffer += "0" 
                    } 
                }
                if (tableType == com.roulettetactician.ui.model.TableType.US) {
                    item {
                        KeypadButton(
                            text = "00",
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            fontSize = 14.sp
                        ) {
                            inputBuffer = "00"
                        }
                    }
                }
                item { 
                    KeypadButton(
                        text = "OK",
                        color = if (isValidInput) Color(0xFF16A34A) else Color(0xFF334155),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        fontSize = 14.sp
                    ) {
                        val num = parsedNum
                        if (isValidInput && num != null) {
                            onNumberSelected(num)
                        }
                    } 
                }
            }
            
            Spacer(Modifier.height(5.dp))
            
            // Insert Before and Insert After buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(
                            if (isValidInput) Color(0xFF2563EB) else Color(0xFF334155),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable(enabled = isValidInput) {
                            val num = parsedNum
                            if (isValidInput && num != null) {
                                onInsertBefore(num)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Insert Before",
                        color = if (isValidInput) Color.White else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .background(
                            if (isValidInput) Color(0xFF0D9488) else Color(0xFF334155),
                            RoundedCornerShape(5.dp)
                        )
                        .clickable(enabled = isValidInput) {
                            val num = parsedNum
                            if (isValidInput && num != null) {
                                onInsertAfter(num)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Insert After",
                        color = if (isValidInput) Color.White else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Delete Spin button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(Color(0xFFDC2626), RoundedCornerShape(5.dp))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Delete This Spin",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}
