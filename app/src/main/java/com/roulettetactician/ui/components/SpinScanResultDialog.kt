package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.roulettetactician.ui.SpinScanState

/**
 * Dialog displaying the results of the Gemini Vision spin log scan.
 * Shows:
 *  - Overlapping spins (already in app history) dimmed out
 *  - New spins to be appended highlighted with a badge
 *  - Dropped unreadable entries / glare warnings
 *  - Confirm button to commit to Room
 */
@Composable
fun SpinScanResultDialog(
    state: SpinScanState,
    onConfirm: () -> Unit,
    onRetake: () -> Unit,
    onOpenApiKeySettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    if (state is SpinScanState.Idle) return

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            when (state) {
                is SpinScanState.Processing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Analyzing screen with Gemini Vision...",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            "Isolating spin sequence & filtering out bets/stats",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }

                is SpinScanState.Error -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFEF4444))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Scan Unsuccessful",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            state.message,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (onOpenApiKeySettings != null) {
                                Button(
                                    onClick = onOpenApiKeySettings,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier.weight(1.1f),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text("Set API Key", color = Color.White, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("Close", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                            Button(
                                onClick = onRetake,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier.weight(1.2f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text("Retake Photo", color = Color.White, fontSize = 12.sp, maxLines = 1)
                            }
                        }
                    }
                }

                is SpinScanState.Review -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Scan Review (${state.layout.replace('_', ' ')})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            "Chronological order: Oldest → Newest",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )

                        Spacer(Modifier.height(12.dp))

                        // Spin row visualization
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            itemsIndexed(state.scannedOldestFirst) { idx, num ->
                                val isOverlap = idx < state.overlap
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            if (isOverlap) Color.DarkGray.copy(alpha = 0.5f) else Color.Transparent
                                        )
                                ) {
                                    SpinHistoryItem(
                                        number = num,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    if (isOverlap) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "Existing",
                                                tint = Color(0xFF22C55E),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Stats summary
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "• ${state.overlap} spins already in history (skipped)",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                "• ${state.newSpins.size} new spins will be appended",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            if (state.droppedOlder > 0) {
                                Text(
                                    "• ${state.droppedOlder} older spins discarded due to unreadable glare cell",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp
                                )
                            }
                            if (state.possibleGap) {
                                Text(
                                    "• Notice: No overlap found with existing history. Spins between sessions may be skipped.",
                                    color = Color(0xFFF97316),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = onRetake,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Retake", color = Color.White, fontSize = 12.sp)
                            }

                            Button(
                                onClick = onConfirm,
                                enabled = state.newSpins.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Append (${state.newSpins.size})", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
