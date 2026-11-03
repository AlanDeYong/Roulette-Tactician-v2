package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// DraggableDialog is used instead of Dialog





@Composable
fun HotColdEditor(
    type: String, // "HOT" or "COLD"
    numbers: List<Int?>,
    onSave: (List<Int?>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentNumbers by remember { mutableStateOf(numbers) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var inputBuffer by remember { mutableStateOf("") }

    // Colors
    val themeColor = if (type == "HOT") Color(0xFFEAB308) else Color(0xFF3B82F6)
    
    fun updateNumber(num: Int?) {
        val newList = currentNumbers.toMutableList()
        if (selectedIndex in newList.indices) {
            newList[selectedIndex] = num
        }
        currentNumbers = newList
        inputBuffer = "" 
        // Auto-advance
        if (selectedIndex < currentNumbers.size - 1) {
             selectedIndex++
        }
    }

    DraggableDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp) // Compact for landscape
                .wrapContentHeight()
                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            // --- HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit $type Numbers", color = themeColor, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Button(
                    onClick = { onSave(currentNumbers); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)), // Green Done
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Done", color = Color.White, fontSize = 12.sp)
                }
            }
            
            Spacer(Modifier.height(8.dp))

            // --- SLOTS (Horizontal) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                currentNumbers.forEachIndexed { index, num ->
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Color(0xFF0F172A),
                                RoundedCornerShape(8.dp) // Square with rounded corners
                            )
                            .border(
                                2.dp,
                                if (index == selectedIndex) Color.White else Color(0xFF334155),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedIndex = index; inputBuffer = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = num?.toString() ?: "-",
                                color = if (num != null) Color.White else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                             // Slot index indicator
                            Text(
                                text = "#${index + 1}",
                                color = Color.DarkGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // --- KEYPAD GRID (4 columns: 1-3 + Save, 4-6 + 0, 7-9 + Buffer) ---
            LazyVerticalGrid(
                 columns = GridCells.Fixed(4),
                 verticalArrangement = Arrangement.spacedBy(6.dp),
                 horizontalArrangement = Arrangement.spacedBy(6.dp),
                 modifier = Modifier.fillMaxWidth().height(140.dp) // Shorter height, wider layout
            ) {
                // Row 1: 1, 2, 3, Buffer
                items((1..3).toList()) { num ->
                    KeypadButton(text = num.toString(), color = Color(0xFF334155)) {
                         if (inputBuffer.length < 2) inputBuffer += num
                    }
                }
                item { 
                    // Buffer Display
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = inputBuffer.ifEmpty { "--" }, 
                            color = if (inputBuffer.isNotEmpty()) Color.White else Color.Gray, 
                            fontSize = 18.sp, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Row 2: 4, 5, 6, 0
                items((4..6).toList()) { num ->
                    KeypadButton(text = num.toString(), color = Color(0xFF334155)) {
                         if (inputBuffer.length < 2) inputBuffer += num
                    }
                }
                item { 
                    KeypadButton(text = "0", color = Color(0xFF334155)) {
                         if (inputBuffer.length < 2) inputBuffer += "0"
                    } 
                }
                
                // Row 3: 7, 8, 9, SET
                items((7..9).toList()) { num ->
                    KeypadButton(text = num.toString(), color = Color(0xFF334155)) {
                         if (inputBuffer.length < 2) inputBuffer += num
                    }
                }
                item { 
                    // SET Button (Green)
                    Box(
                        modifier = Modifier
                            .size(44.dp) 
                            .background(Color(0xFF15803D), RoundedCornerShape(6.dp))
                            .clickable {
                                val newVal = inputBuffer.toIntOrNull()
                                if (newVal != null && newVal <= 36) {
                                    updateNumber(newVal)
                                } else if (inputBuffer == "") {
                                    updateNumber(null)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SET", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

