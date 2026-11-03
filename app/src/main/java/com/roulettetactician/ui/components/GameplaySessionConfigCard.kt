package com.roulettetactician.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.data.GameplaySessionEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameplaySessionConfigCard(
    sessions: List<GameplaySessionEntity>,
    activeSession: GameplaySessionEntity?,
    availableRouletteSessions: List<String> = emptyList(),
    onSelectSession: (GameplaySessionEntity) -> Unit,
    onCreateSession: (name: String, startBankroll: Double) -> Unit,
    onRenameSession: (GameplaySessionEntity, String) -> Unit,
    onReassignSession: (GameplaySessionEntity, String) -> Unit = { _, _ -> },
    onDeleteSession: (GameplaySessionEntity) -> Unit,
    onUpdateConfig: (bankroll: Double, inBase: Double, inMin: Double, outBase: Double, outMin: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var sessionToRename by remember { mutableStateOf<GameplaySessionEntity?>(null) }
    var renameSessionName by remember { mutableStateOf("") }

    var showReassignDialog by remember { mutableStateOf(false) }
    var sessionToReassign by remember { mutableStateOf<GameplaySessionEntity?>(null) }
    var reassignTargetSessionName by remember { mutableStateOf("") }

    var newSessionName by remember { mutableStateOf("") }
    var newSessionBankroll by remember { mutableStateOf("1000") }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Auto-clear focus when keyboard closes
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    // Config Input Fields
    var bankrollInput by remember { mutableStateOf("1000") }
    var inBaseInput by remember { mutableStateOf("1") }
    var inMinInput by remember { mutableStateOf("1") }
    var outBaseInput by remember { mutableStateOf("5") }
    var outMinInput by remember { mutableStateOf("5") }

    // Sync state when activeSession changes
    LaunchedEffect(activeSession?.id) {
        activeSession?.let { session ->
            bankrollInput = if (session.startingBankroll % 1.0 == 0.0) session.startingBankroll.toInt().toString() else session.startingBankroll.toString()
            inBaseInput = if (session.insideBaseUnit % 1.0 == 0.0) session.insideBaseUnit.toInt().toString() else session.insideBaseUnit.toString()
            inMinInput = if (session.insideMinUnit % 1.0 == 0.0) session.insideMinUnit.toInt().toString() else session.insideMinUnit.toString()
            outBaseInput = if (session.outsideBaseUnit % 1.0 == 0.0) session.outsideBaseUnit.toInt().toString() else session.outsideBaseUnit.toString()
            outMinInput = if (session.outsideMinUnit % 1.0 == 0.0) session.outsideMinUnit.toInt().toString() else session.outsideMinUnit.toString()
        }
    }

    if (showNewSessionDialog) {
        AlertDialog(
            onDismissRequest = { showNewSessionDialog = false },
            title = { Text("New Game Play Session", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Session Name:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = newSessionName,
                            onValueChange = { newSessionName = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (newSessionName.isEmpty()) Text("e.g. Oscar's Grind Run", color = Color.DarkGray, fontSize = 14.sp)
                                inner()
                            }
                        )
                    }

                    Text("Starting Bankroll ($):", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = newSessionBankroll,
                            onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) newSessionBankroll = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newSessionName.trim().ifEmpty { "Session ${sessions.size + 1}" }
                        val bankroll = newSessionBankroll.toDoubleOrNull() ?: 1000.0
                        onCreateSession(name, bankroll)
                        showNewSessionDialog = false
                        newSessionName = ""
                        newSessionBankroll = "1000"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Create", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showDeleteConfirmDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Gameplay Session?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Are you sure you want to delete session '${activeSession.name}'? All recorded bets and outcomes for this session will be removed.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSession(activeSession)
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showRenameDialog && sessionToRename != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                sessionToRename = null
            },
            title = {
                Text(
                    "Rename Gameplay Session",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter new name for '${sessionToRename?.name}':",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = renameSessionName,
                            onValueChange = { renameSessionName = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = renameSessionName.trim()
                        if (clean.isNotEmpty() && sessionToRename != null) {
                            onRenameSession(sessionToRename!!, clean)
                        }
                        showRenameDialog = false
                        sessionToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRenameDialog = false
                    sessionToRename = null
                }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showReassignDialog && sessionToReassign != null) {
        val currentParent = sessionToReassign?.rouletteSessionName ?: "New Session..."
        val candidates = remember(availableRouletteSessions, currentParent) {
            (listOf("New Session...", "My Wheel") + availableRouletteSessions).distinct().filter { it != currentParent }
        }

        AlertDialog(
            onDismissRequest = {
                showReassignDialog = false
                sessionToReassign = null
                reassignTargetSessionName = ""
            },
            title = {
                Text(
                    "Reassign Gameplay Session",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Move '${sessionToReassign?.name}' to a different spin session.\nCurrently linked to: '$currentParent'",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )

                    if (candidates.isNotEmpty()) {
                        Text(
                            text = "Select destination spin session:",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 130.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            candidates.forEach { targetName ->
                                val isSelected = reassignTargetSessionName == targetName
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) Color(0xFF0284C7).copy(alpha = 0.35f) else Color(0xFF0F172A),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable { reassignTargetSessionName = targetName }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DriveFileMove,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF38BDF8) else Color.Gray,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = targetName,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Text("Or enter destination spin session name:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = reassignTargetSessionName,
                            onValueChange = { reassignTargetSessionName = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            singleLine = true,
                            decorationBox = { inner ->
                                if (reassignTargetSessionName.isEmpty()) {
                                    Text("e.g. Session 2", color = Color.DarkGray, fontSize = 12.sp)
                                }
                                inner()
                            }
                        )
                    }

                    Text(
                        text = "Once reassigned, this gameplay session will immediately move to the destination spin session and will not appear in this session.",
                        color = Color(0xFFFBBF24),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                val targetClean = reassignTargetSessionName.trim()
                val canReassign = targetClean.isNotEmpty() && targetClean != currentParent
                Button(
                    onClick = {
                        if (canReassign && sessionToReassign != null) {
                            onReassignSession(sessionToReassign!!, targetClean)
                        }
                        showReassignDialog = false
                        sessionToReassign = null
                        reassignTargetSessionName = ""
                    },
                    enabled = canReassign,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Reassign", color = if (canReassign) Color.White else Color.Gray, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showReassignDialog = false
                    sessionToReassign = null
                    reassignTargetSessionName = ""
                }) {
                    Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // ================= SINGLE COMPACT BAR STRIP =================
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Gameplay Session Dropdown
            Text(
                "Game Play Session:",
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Box {
                Row(
                    modifier = Modifier
                        .height(26.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                        .clickable { showDropdown = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeSession?.name ?: "Game 1",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    sessions.forEach { sess ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        onSelectSession(sess)
                                        showDropdown = false
                                    },
                                    onLongClick = {
                                        sessionToRename = sess
                                        renameSessionName = sess.name
                                        showRenameDialog = true
                                        showDropdown = false
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                if (sess.id == activeSession?.id) {
                                    Box(
                                        Modifier
                                            .size(5.dp)
                                            .background(Color(0xFFFBBF24), CircleShape)
                                    )
                                }
                                Text(
                                    sess.name,
                                    color = if (sess.id == activeSession?.id) Color(0xFFFBBF24) else Color.White,
                                    fontWeight = if (sess.id == activeSession?.id) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            IconButton(
                                onClick = {
                                    sessionToReassign = sess
                                    reassignTargetSessionName = ""
                                    showReassignDialog = true
                                    showDropdown = false
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DriveFileMove,
                                    contentDescription = "Reassign Session",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(12.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    sessionToRename = sess
                                    renameSessionName = sess.name
                                    showRenameDialog = true
                                    showDropdown = false
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = "Rename Session",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Pencil Edit Active Session Button
            if (activeSession != null) {
                IconButton(
                    onClick = {
                        sessionToRename = activeSession
                        renameSessionName = activeSession.name
                        showRenameDialog = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename Active Session",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Quick Reassign Active Session Button
            if (activeSession != null) {
                IconButton(
                    onClick = {
                        sessionToReassign = activeSession
                        reassignTargetSessionName = ""
                        showReassignDialog = true
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.DriveFileMove,
                        contentDescription = "Reassign Active Session",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // + New Session Button
            Button(
                onClick = { showNewSessionDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                modifier = Modifier.height(26.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.White)
                Spacer(Modifier.width(2.dp))
                Text("New", fontSize = 10.5.sp, color = Color.White)
            }

            // Delete Session Button
            if (sessions.size > 1 && activeSession != null) {
                IconButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete Session", tint = Color(0xFFEF4444), modifier = Modifier.size(14.dp))
                }
            }

            // Divider
            Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF334155)))

            // Starting Bankroll
            CompactConfigField(
                label = "Start:",
                prefix = "$",
                value = bankrollInput,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) bankrollInput = it },
                inputWidth = 48.dp
            )

            // Inside Base Unit
            CompactConfigField(
                label = "In Base:",
                prefix = "$",
                value = inBaseInput,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) inBaseInput = it },
                inputWidth = 26.dp
            )

            // Inside Min Unit
            CompactConfigField(
                label = "In Min:",
                prefix = "$",
                value = inMinInput,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) inMinInput = it },
                inputWidth = 26.dp
            )

            // Outside Base Unit
            CompactConfigField(
                label = "Out Base:",
                prefix = "$",
                value = outBaseInput,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) outBaseInput = it },
                inputWidth = 26.dp
            )

            // Outside Min Unit
            CompactConfigField(
                label = "Out Min:",
                prefix = "$",
                value = outMinInput,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) outMinInput = it },
                inputWidth = 26.dp
            )

            Spacer(Modifier.width(2.dp))

            // Apply Settings Button
            Button(
                onClick = {
                    val bankroll = bankrollInput.toDoubleOrNull() ?: 1000.0
                    val inBase = inBaseInput.toDoubleOrNull() ?: 1.0
                    val inMin = inMinInput.toDoubleOrNull() ?: 1.0
                    val outBase = outBaseInput.toDoubleOrNull() ?: 5.0
                    val outMin = outMinInput.toDoubleOrNull() ?: 5.0
                    onUpdateConfig(bankroll, inBase, inMin, outBase, outMin)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                modifier = Modifier.height(26.dp),
                shape = RoundedCornerShape(4.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text("Apply Settings", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun CompactConfigField(
    label: String,
    prefix: String = "$",
    value: String,
    onValueChange: (String) -> Unit,
    inputWidth: androidx.compose.ui.unit.Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Box(
            modifier = Modifier
                .width(inputWidth)
                .height(24.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prefix, color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(1.dp))
                val fieldFocusManager = LocalFocusManager.current
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Start
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { fieldFocusManager.clearFocus(force = true) }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
