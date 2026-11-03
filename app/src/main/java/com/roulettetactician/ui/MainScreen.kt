package com.roulettetactician.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.hilt.navigation.compose.hiltViewModel
import com.roulettetactician.logic.Geometry
import com.roulettetactician.ui.components.Chip
import com.roulettetactician.ui.components.RouletteBoard
import com.roulettetactician.ui.components.SpinHistoryItem
import com.roulettetactician.ui.components.SpinLogCameraDialog
import com.roulettetactician.ui.components.SpinScanResultDialog
import com.roulettetactician.ui.components.ApiKeyDialog
import com.roulettetactician.ui.components.SessionManagerDialog
import com.roulettetactician.ui.components.HotColdEditor
import com.roulettetactician.ui.components.RouletteWheel
import com.roulettetactician.ui.components.Keypad
import com.roulettetactician.ui.components.EditKeypad
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import com.roulettetactician.logic.TriggerCalculator
import com.roulettetactician.ui.components.TriggersDashboard
import com.roulettetactician.ui.components.MovingAverageDashboard
import com.roulettetactician.ui.components.MACategoryTab
import com.roulettetactician.logic.MovingAverageCalculator
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableDoubleStateOf
import com.roulettetactician.logic.HeatmapCalculator
import com.roulettetactician.logic.BetTrendCalculator
import com.roulettetactician.ui.components.SharedRouletteFelt
import com.roulettetactician.ui.components.GameplayLedgerCard
import com.roulettetactician.ui.components.FeltHeatMode
import com.roulettetactician.ui.components.BetTrendDashboard
import com.roulettetactician.ui.components.TimeMachineScrubber
import com.roulettetactician.ui.model.TableType
import com.roulettetactician.ui.model.TableState
import com.roulettetactician.ui.model.PlacedChip
import androidx.compose.ui.draw.clipToBounds
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.AnnotatedString
import com.roulettetactician.logic.ChipRemovalMode
import com.roulettetactician.logic.GameplayCalculator
import com.roulettetactician.ui.components.GameplayEquityCard
import com.roulettetactician.ui.components.GameplaySessionConfigCard
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: GameViewModel = hiltViewModel()
) {
    val history by viewModel.spinHistory.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val stats by viewModel.heatmapStats.collectAsState()

    val gameplaySessions by viewModel.gameplaySessions.collectAsState()
    val activeGameplaySession by viewModel.activeGameplaySession.collectAsState()
    val savedSessions by viewModel.savedSessions.collectAsState()
    val gameplayRecords by viewModel.gameplayRecords.collectAsState()
    val chipRemovalMode by viewModel.chipRemovalMode.collectAsState()
    val hasUnsavedSpinEdits by viewModel.hasUnsavedSpinEdits.collectAsState()
    val pendingEditSpinIndex by viewModel.pendingEditSpinIndex.collectAsState()
    val timeTravelIndex by viewModel.timeTravelIndex.collectAsState()
    val canUndo by viewModel.canUndoChipAction.collectAsState()
    val canRedo by viewModel.canRedoChipAction.collectAsState()
    val canUndoSpin by viewModel.canUndoSpinAction.collectAsState()
    val canRedoSpin by viewModel.canRedoSpinAction.collectAsState()
    val scanState by viewModel.spinScanState.collectAsState()
    val geminiApiKey by viewModel.geminiApiKey.collectAsState()
    val activeEditIndex = timeTravelIndex ?: if (hasUnsavedSpinEdits) (pendingEditSpinIndex ?: history.size) else null

    var showCameraScanner by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    fun performActionWithDirtyCheck(action: () -> Unit) {
        if (hasUnsavedSpinEdits) {
            pendingAction = action
            showUnsavedDialog = true
        } else {
            action()
        }
    }

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val feltScrollState = rememberScrollState()
    val trendLeftScrollState = rememberScrollState()
    val trendRightScrollState = rememberScrollState()
    val tabSaveableStateHolder = rememberSaveableStateHolder()
    var equityCardTopOffset by remember { mutableIntStateOf(0) }

    // Board Scale/Pan State - Default 1.0f zoom centered to fill board area
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    
    // History State
    var historyExpanded by remember { mutableStateOf(false) }
    val displayedHistory = if (historyExpanded) history else history.take(20)
    
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = com.roulettetactician.data.SpinLogExtractor.loadBitmapFromUri(context, it)
            if (bitmap != null) {
                viewModel.scanSpinLog(bitmap)
            }
        }
    }

    // Auto-clear focus when soft keyboard is dismissed so no blinking cursor remains
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) {
            focusManager.clearFocus(force = true)
        }
    }

    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var showKeypad by remember { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showGameplaySessionDialog by remember { mutableStateOf(false) }
    var editHot by remember { mutableStateOf(false) }
    var editCold by remember { mutableStateOf(false) }
    var activeMainTab by remember { mutableStateOf("Felt") } // "Felt" or "MA"

    // Moving Average Dashboard Persistent State (survives tab switches, session changes, and restarts)
    val maType by viewModel.maType.collectAsState()
    val maPeriod by viewModel.maPeriod.collectAsState()
    val maActiveCategory by viewModel.maActiveCategory.collectAsState()
    val maAlmaOffset by viewModel.maAlmaOffset.collectAsState()
    val maAlmaSigma by viewModel.maAlmaSigma.collectAsState()
    val maKamaFast by viewModel.maKamaFast.collectAsState()
    val maKamaSlow by viewModel.maKamaSlow.collectAsState()

    // Trigger Dashboard Thresholds State (survives tab switches, session changes, and restarts)
    val triggerThresholdRB by viewModel.triggerThresholdRB.collectAsState()
    val triggerThresholdHL by viewModel.triggerThresholdHL.collectAsState()
    val triggerThresholdOE by viewModel.triggerThresholdOE.collectAsState()
    val triggerThresholdDozens by viewModel.triggerThresholdDozens.collectAsState()
    val triggerThresholdColumns by viewModel.triggerThresholdColumns.collectAsState()

    val triggerColdThresholdRB by viewModel.triggerColdThresholdRB.collectAsState()
    val triggerColdThresholdHL by viewModel.triggerColdThresholdHL.collectAsState()
    val triggerColdThresholdOE by viewModel.triggerColdThresholdOE.collectAsState()
    val triggerColdThresholdDozens by viewModel.triggerColdThresholdDozens.collectAsState()
    val triggerColdThresholdColumns by viewModel.triggerColdThresholdColumns.collectAsState()

    // Bet Trend Dashboard Persistent State (survives tab switches, session changes, and restarts)
    val trendActiveFamily by viewModel.trendActiveFamily.collectAsState()
    val trendLookbackWindow by viewModel.trendLookbackWindow.collectAsState()
    val trendSelectedProgression by viewModel.trendSelectedProgression.collectAsState()
    val trendColdLimit by viewModel.trendColdLimit.collectAsState()
    val trendPrimaryCriterion by viewModel.trendPrimaryCriterion.collectAsState()
    val trendSecondaryCriterion by viewModel.trendSecondaryCriterion.collectAsState()
    val trendColdRule by viewModel.trendColdRule.collectAsState()
    val trendEqualizePayout by viewModel.trendEqualizePayout.collectAsState()
    val trendPushOrProfit by viewModel.trendPushOrProfit.collectAsState()
    val trendRiskCap by viewModel.trendRiskCap.collectAsState()
    val trendUnderwaterFilter by viewModel.trendUnderwaterFilter.collectAsState()
    val trendTransitionRule by viewModel.trendTransitionRule.collectAsState()
    val trendSwitchSensitivity by viewModel.trendSwitchSensitivity.collectAsState()
    val trendShowIdealOverlay by viewModel.trendShowIdealOverlay.collectAsState()
    val trendStartMode by viewModel.trendStartMode.collectAsState()
    val trendEquityChartHeight by viewModel.trendEquityChartHeightDp.collectAsState()
    val trendLedgerTableHeight by viewModel.trendLedgerTableHeightDp.collectAsState()

    // Chronological order for MA & Triggers analysis (oldest to newest)
    val chronologicalSpins = remember(history) { history.reversed().map { it.number } }

    // Effective spins based on Time Machine historical position
    val effectiveChronologicalSpins = remember(chronologicalSpins, timeTravelIndex) {
        if (timeTravelIndex == null || timeTravelIndex!! >= chronologicalSpins.size) {
            chronologicalSpins
        } else {
            chronologicalSpins.take(timeTravelIndex!!.coerceIn(1, chronologicalSpins.size))
        }
    }

    var mainFeltHeatMode by rememberSaveable { mutableStateOf(FeltHeatMode.GLOBAL_HEAT) }
    val mainFeltWeights = remember(effectiveChronologicalSpins, uiState.analysisWindow, uiState.tableType) {
        val windowSpins = effectiveChronologicalSpins.takeLast(minOf(uiState.analysisWindow, effectiveChronologicalSpins.size))
        BetTrendCalculator.computeFrequencies(windowSpins, uiState.tableType == TableType.US)
    }

    val outsideHeatMap = remember(
        mainFeltHeatMode,
        effectiveChronologicalSpins,
        uiState.analysisWindow,
        mainFeltWeights,
        maType,
        maPeriod,
        maAlmaOffset,
        maAlmaSigma,
        maKamaFast,
        maKamaSlow,
        uiState.tableType
    ) {
        when (mainFeltHeatMode) {
            FeltHeatMode.GLOBAL_HEAT -> {
                HeatmapCalculator.computeOutsideHeat(
                    spins = effectiveChronologicalSpins.reversed(),
                    windowSize = uiState.analysisWindow
                )
            }
            FeltHeatMode.TREND_WEIGHTS -> {
                HeatmapCalculator.computeOutsideWeights(
                    numberWeights = mainFeltWeights
                )
            }
            FeltHeatMode.MA_TREND -> {
                val maConfig = MovingAverageCalculator.MAConfig(
                    type = maType,
                    period = maPeriod,
                    almaOffset = maAlmaOffset,
                    almaSigma = maAlmaSigma,
                    kamaFast = maKamaFast,
                    kamaSlow = maKamaSlow
                )
                MovingAverageCalculator.computeOutsideMA(
                    chronologicalSpins = effectiveChronologicalSpins,
                    config = maConfig,
                    isUS = uiState.tableType == TableType.US
                )
            }
        }
    }



    val feltStartBankroll = activeGameplaySession?.startingBankroll ?: 1000.0
    val feltBaseInsideBet = maxOf(
        activeGameplaySession?.insideBaseUnit ?: 1.0,
        activeGameplaySession?.insideMinUnit ?: 1.0
    )
    val feltBaseOutsideBet = maxOf(
        activeGameplaySession?.outsideBaseUnit ?: 5.0,
        activeGameplaySession?.outsideMinUnit ?: 5.0
    )

    // Historical Bet Trend calculations for replay on the Felt tab
    val historicalTrendResult = remember(
        effectiveChronologicalSpins,
        uiState.tableType,
        trendActiveFamily,
        trendLookbackWindow,
        trendSelectedProgression,
        trendColdLimit,
        trendPrimaryCriterion,
        trendSecondaryCriterion,
        trendColdRule,
        trendEqualizePayout,
        trendPushOrProfit,
        trendRiskCap,
        trendUnderwaterFilter,
        trendTransitionRule,
        trendSwitchSensitivity,
        trendStartMode,
        feltStartBankroll,
        feltBaseInsideBet,
        feltBaseOutsideBet
    ) {
        if (effectiveChronologicalSpins.isEmpty()) null
        else {
            BetTrendCalculator.optimize(
                spins = effectiveChronologicalSpins,
                isUS = uiState.tableType == TableType.US,
                targetProgression = trendSelectedProgression,
                family = trendActiveFamily,
                coldLimit = trendColdLimit,
                lookbackWindow = trendLookbackWindow,
                startBankroll = feltStartBankroll,
                baseInsideBet = feltBaseInsideBet,
                baseOutsideBet = feltBaseOutsideBet,
                primaryCriterion = trendPrimaryCriterion,
                secondaryCriterion = trendSecondaryCriterion,
                coldRule = trendColdRule,
                equalizePayout = trendEqualizePayout,
                pushOrProfit = trendPushOrProfit,
                riskCap = trendRiskCap,
                underwaterFilter = trendUnderwaterFilter,
                transitionRule = trendTransitionRule,
                switchSensitivity = trendSwitchSensitivity,
                startMode = trendStartMode
            )
        }
    }

    val historicalRecommendedChips = remember(historicalTrendResult) {
        historicalTrendResult?.recommendedBets?.mapIndexed { idx, rec ->
            PlacedChip(
                id = (idx + 1000L),
                value = maxOf(1, rec.dollarAmount.roundToInt()),
                xPercent = rec.bet.xPercent,
                yPercent = rec.bet.yPercent,
                betId = rec.bet.betId
            )
        } ?: emptyList<PlacedChip>()
    }

    val historicalChipLabels = remember(historicalTrendResult) {
        historicalTrendResult?.recommendedBets?.mapIndexed { idx, rec ->
            (idx + 1000L) to "$${maxOf(1, rec.dollarAmount.roundToInt())}"
        }?.toMap() ?: emptyMap<Long, String>()
    }
    val triggerThresholds = remember(
        triggerThresholdRB, triggerThresholdHL, triggerThresholdOE, triggerThresholdDozens, triggerThresholdColumns,
        triggerColdThresholdRB, triggerColdThresholdHL, triggerColdThresholdOE, triggerColdThresholdDozens, triggerColdThresholdColumns
    ) {
        TriggerCalculator.TriggerThresholds(
            redBlack = triggerThresholdRB,
            highLow = triggerThresholdHL,
            oddEven = triggerThresholdOE,
            dozens = triggerThresholdDozens,
            columns = triggerThresholdColumns,
            coldRedBlack = triggerColdThresholdRB,
            coldHighLow = triggerColdThresholdHL,
            coldOddEven = triggerColdThresholdOE,
            coldDozens = triggerColdThresholdDozens,
            coldColumns = triggerColdThresholdColumns
        )
    }
    val triggerResult = remember(effectiveChronologicalSpins, triggerThresholds) {
        TriggerCalculator.evaluate(effectiveChronologicalSpins, triggerThresholds)
    }
    
    // Config Inputs
    var tableNameInput by remember { mutableStateOf("") }
    var windowInput by remember { mutableStateOf("") }
    
    // Sync local state with VM
    LaunchedEffect(uiState.wheelName) { tableNameInput = uiState.wheelName }
    LaunchedEffect(uiState.analysisWindow) { windowInput = uiState.analysisWindow.toString() }

    // Table Restore Event Listener
    LaunchedEffect(Unit) {
        viewModel.tableRestoreEvents.collect { tb ->
            scale = tb.scale
            offset = Offset(tb.offsetX, tb.offsetY)
        }
    }



    if (showKeypad) {
        Keypad(
            tableType = uiState.tableType,
            onNumberSelected = { num ->
                viewModel.addSpin(num)
                // Don't close - allow continuous entry
            },
            onDismiss = { showKeypad = false }
        )
    }

    if (showSessions) {
        val sessions by viewModel.savedSessions.collectAsState()
        val backupFolderUri by viewModel.backupFolderUri.collectAsState()
        val gameplaySessionCounts by viewModel.gameplaySessionCounts.collectAsState()
        SessionManagerDialog(
            currentName = uiState.wheelName,
            sessions = sessions,
            gameplaySessionCounts = gameplaySessionCounts,
            onSave = { 
                viewModel.saveCurrentSession(
                    trendFamily = trendActiveFamily,
                    trendLookback = trendLookbackWindow,
                    trendProgression = trendSelectedProgression,
                    trendColdLimit = trendColdLimit,
                    trendPrimaryCriterion = trendPrimaryCriterion,
                    trendSecondaryCriterion = trendSecondaryCriterion,
                    trendColdRule = trendColdRule,
                    trendEqualizePayout = trendEqualizePayout,
                    trendPushOrProfit = trendPushOrProfit,
                    trendRiskCap = trendRiskCap,
                    trendUnderwaterFilter = trendUnderwaterFilter,
                    trendTransitionRule = trendTransitionRule,
                    trendSwitchSensitivity = trendSwitchSensitivity,
                    trendShowIdealOverlay = trendShowIdealOverlay,
                    trendStartMode = trendStartMode,
                    maType = maType,
                    maPeriod = maPeriod,
                    maCategory = maActiveCategory,
                    maAlmaOffset = maAlmaOffset,
                    maAlmaSigma = maAlmaSigma,
                    maKamaFast = maKamaFast,
                    maKamaSlow = maKamaSlow,
                    triggerRB = triggerThresholdRB,
                    triggerHL = triggerThresholdHL,
                    triggerOE = triggerThresholdOE,
                    triggerDozens = triggerThresholdDozens,
                    triggerColumns = triggerThresholdColumns
                ) 
            },
            onLoad = { 
                viewModel.loadSession(it) 
                showSessions = false
            },
            onDelete = { viewModel.deleteSession(it) },
            onImportFile = { filename, content, onDone ->
                viewModel.importSessionFromFile(filename, content, onDone)
            },
            onGetExportContent = { session ->
                viewModel.getExportSpinsContent(session)
            },
            backupFolderUri = backupFolderUri,
            onSetBackupFolder = { uri -> viewModel.setBackupFolderUri(uri.toString()) },
            onBackupToFolder = { uri -> viewModel.saveBackupToFolder(context, uri) },
            onGetBackupJson = { viewModel.exportFullBackupJson() },
            onRestoreBackup = { json -> viewModel.restoreFullBackupFromJson(json) },
            onDismiss = { showSessions = false }
        )
    }
    
    if (editHot) {
        HotColdEditor(
            type = "HOT",
            numbers = uiState.hotOverrides,
            onSave = { list -> 
                 list.forEachIndexed { i, n -> viewModel.setManualHot(i, n) }
            },
            onDismiss = { editHot = false }
        )
    }

    if (editCold) {
         HotColdEditor(
            type = "COLD",
            numbers = uiState.coldOverrides,
            onSave = { list -> 
                 list.forEachIndexed { i, n -> viewModel.setManualCold(i, n) }
            },
            onDismiss = { editCold = false }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { 
                showUnsavedDialog = false
                pendingAction = null
            },
            title = { Text("Unsaved Bet Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { 
                Text(
                    "You have modified bets for spin #${pendingEditSpinIndex ?: ""}. Would you like to save your changes to this spin's game record?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveSpinEdits()
                        showUnsavedDialog = false
                        val act = pendingAction
                        pendingAction = null
                        act?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            viewModel.discardSpinEdits()
                            showUnsavedDialog = false
                            val act = pendingAction
                            pendingAction = null
                            act?.invoke()
                        }
                    ) {
                        Text("Discard", color = Color(0xFFF87171))
                    }
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            pendingAction = null
                        }
                    ) {
                        Text("Cancel", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Expanded History Dialog
    if (historyExpanded) {
        var editingSpin: com.roulettetactician.data.SpinResult? by remember { mutableStateOf(null) }
        var customTrimMode: String? by remember { mutableStateOf(null) } // "DISCARD" or "RETAIN"
        var customTrimCountText by remember { mutableStateOf("") }

        if (customTrimMode != null) {
            val isDiscard = customTrimMode == "DISCARD"
            AlertDialog(
                onDismissRequest = { customTrimMode = null },
                title = {
                    Text(
                        text = if (isDiscard) "Discard First # of Spins" else "Retain Only Latest # of Spins",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isDiscard) {
                                "Enter how many of the oldest spins to discard (Total: ${history.size}):"
                            } else {
                                "Enter how many of the latest spins to retain (Total: ${history.size}):"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = customTrimCountText,
                                onValueChange = { if (it.all { c -> c.isDigit() }) customTrimCountText = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    val countVal = customTrimCountText.toIntOrNull() ?: 0
                    Button(
                        onClick = {
                            if (countVal > 0) {
                                if (isDiscard) {
                                    viewModel.discardFirstSpins(countVal)
                                } else {
                                    viewModel.retainLatestSpins(countVal)
                                }
                            }
                            customTrimMode = null
                        },
                        enabled = countVal > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDiscard) Color(0xFFDC2626) else Color(0xFF0284C7)
                        )
                    ) {
                        Text(if (isDiscard) "Discard $countVal" else "Retain $countVal", color = Color.White, fontSize = 12.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { customTrimMode = null }) {
                        Text("Cancel", color = Color.Gray, fontSize = 12.sp)
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
        
        // Edit Keypad Dialog
        if (editingSpin != null) {
            val historyIndex = history.indexOfFirst { it.id == editingSpin!!.id }
            val chronoIndex = if (historyIndex >= 0) history.size - historyIndex else 0
            val spinLabel = if (chronoIndex > 0) "Spin #$chronoIndex" else "Edit Spin"

            EditKeypad(
                currentNumber = editingSpin!!.number,
                spinLabel = spinLabel,
                tableType = uiState.tableType,
                onNumberSelected = { newNumber ->
                    viewModel.updateSpin(editingSpin!!, newNumber)
                    editingSpin = null
                },
                onInsertBefore = { newNumber ->
                    viewModel.insertSpinBefore(editingSpin!!, newNumber)
                    editingSpin = null
                },
                onInsertAfter = { newNumber ->
                    viewModel.insertSpinAfter(editingSpin!!, newNumber)
                    editingSpin = null
                },
                onDelete = {
                    viewModel.deleteSpin(editingSpin!!)
                    editingSpin = null
                },
                onDismiss = { editingSpin = null }
            )
        }
        
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { historyExpanded = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B))
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spin History (${history.size} spins) - Tap to edit",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.undoSpinAction() },
                                enabled = canUndoSpin
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo Spin Action",
                                    tint = if (canUndoSpin) Color(0xFF38BDF8) else Color(0xFF475569)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.redoSpinAction() },
                                enabled = canRedoSpin
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo Spin Action",
                                    tint = if (canRedoSpin) Color(0xFF38BDF8) else Color(0xFF475569)
                                )
                            }
                            IconButton(onClick = { historyExpanded = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // Bulk Discard First / Retain Latest Controls Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "Discard First:",
                            color = Color(0xFFF87171),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        listOf(50, 100, 200).forEach { count ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (history.isNotEmpty()) Color(0xFF7F1D1D) else Color(0xFF1E293B),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (history.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.7f) else Color(0xFF334155),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable(enabled = history.isNotEmpty()) {
                                        viewModel.discardFirstSpins(count)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$count",
                                    color = if (history.isNotEmpty()) Color.White else Color(0xFF64748B),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (history.isNotEmpty()) Color(0xFF7F1D1D) else Color(0xFF1E293B),
                                    RoundedCornerShape(3.dp)
                                )
                                .border(
                                    1.dp,
                                    if (history.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.7f) else Color(0xFF334155),
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable(enabled = history.isNotEmpty()) {
                                    customTrimCountText = ""
                                    customTrimMode = "DISCARD"
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Custom #",
                                color = if (history.isNotEmpty()) Color(0xFFFCA5A5) else Color(0xFF64748B),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF334155)))
                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "Retain Latest:",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        listOf(50, 100, 200).forEach { count ->
                            val canRetain = history.size > count
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (canRetain) Color(0xFF0C4A6E) else Color(0xFF1E293B),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (canRetain) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color(0xFF334155),
                                        RoundedCornerShape(3.dp)
                                    )
                                    .clickable(enabled = canRetain) {
                                        viewModel.retainLatestSpins(count)
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$count",
                                    color = if (canRetain) Color.White else Color(0xFF64748B),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (history.isNotEmpty()) Color(0xFF0C4A6E) else Color(0xFF1E293B),
                                    RoundedCornerShape(3.dp)
                                )
                                .border(
                                    1.dp,
                                    if (history.isNotEmpty()) Color(0xFF38BDF8).copy(alpha = 0.7f) else Color(0xFF334155),
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable(enabled = history.isNotEmpty()) {
                                    customTrimCountText = ""
                                    customTrimMode = "RETAIN"
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Custom #",
                                color = if (history.isNotEmpty()) Color(0xFFBAE6FD) else Color(0xFF64748B),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (history.isEmpty()) {
                        Text("No spins recorded yet", color = Color.Gray)
                    } else {
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 32.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                        ) {
                            items(history.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { editingSpin = history[index] }
                                ) {
                                    SpinHistoryItem(history[index].number, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    // Bottom buttons row: Clear, Copy, Camera, Image in 1 compact bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        // Clear All button
                        Button(
                            onClick = {
                                viewModel.clearHistory()
                                historyExpanded = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Clear", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        // Copy button (copies in reverse order from what is shown: oldest to newest)
                        Button(
                            onClick = {
                                val text = history.reversed().joinToString(",") { it.number.toString() }
                                clipboardManager.setText(AnnotatedString(text))
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Copy", fontSize = 11.sp, maxLines = 1)
                        }
                        // Scan Log (CameraX -> Gemini Vision)
                        Button(
                            onClick = { showCameraScanner = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Camera", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                        // From Image (Phone Gallery / Storage -> Gemini Vision)
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Image", color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    // Camera Scan Dialog
    if (showCameraScanner) {
        SpinLogCameraDialog(
            onDismiss = { showCameraScanner = false },
            onOpenApiKeySettings = { showApiKeyDialog = true },
            onSelectFromGallery = {
                showCameraScanner = false
                imagePickerLauncher.launch("image/*")
            },
            onPhotoCaptured = { bitmap ->
                showCameraScanner = false
                viewModel.scanSpinLog(bitmap)
            }
        )
    }

    // Scan Review / Error / Progress Dialog
    SpinScanResultDialog(
        state = scanState,
        onConfirm = { viewModel.confirmScannedSpins() },
        onRetake = {
            viewModel.dismissSpinScan()
            showCameraScanner = true
        },
        onOpenApiKeySettings = { showApiKeyDialog = true },
        onDismiss = { viewModel.dismissSpinScan() }
    )

    // In-App Gemini API Key Settings Dialog
    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentApiKey = geminiApiKey,
            onSave = { viewModel.setGeminiApiKey(it) },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // Condensed Game Play Session & Bet Configuration Popup Dialog
    if (showGameplaySessionDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showGameplaySessionDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Game Play Session & Bet Configuration",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showGameplaySessionDialog = false },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    GameplaySessionConfigCard(
                        sessions = gameplaySessions,
                        activeSession = activeGameplaySession,
                        availableRouletteSessions = remember(savedSessions) {
                            (listOf("New Session...", "My Wheel") + savedSessions.map { it.name }).distinct()
                        },
                        onSelectSession = { viewModel.selectGameplaySession(it) },
                        onCreateSession = { name, bankroll -> viewModel.createGameplaySession(name, bankroll) },
                        onRenameSession = { sess, newName -> viewModel.renameGameplaySession(sess, newName) },
                        onReassignSession = { sess, targetRouletteSession ->
                            viewModel.reassignGameplaySession(sess, targetRouletteSession)
                        },
                        onDeleteSession = { viewModel.deleteGameplaySession(it) },
                        onUpdateConfig = { bankroll, inBase, inMin, outBase, outMin ->
                            viewModel.updateGameplaySessionConfig(bankroll, inBase, inMin, outBase, outMin)
                            showGameplaySessionDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus(force = true)
            }
    ) {
        
        // ================= HEADER ROW (56dp) =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color(0xFF1E293B))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus(force = true)
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Text("Roulette Tactician", color = Color(0xFFEAB308), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            
            Spacer(Modifier.width(14.dp))
            
            // Spin Session Name Input (compact 2-row label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Spin\nSession:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(32.dp)
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = tableNameInput,
                        onValueChange = { 
                            tableNameInput = it
                            viewModel.setWheelName(it) 
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) }
                        ),
                        decorationBox = { innerTextField ->
                            if (tableNameInput.isEmpty()) {
                                Text("New Session...", color = Color.Gray, fontSize = 14.sp)
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Analysis Window Input (compact 2-row label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Analyse\nLast:",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(32.dp)
                        .border(1.dp, Color.DarkGray, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = windowInput,
                        onValueChange = { 
                            if (it.all { c -> c.isDigit() }) {
                                windowInput = it
                                it.toIntOrNull()?.let { win -> viewModel.setAnalysisWindow(win) }
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) }
                        )
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Sessions Folder Icon
            IconButton(onClick = { showSessions = true }) {
                Icon(
                    Icons.Filled.Folder, 
                    contentDescription = "Sessions", 
                    tint = Color(0xFFEAB308)
                )
            }

            // Gemini API Key Settings Icon
            IconButton(onClick = { showApiKeyDialog = true }) {
                Icon(
                    Icons.Filled.Key, 
                    contentDescription = "Gemini API Key", 
                    tint = if (geminiApiKey.isNotBlank()) Color(0xFFEAB308) else Color(0xFF64748B)
                )
            }
            
            Spacer(Modifier.weight(1f))

            // EU/US Toggle (Compact, border drawn, EU on left, US on right)
            Row(
                modifier = Modifier
                    .height(28.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0xFF475569), RoundedCornerShape(4.dp))
                    .padding(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // EU Button (Left)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (uiState.tableType == TableType.EU) Color(0xFF2563EB) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { viewModel.setTableType(TableType.EU) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "EU",
                        color = if (uiState.tableType == TableType.EU) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                // US Button (Right)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (uiState.tableType == TableType.US) Color(0xFF2563EB) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { viewModel.setTableType(TableType.US) }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "US",
                        color = if (uiState.tableType == TableType.US) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            
            Spacer(Modifier.width(8.dp))

            // ADD SPIN Button (2 rows: "Add\nSpin")
            Button(
                onClick = { showKeypad = true }, 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)), // Purple
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Add\nSpin",
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        
        // ================= HOT/COLD BAR (26dp) =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(Color.Black)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // HOT section
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { editHot = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HOT: ", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (uiState.hotOverrides.all { it == null }) {
                    Text("Tap to set", color = Color.DarkGray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                } else {
                    uiState.hotOverrides.forEach { n ->
                        if (n != null) {
                            Text("$n ", color = Color(0xFFFACC15), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.width(3.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // COLD section
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { editCold = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("COLD: ", color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                if (uiState.coldOverrides.all { it == null }) {
                    Text("Tap to set", color = Color.DarkGray, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                } else {
                    uiState.coldOverrides.forEach { n ->
                        if (n != null) {
                            Text("$n ", color = Color(0xFFBFDBFE), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Spacer(Modifier.width(3.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // ================= TABS: "Felt", "MA" & "Triggers" =================
            Row(
                modifier = Modifier
                    .height(22.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                    .padding(1.5.dp),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Felt Tab
                val isFelt = activeMainTab == "Felt"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (isFelt) Color(0xFF15803D) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { performActionWithDirtyCheck { activeMainTab = "Felt" } }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Felt",
                        color = if (isFelt) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (isFelt) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 11.sp
                    )
                }

                // Bet Trend Tab (Immediately to the right of Felt)
                val isBetTrend = activeMainTab == "Bet Trend"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (isBetTrend) Color(0xFF0284C7) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { performActionWithDirtyCheck { activeMainTab = "Bet Trend" } }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bet Trend",
                        color = if (isBetTrend) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (isBetTrend) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 11.sp
                    )
                }

                // MA Tab
                val isMA = activeMainTab == "MA"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (isMA) Color(0xFF059669) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { performActionWithDirtyCheck { activeMainTab = "MA" } }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "MA",
                        color = if (isMA) Color.White else Color(0xFF94A3B8),
                        fontWeight = if (isMA) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        lineHeight = 11.sp
                    )
                }

                // Triggers Tab
                val isTriggers = activeMainTab == "Triggers"
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .background(
                            if (isTriggers) Color(0xFFD97706) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { performActionWithDirtyCheck { activeMainTab = "Triggers" } }
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "Triggers",
                            color = if (isTriggers) Color.White else Color(0xFF94A3B8),
                            fontWeight = if (isTriggers) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 10.sp,
                            lineHeight = 11.sp
                        )

                        // Flashing alert dot on the corner of the Trigger tab when triggered
                        if (triggerResult.hasAnyTriggered) {
                            PulsingTriggerDot(modifier = Modifier.size(6.dp))
                        }
                    }
                }
            }

            // 3-mode Felt Heat toggle (Visible when on the Felt or Bet Trend tab)
            if (activeMainTab == "Felt" || activeMainTab == "Bet Trend") {
                Spacer(Modifier.width(6.dp))
                Row(
                    modifier = Modifier
                        .height(22.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                        .padding(1.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                if (mainFeltHeatMode == FeltHeatMode.GLOBAL_HEAT) Color(0xFF0284C7) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { mainFeltHeatMode = FeltHeatMode.GLOBAL_HEAT }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Global Heat",
                            color = if (mainFeltHeatMode == FeltHeatMode.GLOBAL_HEAT) Color.White else Color(0xFF94A3B8),
                            fontWeight = if (mainFeltHeatMode == FeltHeatMode.GLOBAL_HEAT) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 9.5.sp,
                            lineHeight = 10.5.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                if (mainFeltHeatMode == FeltHeatMode.TREND_WEIGHTS) Color(0xFF10B981) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { mainFeltHeatMode = FeltHeatMode.TREND_WEIGHTS }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Trend Weights",
                            color = if (mainFeltHeatMode == FeltHeatMode.TREND_WEIGHTS) Color.White else Color(0xFF94A3B8),
                            fontWeight = if (mainFeltHeatMode == FeltHeatMode.TREND_WEIGHTS) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 9.5.sp,
                            lineHeight = 10.5.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(
                                if (mainFeltHeatMode == FeltHeatMode.MA_TREND) Color(0xFF8B5CF6) else Color.Transparent,
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { mainFeltHeatMode = FeltHeatMode.MA_TREND }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "MA Trend",
                            color = if (mainFeltHeatMode == FeltHeatMode.MA_TREND) Color.White else Color(0xFF94A3B8),
                            fontWeight = if (mainFeltHeatMode == FeltHeatMode.MA_TREND) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 9.5.sp,
                            lineHeight = 10.5.sp
                        )
                    }
                }

                Spacer(Modifier.width(6.dp))

                // Condensed Game Play & Bet Config Button (same 22.dp height as MA Trend bar, minimal top/bottom margin)
                Column(
                    modifier = Modifier
                        .height(22.dp)
                        .background(
                            if (showGameplaySessionDialog) Color(0xFF0284C7) else Color(0xFF1E293B),
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (showGameplaySessionDialog) Color(0xFF38BDF8) else Color(0xFFF59E0B).copy(alpha = 0.65f),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { showGameplaySessionDialog = true }
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Game Play",
                        color = if (showGameplaySessionDialog) Color.White else Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Text(
                        text = "Bet Config",
                        color = if (showGameplaySessionDialog) Color.White else Color(0xFFFBBF24),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        lineHeight = 8.sp,
                        maxLines = 1,
                        softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }
            }

            // Fill remaining space on the right so both stay on the left side
            Spacer(Modifier.weight(1f))
        }

        // ================= TIME MACHINE (HISTORICAL REPLAY) TRANSPORT SCRUBBER =================
        if (activeMainTab in listOf("Felt", "Bet Trend") && chronologicalSpins.isNotEmpty()) {
            TimeMachineScrubber(
                totalSpins = chronologicalSpins.size,
                timeTravelIndex = timeTravelIndex,
                currentSpunNumber = effectiveChronologicalSpins.lastOrNull(),
                onIndexChange = { idx ->
                    performActionWithDirtyCheck { viewModel.setTimeTravelIndex(idx) }
                },
                onStepBack = {
                    performActionWithDirtyCheck { viewModel.stepBack() }
                },
                onStepForward = {
                    performActionWithDirtyCheck { viewModel.stepForward() }
                },
                onReturnToLive = {
                    performActionWithDirtyCheck { viewModel.returnToLive() }
                },
                onGoToStart = {
                    performActionWithDirtyCheck { viewModel.goToStart() }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // ================= PINNED HISTORY BAR (SHOWN IN BOTH FELT & BET TREND) =================
        if (activeMainTab in listOf("Felt", "Bet Trend")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF334155))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (timeTravelIndex != null) "REPLAY 20" else "LAST 20",
                    color = if (timeTravelIndex != null) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))

                if (history.isEmpty()) {
                    Text("No spins yet", color = Color.DarkGray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 11.sp)
                } else {
                    val displayedSpins = if (timeTravelIndex != null) effectiveChronologicalSpins.reversed() else history.map { it.number }
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = false
                    ) {
                        items(displayedSpins.take(20)) { num ->
                            SpinHistoryItem(num, Modifier.size(22.dp))
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Expand/List Icon
                IconButton(
                    onClick = { historyExpanded = !historyExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = "Expand History",
                        tint = if (historyExpanded) Color(0xFF3B82F6) else Color.Gray
                    )
                }
                if (historyExpanded) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = history.reversed().joinToString(",") { it.number.toString() }
                            clipboardManager.setText(AnnotatedString(text))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color.Gray)
                    }
                }

                // Current Bankroll Balance at the end of the "LAST 20" strip
                Spacer(Modifier.width(8.dp))
                val currentBankroll = remember(gameplayRecords, timeTravelIndex, activeGameplaySession) {
                    if (timeTravelIndex != null) {
                        gameplayRecords.find { it.spinIndex == timeTravelIndex }?.bankroll
                            ?: gameplayRecords.lastOrNull()?.bankroll
                            ?: (activeGameplaySession?.startingBankroll ?: 1000.0)
                    } else {
                        gameplayRecords.lastOrNull()?.bankroll ?: (activeGameplaySession?.startingBankroll ?: 1000.0)
                    }
                }
                val startingBankroll = activeGameplaySession?.startingBankroll ?: 1000.0
                val bankrollColor = when {
                    currentBankroll > startingBankroll -> Color(0xFF4ADE80)
                    currentBankroll < startingBankroll -> Color(0xFFF87171)
                    else -> Color(0xFF38BDF8)
                }
                val bankrollText = if (currentBankroll % 1.0 == 0.0) {
                    String.format("%,d", currentBankroll.toLong())
                } else {
                    String.format("%,.2f", currentBankroll)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                        .border(1.dp, bankrollColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Bankroll: ",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "$$bankrollText",
                        color = bankrollColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ================= MAIN DISPLAY AREA (Felt vs Bet Trend vs MA vs Triggers Dashboard) =================
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            tabSaveableStateHolder.SaveableStateProvider(activeMainTab) {
                when (activeMainTab) {
                    "Bet Trend" -> {
                        BetTrendDashboard(
                            spins = effectiveChronologicalSpins,
                            fullSpins = chronologicalSpins,
                            timeTravelIndex = timeTravelIndex,
                            onTimeTravelChange = { idx ->
                                performActionWithDirtyCheck { viewModel.setTimeTravelIndex(idx) }
                            },
                            tableType = uiState.tableType,
                            heatmapStats = stats,
                            hotOverrides = uiState.hotOverrides,
                            coldOverrides = uiState.coldOverrides,
                            activeFamily = trendActiveFamily,
                            onActiveFamilyChange = { viewModel.setTrendActiveFamily(it) },
                            lookbackWindow = trendLookbackWindow,
                            onLookbackWindowChange = { viewModel.setTrendLookbackWindow(it) },
                            selectedProgression = trendSelectedProgression,
                            onSelectedProgressionChange = { viewModel.setTrendSelectedProgression(it) },
                            coldLimit = trendColdLimit,
                            onColdLimitChange = { viewModel.setTrendColdLimit(it) },
                            primaryCriterion = trendPrimaryCriterion,
                            onPrimaryCriterionChange = { viewModel.setTrendPrimaryCriterion(it) },
                            secondaryCriterion = trendSecondaryCriterion,
                            onSecondaryCriterionChange = { viewModel.setTrendSecondaryCriterion(it) },
                            coldRule = trendColdRule,
                            onColdRuleChange = { viewModel.setTrendColdRule(it) },
                            equalizePayout = trendEqualizePayout,
                            onEqualizePayoutChange = { viewModel.setTrendEqualizePayout(it) },
                            pushOrProfit = trendPushOrProfit,
                            onPushOrProfitChange = { viewModel.setTrendPushOrProfit(it) },
                            riskCap = trendRiskCap,
                            onRiskCapChange = { viewModel.setTrendRiskCap(it) },
                            underwaterFilter = trendUnderwaterFilter,
                            onUnderwaterFilterChange = { viewModel.setTrendUnderwaterFilter(it) },
                            transitionRule = trendTransitionRule,
                            onTransitionRuleChange = { viewModel.setTrendTransitionRule(it) },
                            switchSensitivity = trendSwitchSensitivity,
                            onSwitchSensitivityChange = { viewModel.setTrendSwitchSensitivity(it) },
                            showIdealOverlay = trendShowIdealOverlay,
                            onShowIdealOverlayChange = { viewModel.setTrendShowIdealOverlay(it) },
                            startMode = trendStartMode,
                            onStartModeChange = { viewModel.setTrendStartMode(it) },
                            heatMode = mainFeltHeatMode,
                            outsideHeatMap = outsideHeatMap,
                            equityChartHeight = trendEquityChartHeight.dp,
                            onEquityChartHeightChange = { viewModel.setTrendEquityChartHeight(it.value) },
                            ledgerTableHeight = trendLedgerTableHeight.dp,
                            onLedgerTableHeightChange = { viewModel.setTrendLedgerTableHeight(it.value) },
                            leftScrollState = trendLeftScrollState,
                            rightScrollState = trendRightScrollState,
                            startBankroll = feltStartBankroll,
                            baseInsideBet = feltBaseInsideBet,
                            baseOutsideBet = feltBaseOutsideBet,
                            onApplyBetsToFelt = { newChips ->
                                performActionWithDirtyCheck {
                                    viewModel.applyTrendBets(newChips)
                                    activeMainTab = "Felt"
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "MA" -> {
                        MovingAverageDashboard(
                            spins = effectiveChronologicalSpins,
                            maType = maType,
                            onMaTypeChange = { viewModel.setMaType(it) },
                            period = maPeriod,
                            onPeriodChange = { viewModel.setMaPeriod(it) },
                            activeCategory = maActiveCategory,
                            onActiveCategoryChange = { viewModel.setMaActiveCategory(it) },
                            almaOffset = maAlmaOffset,
                            onAlmaOffsetChange = { viewModel.setMaAlmaOffset(it) },
                            almaSigma = maAlmaSigma,
                            onAlmaSigmaChange = { viewModel.setMaAlmaSigma(it) },
                            kamaFast = maKamaFast,
                            onKamaFastChange = { viewModel.setMaKamaFast(it) },
                            kamaSlow = maKamaSlow,
                            onKamaSlowChange = { viewModel.setMaKamaSlow(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "Triggers" -> {
                        TriggersDashboard(
                            spins = effectiveChronologicalSpins,
                            thresholds = triggerThresholds,
                            onThresholdChange = { updated ->
                                viewModel.setTriggerThresholds(
                                    rb = updated.redBlack,
                                    hl = updated.highLow,
                                    oe = updated.oddEven,
                                    dozens = updated.dozens,
                                    columns = updated.columns,
                                    coldRb = updated.coldRedBlack,
                                    coldHl = updated.coldHighLow,
                                    coldOe = updated.coldOddEven,
                                    coldDozens = updated.coldDozens,
                                    coldColumns = updated.coldColumns
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        val isScrolledDown = feltScrollState.value > 80
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A))
                        ) {
                    // ================= SCROLLABLE FELT CONTENT (BOARD, CHARTS, CONFIG, LEDGER) =================
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val boardHeight = maxHeight
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(feltScrollState)
                        ) {
                            // ================= BOARD AREA =================
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(boardHeight)
                                    .background(Color(0xFF0F172A))
                                    .clipToBounds()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Unified SharedRouletteFelt and RouletteWheel with 2-finger zoom/pan gestures
                                    // 1-finger vertical drags pass through to scroll the page
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clipToBounds()
                                            .pointerInput(Unit) {
                                                awaitEachGesture {
                                                    awaitFirstDown(requireUnconsumed = false)
                                                    do {
                                                        val event = awaitPointerEvent()
                                                        if (event.changes.size >= 2) {
                                                            val zoomChange = event.calculateZoom()
                                                            val panChange = event.calculatePan()
                                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                                scale = (scale * zoomChange).coerceIn(0.5f, 4f)
                                                                offset += panChange
                                                                event.changes.forEach { it.consume() }
                                                            }
                                                        }
                                                    } while (event.changes.any { it.pressed })
                                                }
                                            }
                                            .graphicsLayer(
                                                scaleX = scale,
                                                scaleY = scale,
                                                translationX = offset.x,
                                                translationY = offset.y
                                            )
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            SharedRouletteFelt(
                                                tableType = uiState.tableType,
                                                heatmapStats = stats,
                                                hotOverrides = uiState.hotOverrides,
                                                coldOverrides = uiState.coldOverrides,
                                                chips = uiState.chips,
                                                chipLabels = emptyMap(),
                                                coldBannedNumbers = historicalTrendResult?.coldBannedNumbers?.toSet() ?: emptySet(),
                                                lastSpunNumber = effectiveChronologicalSpins.lastOrNull(),
                                                heatMode = mainFeltHeatMode,
                                                numberWeights = mainFeltWeights,
                                                outsideHeatMap = outsideHeatMap,
                                                isInteractive = true,
                                                onTapPlaceChip = { x, y, betId ->
                                                    viewModel.placeChip(x, y, betId)
                                                },
                                                onChipMoved = { id, x, y, betId ->
                                                    viewModel.updateChipPosition(id, x, y, betId)
                                                },
                                                onChipRemoved = { id ->
                                                    viewModel.removeChip(id)
                                                },
                                                modifier = Modifier.weight(2.75f).fillMaxHeight()
                                            )
                                            
                                            val optimalMidpoint = com.roulettetactician.logic.WheelLayouts.findOptimalMidpoint(
                                                targets = uiState.hotOverrides.filterNotNull(),
                                                type = uiState.tableType
                                            )
                                            
                                            RouletteWheel(
                                                midpoints = listOfNotNull(optimalMidpoint),
                                                hotNumbers = uiState.hotOverrides.filterNotNull(),
                                                tableType = uiState.tableType,
                                                onTriangleClick = { midpoint ->
                                                    val neighbors = com.roulettetactician.logic.WheelLayouts.getNeighbors(midpoint, uiState.tableType)
                                                    if (neighbors.isNotEmpty()) {
                                                        viewModel.placeStrategyBets(neighbors)
                                                    }
                                                },
                                                modifier = Modifier.weight(1.05f).fillMaxHeight()
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(6.dp))

                                    // Chip Denominations Selector (2 columns x 3 rows) + Clear Chips Button
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val chipRows = listOf(
                                            listOf(1, 5),
                                            listOf(25, 100),
                                            listOf(500, 1000)
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            chipRows.forEach { rowValues ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    rowValues.forEach { value ->
                                                        val isSelected = uiState.selectedChipValue == value
                                                        Box(
                                                            modifier = Modifier
                                                                .size(34.dp)
                                                                .clickable { viewModel.selectChipValue(value) }
                                                                .then(
                                                                    if (isSelected) Modifier.border(2.dp, Color(0xFF38BDF8), CircleShape)
                                                                    else Modifier
                                                                ),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Chip(
                                                                value = value,
                                                                size = 28.dp,
                                                                modifier = Modifier.graphicsLayer {
                                                                    val s = if (isSelected) 1.1f else 0.95f
                                                                    scaleX = s
                                                                    scaleY = s
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        // Clear Chips Button
                                        Button(
                                            onClick = { viewModel.clearChips() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D)),
                                            shape = RoundedCornerShape(4.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFFCA5A5),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                            Text(
                                                "Clear Chips",
                                                color = Color.White,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // ================= GAMEPLAY EQUITY PROGRESS CHART =================
                            GameplayEquityCard(
                                records = gameplayRecords,
                                startBankroll = activeGameplaySession?.startingBankroll ?: 1000.0,
                                scrubberSpinIndex = timeTravelIndex,
                                onScrubberChange = { idx ->
                                    performActionWithDirtyCheck { viewModel.setTimeTravelIndex(idx) }
                                },
                                chartHeight = 160.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .onGloballyPositioned { coordinates ->
                                        equityCardTopOffset = (coordinates.positionInParent().y + feltScrollState.value).toInt()
                                    }
                            )

                            Spacer(Modifier.height(8.dp))

                            // ================= GAMEPLAY BET LEDGER =================
                            GameplayLedgerCard(
                                records = gameplayRecords,
                                scrubberSpinIndex = timeTravelIndex,
                                onSelectSpin = { idx ->
                                    performActionWithDirtyCheck { viewModel.setTimeTravelIndex(idx) }
                                },
                                initialHeight = 180.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                            )

                            Spacer(Modifier.height(12.dp))

                            // Return to Table Footer Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch { feltScrollState.animateScrollTo(0) }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("▲ Back to Table", fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // ================= QUICK ACTION & CHIP BAR =================
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total Bet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.width(2.dp))
                            Text("${uiState.totalBet}", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        
                        Spacer(Modifier.width(8.dp))

                        // +Base Unit Button
                        Button(
                            onClick = { viewModel.increaseAllPlacedChipsByBaseUnit() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("+Base Unit", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Spacer(Modifier.width(4.dp))

                        // -Base Unit Button
                        Button(
                            onClick = { viewModel.decreaseAllPlacedChipsByBaseUnit() },
                            enabled = uiState.chips.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC2410C),
                                disabledContainerColor = Color(0xFF334155).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "-Base Unit",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.chips.isNotEmpty()) Color.White else Color(0xFF64748B)
                            )
                        }

                        Spacer(Modifier.width(6.dp))

                        // +Prev Bets Button (Quickly adds bets from the previous spin)
                        val hasPrevBets = remember(gameplayRecords, timeTravelIndex, uiState.chips) {
                            viewModel.getPreviousSpinBets().isNotEmpty()
                        }
                        Button(
                            onClick = { viewModel.addPreviousSpinBets() },
                            enabled = hasPrevBets,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0D9488),
                                disabledContainerColor = Color(0xFF334155).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "+Prev Bets",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasPrevBets) Color.White else Color(0xFF64748B)
                            )
                        }

                        // Undo / Redo Buttons
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                                .padding(1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.undoChipAction() },
                                enabled = canUndo,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Undo,
                                    contentDescription = "Undo",
                                    tint = if (canUndo) Color(0xFF38BDF8) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Box(modifier = Modifier.width(1.dp).height(16.dp).background(Color(0xFF334155)))
                            IconButton(
                                onClick = { viewModel.redoChipAction() },
                                enabled = canRedo,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Redo,
                                    contentDescription = "Redo",
                                    tint = if (canRedo) Color(0xFF38BDF8) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(6.dp))

                        // Chip Removal Mode Toggle: [1 Chip | Stack]
                        Row(
                            modifier = Modifier
                                .height(28.dp)
                                .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF334155), RoundedCornerShape(4.dp))
                                .padding(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Drop:", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp, end = 2.dp))
                            val isSingle = chipRemovalMode == ChipRemovalMode.SINGLE_CHIP
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(if (isSingle) Color(0xFF2563EB) else Color.Transparent, RoundedCornerShape(3.dp))
                                    .clickable { viewModel.setChipRemovalMode(ChipRemovalMode.SINGLE_CHIP) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("1 Chip", color = if (isSingle) Color.White else Color.Gray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(if (!isSingle) Color(0xFF2563EB) else Color.Transparent, RoundedCornerShape(3.dp))
                                    .clickable { viewModel.setChipRemovalMode(ChipRemovalMode.ENTIRE_STACK) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Stack", color = if (!isSingle) Color.White else Color.Gray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Replay / Unsaved Spin Status & Actions (placed in space to the right of Drop Mode selector)
                        if (activeEditIndex != null && (timeTravelIndex != null || hasUnsavedSpinEdits)) {
                            Spacer(Modifier.width(8.dp))
                            val spinLabel = if (timeTravelIndex != null) "Replay Spin #$timeTravelIndex" else "Last Spin #$activeEditIndex"
                            Row(
                                modifier = Modifier
                                    .height(28.dp)
                                    .background(
                                        if (hasUnsavedSpinEdits) Color(0xFF7F1D1D) else Color(0xFF0F172A),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (hasUnsavedSpinEdits) Color(0xFFEF4444) else Color(0xFF334155),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasUnsavedSpinEdits) "⚠ $spinLabel: Bets Modified" else "$spinLabel: Recorded Bets",
                                    color = if (hasUnsavedSpinEdits) Color(0xFFFCA5A5) else Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (hasUnsavedSpinEdits) {
                                    Spacer(Modifier.width(6.dp))
                                    Button(
                                        onClick = { viewModel.saveSpinEdits(activeEditIndex) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        modifier = Modifier.height(22.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Save", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.discardSpinEdits(activeEditIndex) },
                                        modifier = Modifier.height(22.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text("Revert", fontSize = 9.5.sp, color = Color(0xFFFCA5A5))
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        // Quick Jump Button between Table and Progress Graph
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    if (isScrolledDown) {
                                        feltScrollState.animateScrollTo(0)
                                    } else {
                                        feltScrollState.animateScrollTo(equityCardTopOffset.coerceAtLeast(0))
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScrolledDown) Color(0xFF0284C7) else Color(0xFF4F46E5)
                            ),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = if (isScrolledDown) "▲ Table" else "Graph ▼",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
}
}

@Composable
private fun PulsingTriggerDot(modifier: Modifier = Modifier) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "tabPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "tabPulseAlpha"
    )
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFFEF4444), androidx.compose.foundation.shape.CircleShape)
    )
}

