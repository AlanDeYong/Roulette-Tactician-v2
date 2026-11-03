package com.roulettetactician.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.roulettetactician.data.SessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SessionManagerDialog(
    currentName: String,
    sessions: List<SessionEntity>,
    gameplaySessionCounts: Map<String, Int> = emptyMap(),
    onSave: () -> Unit,
    onLoad: (SessionEntity) -> Unit,
    onDelete: (SessionEntity) -> Unit,
    onImportFile: (String, String, (Int) -> Unit) -> Unit,
    onGetExportContent: suspend (SessionEntity?) -> String,
    backupFolderUri: String? = null,
    onSetBackupFolder: (Uri) -> Unit = {},
    onBackupToFolder: suspend (Uri) -> Result<String> = { Result.failure(Exception()) },
    onGetBackupJson: suspend () -> String = { "" },
    onRestoreBackup: suspend (String) -> Result<Pair<Int, Int>> = { Result.failure(Exception()) },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    // Auto-clear focus when keyboard is closed
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var sessionTargetForExport by remember { mutableStateOf<SessionEntity?>(null) }
    var sessionPendingDelete by remember { mutableStateOf<SessionEntity?>(null) }
    var showPasteSpinsDialog by remember { mutableStateOf(false) }
    var pasteSpinsSessionName by remember { mutableStateOf("") }
    var pasteSpinsContent by remember { mutableStateOf("") }

    val folderDisplayName = remember(backupFolderUri) {
        if (backupFolderUri.isNullOrBlank()) {
            "No folder selected"
        } else {
            try {
                val uri = Uri.parse(backupFolderUri)
                val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                val name = doc?.name
                if (!name.isNullOrBlank()) {
                    name
                } else {
                    val decoded = Uri.decode(uri.toString())
                    when {
                        decoded.contains("primary:") -> decoded.substringAfter("primary:")
                        else -> "Selected Folder"
                    }
                }
            } catch (e: Exception) {
                "Configured Folder"
            }
        }
    }

    // Folder Picker Launcher for user-selected backup location
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (uri != null) {
            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                onSetBackupFolder(uri)
                statusMessage = "Backup folder set successfully."
                Toast.makeText(context, "Backup folder selected", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                statusMessage = "Error setting folder: ${e.localizedMessage}"
            }
        }
    }

    // Backup File Creator Launcher (fallback when no folder set or user wants specific file)
    val backupFileCreatorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = onGetBackupJson()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.bufferedWriter().use { it.write(json) }
                    }
                    withContext(Dispatchers.Main) {
                        statusMessage = "All sessions backed up safely to file."
                        Toast.makeText(context, "Backup file saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Backup failed: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    // Restore Backup File Launcher
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""
                    val result = onRestoreBackup(json)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { (sessionsCount, gameplayCount) ->
                            statusMessage = "Restored $sessionsCount roulette sessions & $gameplayCount gameplay sessions!"
                            Toast.makeText(context, "Restored $sessionsCount sessions & $gameplayCount gameplays", Toast.LENGTH_LONG).show()
                        }.onFailure { err ->
                            statusMessage = "Restore failed: ${err.localizedMessage}"
                            Toast.makeText(context, "Restore failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Error reading backup file: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    // File Import Launcher (reads spins from 1st to last)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    var filename = "Imported Spins"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                filename = cursor.getString(nameIndex) ?: filename
                            }
                        }
                    }
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: ""

                    withContext(Dispatchers.Main) {
                        onImportFile(filename.removeSuffix(".txt").removeSuffix(".csv"), content) { count ->
                            if (count > 0) {
                                statusMessage = "Successfully imported $count spins from $filename"
                            } else {
                                statusMessage = "Failed to import: No valid roulette spins found in $filename"
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Error reading file: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    // File Export Launcher (writes spins from 1st to last in that order)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val content = onGetExportContent(sessionTargetForExport)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.bufferedWriter().use { writer ->
                            writer.write(content)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        val label = sessionTargetForExport?.name ?: currentName.ifEmpty { "Current Session" }
                        statusMessage = "Exported spins (1st to last) for '$label' to file."
                        Toast.makeText(context, "Saved spins to file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        statusMessage = "Error writing file: ${e.localizedMessage}"
                    }
                }
            }
        }
    }

    if (showPasteSpinsDialog) {
        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showPasteSpinsDialog = false },
            title = {
                Text("Paste Spin Data (In-App)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Paste numbers (separated by commas, spaces, or lines, e.g. 17, 20, 00, 32):",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.5.sp
                    )

                    OutlinedTextField(
                        value = pasteSpinsSessionName,
                        onValueChange = { pasteSpinsSessionName = it },
                        label = { Text("Session Name", fontSize = 10.sp) },
                        placeholder = { Text("e.g. My Roulette Spins", fontSize = 10.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    OutlinedTextField(
                        value = pasteSpinsContent,
                        onValueChange = { pasteSpinsContent = it },
                        label = { Text("Spins Content", fontSize = 10.sp) },
                        placeholder = { Text("17, 20, 32, 15, 0, 00...", fontSize = 10.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(88.dp),
                        maxLines = 4,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF475569)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text ?: ""
                                if (clipText.isNotEmpty()) {
                                    pasteSpinsContent = clipText
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste from Clipboard", fontSize = 10.sp, color = Color(0xFF38BDF8))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sessionTitle = pasteSpinsSessionName.ifBlank { "Pasted Session" }
                        onImportFile(sessionTitle, pasteSpinsContent) { count ->
                            if (count > 0) {
                                statusMessage = "Successfully imported $count spins into '$sessionTitle'"
                                showPasteSpinsDialog = false
                                pasteSpinsContent = ""
                                pasteSpinsSessionName = ""
                            } else {
                                statusMessage = "Failed: No valid roulette spins found in pasted text."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Import", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteSpinsDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E293B))
                .padding(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT SIDE: Saved Sessions List
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Session Manager", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    if (statusMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF3B82F6), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusMessage!!,
                                color = Color(0xFF60A5FA),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Saved Sessions", color = Color.Gray, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))

                    // Sessions List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sessions) { session ->
                            val gameCount = gameplaySessionCounts[session.name] ?: 0
                            SessionItem(
                                session = session,
                                gameplayCount = gameCount,
                                onLoad = onLoad,
                                onDelete = { sessionPendingDelete = it },
                                onExport = { target ->
                                    sessionTargetForExport = target
                                    val safeName = target.name.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    exportLauncher.launch("${safeName}_spins.txt")
                                }
                            )
                        }
                        if (sessions.isEmpty()) {
                            item {
                                Text("No saved sessions", color = Color.Gray, modifier = Modifier.padding(8.dp), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                // RIGHT SIDE: Current Session & File Controls & External Backup
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Current Active Session", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        text = currentName.ifEmpty { "Active Session" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Save to database or export chronological spins (1st to last) to file.", color = Color(0xFF94A3B8), fontSize = 10.sp)

                    // Save Session in DB
                    Button(
                        onClick = {
                            onSave()
                            statusMessage = "Current session saved to database."
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Current Session", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Export Current Session to File
                    Button(
                        onClick = {
                            sessionTargetForExport = null
                            val safeName = currentName.ifBlank { "roulette_session" }
                                .replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            exportLauncher.launch("${safeName}_spins.txt")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Spins to File", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Import Spin Data File
                    Button(
                        onClick = {
                            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            importLauncher.launch(arrayOf("text/*", "*/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import Spin Data File", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Quick Paste Spins (In-App)
                    OutlinedButton(
                        onClick = { showPasteSpinsDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC084FC)),
                        border = BorderStroke(1.dp, Color(0xFF7C3AED)),
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste Spins Directly (In-App)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Divider
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334155)))
                    Spacer(Modifier.height(2.dp))

                    // ================= EXTERNAL STORAGE BACKUP & SAFE RETENTION =================
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Text(
                                "External Backup & Safe Retention",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            "Safely retains all Roulette & Gameplay sessions in phone storage (e.g. Downloads) even if app is uninstalled.",
                            color = Color(0xFF94A3B8),
                            fontSize = 8.5.sp,
                            lineHeight = 11.sp
                        )

                        // Folder Location Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = if (!backupFolderUri.isNullOrBlank()) Color(0xFFFBBF24) else Color.Gray,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = folderDisplayName,
                                    color = if (!backupFolderUri.isNullOrBlank()) Color(0xFFE2E8F0) else Color.Gray,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = {
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    folderPickerLauncher.launch(null)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (backupFolderUri.isNullOrBlank()) "Choose Folder" else "Change",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Backup All & Restore Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Backup All Button
                            Button(
                                onClick = {
                                    if (!backupFolderUri.isNullOrBlank()) {
                                        coroutineScope.launch {
                                            val uri = Uri.parse(backupFolderUri)
                                            val res = onBackupToFolder(uri)
                                            res.onSuccess { savedName ->
                                                statusMessage = "All sessions backed up safely to '$savedName' in selected folder!"
                                                Toast.makeText(context, "Saved backup to folder", Toast.LENGTH_SHORT).show()
                                            }.onFailure {
                                                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                                backupFileCreatorLauncher.launch("roulette_tactician_backup.json")
                                            }
                                        }
                                    } else {
                                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                        backupFileCreatorLauncher.launch("roulette_tactician_backup.json")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Backup All", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Restore Backup Button
                            Button(
                                onClick = {
                                    activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    restoreBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Restore Backup", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    sessionPendingDelete?.let { targetSession ->
        val associatedGames = gameplaySessionCounts[targetSession.name] ?: 0
        AlertDialog(
            onDismissRequest = { sessionPendingDelete = null },
            title = {
                Text(
                    text = if (associatedGames > 0) "Warning: Associated Game Plays" else "Delete Spin Session?",
                    color = if (associatedGames > 0) Color(0xFFF87171) else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (associatedGames > 0) {
                        Text(
                            text = "Spin session '${targetSession.name}' has $associatedGames associated game play ${if (associatedGames == 1) "session" else "sessions"}.",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Game play sessions are built specifically on the spin results of this session. Deleting it will permanently delete both this spin session and all $associatedGames associated game play ${if (associatedGames == 1) "session" else "sessions"}.\n\nAre you sure you want to proceed?",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    } else {
                        Text(
                            text = "Are you sure you want to delete spin session '${targetSession.name}'?",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(targetSession)
                        sessionPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (associatedGames > 0) "Delete All" else "Delete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionPendingDelete = null }
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun SessionItem(
    session: SessionEntity,
    gameplayCount: Int = 0,
    onLoad: (SessionEntity) -> Unit,
    onDelete: (SessionEntity) -> Unit,
    onExport: (SessionEntity) -> Unit
) {
    val formattedDateTime = remember(session.timestamp) {
        SimpleDateFormat("ddMMMyyyy HH:mm", Locale.ENGLISH).format(Date(session.timestamp))
    }
    val spinCount = remember(session.history) {
        com.roulettetactician.logic.SessionDataValidator.parseAndValidateHistory(session.history).validCount
    }
    val gameLabel = if (gameplayCount == 1) "1 gameplay session" else "$gameplayCount gameplay sessions"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF334155), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$formattedDateTime • $spinCount spins • $gameLabel", color = Color(0xFF94A3B8), fontSize = 10.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // Load
            IconButton(
                onClick = { onLoad(session) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Load", tint = Color(0xFF3B82F6), modifier = Modifier.size(17.dp))
            }
            // Export to file
            IconButton(
                onClick = { onExport(session) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export to File", tint = Color(0xFF10B981), modifier = Modifier.size(17.dp))
            }
            // Delete
            IconButton(
                onClick = { onDelete(session) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
            }
        }
    }
}
