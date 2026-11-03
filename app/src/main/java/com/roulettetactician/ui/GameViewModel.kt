package com.roulettetactician.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roulettetactician.data.SpinDao
import com.roulettetactician.data.SpinResult
import com.roulettetactician.data.SessionDao
import com.roulettetactician.data.SessionEntity
import com.roulettetactician.ui.model.TableType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import com.roulettetactician.logic.HeatmapCalculator
import com.roulettetactician.logic.SessionDataValidator
import com.roulettetactician.ui.model.GameState
import com.roulettetactician.ui.model.PlacedChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import com.roulettetactician.logic.BetTrendCalculator
import com.roulettetactician.logic.MovingAverageCalculator
import com.roulettetactician.ui.components.MACategoryTab
import com.roulettetactician.ui.model.TableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.roulettetactician.data.BackupDataBundle
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import androidx.documentfile.provider.DocumentFile
import javax.inject.Inject

data class SessionConfigEvent(
    val trendFamily: BetTrendCalculator.BetFamily,
    val trendLookback: Int,
    val trendProgression: BetTrendCalculator.ProgressionType,
    val trendColdLimit: Int,
    val maType: MovingAverageCalculator.MAType,
    val maPeriod: Int,
    val maCategory: MACategoryTab,
    val maAlmaOffset: Double,
    val maAlmaSigma: Double,
    val maKamaFast: Int,
    val maKamaSlow: Int,
    val triggerRB: Int,
    val triggerHL: Int,
    val triggerOE: Int,
    val triggerDozens: Int,
    val triggerColumns: Int,
    val trendPrimaryCriterion: BetTrendCalculator.OptimizationCriterion = BetTrendCalculator.OptimizationCriterion.PNL,
    val trendSecondaryCriterion: BetTrendCalculator.OptimizationCriterion = BetTrendCalculator.OptimizationCriterion.MAX_DD,
    val trendColdRule: BetTrendCalculator.ColdRule = BetTrendCalculator.ColdRule.STRICT,
    val trendEqualizePayout: Boolean = false,
    val trendPushOrProfit: Boolean = false,
    val trendRiskCap: Boolean = true,
    val trendUnderwaterFilter: Boolean = true,
    val trendTransitionRule: BetTrendCalculator.SwitchTransitionRule = BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE,
    val trendSwitchSensitivity: BetTrendCalculator.SwitchSensitivity = BetTrendCalculator.SwitchSensitivity.MODERATE,
    val trendShowIdealOverlay: Boolean = false,
    val trendStartMode: BetTrendCalculator.BettingStartMode = BetTrendCalculator.BettingStartMode.FROM_SPIN_1
)

/** State of the camera-based Spin Log Scanner (Spin History > Scan Board). */
sealed interface SpinScanState {
    data object Idle : SpinScanState
    data object Processing : SpinScanState
    data class Review(
        val layout: String,
        /** Readable scanned spins, oldest-first. */
        val scannedOldestFirst: List<Int>,
        /** How many leading scanned spins already exist at the tail of history. */
        val overlap: Int,
        /** Spins that will be appended, oldest-first. */
        val newSpins: List<Int>,
        /** Older entries discarded because glare made a cell unreadable. */
        val droppedOlder: Int,
        /** History non-empty but no overlap found: spins between scans may be missing. */
        val possibleGap: Boolean
    ) : SpinScanState
    data class Error(val message: String) : SpinScanState
}

@HiltViewModel
class GameViewModel @Inject constructor(
    private val spinDao: SpinDao,
    private val sessionDao: SessionDao,
    private val gameplaySessionDao: com.roulettetactician.data.GameplaySessionDao,
    private val prefs: android.content.SharedPreferences,
    private val spinLogExtractor: com.roulettetactician.data.SpinLogExtractor,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _tableRestoreEvents = MutableSharedFlow<TableState>(replay = 1)
    val tableRestoreEvents: SharedFlow<TableState> = _tableRestoreEvents

    private val _sessionConfigEvents = MutableSharedFlow<SessionConfigEvent>(replay = 1)
    val sessionConfigEvents: SharedFlow<SessionConfigEvent> = _sessionConfigEvents

    val savedSessions: StateFlow<List<SessionEntity>> = sessionDao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Persistent Spin History from Room
    val spinHistory: StateFlow<List<SpinResult>> = spinDao.getAllSpins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI State (Non-persistent for now, or could be saved to Datastore)
    private val _uiState = MutableStateFlow(GameState())
    val uiState: StateFlow<GameState> = _uiState

    // --- Persistent MA & Triggers Configurations ---
    private val _maType = MutableStateFlow(
        try {
            MovingAverageCalculator.MAType.valueOf(prefs.getString("ma_type", "SMA") ?: "SMA")
        } catch (e: Exception) {
            MovingAverageCalculator.MAType.SMA
        }
    )
    val maType: StateFlow<MovingAverageCalculator.MAType> = _maType

    private val _maPeriod = MutableStateFlow(prefs.getInt("ma_period", 10))
    val maPeriod: StateFlow<Int> = _maPeriod

    private val _maActiveCategory = MutableStateFlow(
        try {
            MACategoryTab.valueOf(prefs.getString("ma_category", "ALL") ?: "ALL")
        } catch (e: Exception) {
            MACategoryTab.ALL
        }
    )
    val maActiveCategory: StateFlow<MACategoryTab> = _maActiveCategory

    private val _maAlmaOffset = MutableStateFlow(prefs.getFloat("ma_alma_offset", 0.85f).toDouble())
    val maAlmaOffset: StateFlow<Double> = _maAlmaOffset

    private val _maAlmaSigma = MutableStateFlow(prefs.getFloat("ma_alma_sigma", 6.0f).toDouble())
    val maAlmaSigma: StateFlow<Double> = _maAlmaSigma

    private val _maKamaFast = MutableStateFlow(prefs.getInt("ma_kama_fast", 2))
    val maKamaFast: StateFlow<Int> = _maKamaFast

    private val _maKamaSlow = MutableStateFlow(prefs.getInt("ma_kama_slow", 30))
    val maKamaSlow: StateFlow<Int> = _maKamaSlow

    private val _triggerThresholdRB = MutableStateFlow(prefs.getInt("trigger_rb", 4))
    val triggerThresholdRB: StateFlow<Int> = _triggerThresholdRB

    private val _triggerThresholdHL = MutableStateFlow(prefs.getInt("trigger_hl", 4))
    val triggerThresholdHL: StateFlow<Int> = _triggerThresholdHL

    private val _triggerThresholdOE = MutableStateFlow(prefs.getInt("trigger_oe", 4))
    val triggerThresholdOE: StateFlow<Int> = _triggerThresholdOE

    private val _triggerThresholdDozens = MutableStateFlow(prefs.getInt("trigger_dozens", 6))
    val triggerThresholdDozens: StateFlow<Int> = _triggerThresholdDozens

    private val _triggerThresholdColumns = MutableStateFlow(prefs.getInt("trigger_columns", 6))
    val triggerThresholdColumns: StateFlow<Int> = _triggerThresholdColumns

    private val _triggerColdThresholdRB = MutableStateFlow(prefs.getInt("trigger_cold_rb", 5))
    val triggerColdThresholdRB: StateFlow<Int> = _triggerColdThresholdRB

    private val _triggerColdThresholdHL = MutableStateFlow(prefs.getInt("trigger_cold_hl", 5))
    val triggerColdThresholdHL: StateFlow<Int> = _triggerColdThresholdHL

    private val _triggerColdThresholdOE = MutableStateFlow(prefs.getInt("trigger_cold_oe", 5))
    val triggerColdThresholdOE: StateFlow<Int> = _triggerColdThresholdOE

    private val _triggerColdThresholdDozens = MutableStateFlow(prefs.getInt("trigger_cold_dozens", 8))
    val triggerColdThresholdDozens: StateFlow<Int> = _triggerColdThresholdDozens

    private val _triggerColdThresholdColumns = MutableStateFlow(prefs.getInt("trigger_cold_columns", 8))
    val triggerColdThresholdColumns: StateFlow<Int> = _triggerColdThresholdColumns

    // --- Persistent Bet Trend Configurations ---
    private val _trendActiveFamily = MutableStateFlow(
        try {
            BetTrendCalculator.BetFamily.valueOf(prefs.getString("trend_family", "COMBINED") ?: "COMBINED")
        } catch (e: Exception) {
            BetTrendCalculator.BetFamily.COMBINED
        }
    )
    val trendActiveFamily: StateFlow<BetTrendCalculator.BetFamily> = _trendActiveFamily

    private val _trendLookbackWindow = MutableStateFlow(prefs.getInt("trend_lookback", 37))
    val trendLookbackWindow: StateFlow<Int> = _trendLookbackWindow

    private val _trendSelectedProgression = MutableStateFlow(
        try {
            BetTrendCalculator.ProgressionType.valueOf(prefs.getString("trend_progression", "AUTO") ?: "AUTO")
        } catch (e: Exception) {
            BetTrendCalculator.ProgressionType.AUTO
        }
    )
    val trendSelectedProgression: StateFlow<BetTrendCalculator.ProgressionType> = _trendSelectedProgression

    private val _trendColdLimit = MutableStateFlow(prefs.getInt("trend_cold_limit", 5))
    val trendColdLimit: StateFlow<Int> = _trendColdLimit

    private val _trendPrimaryCriterion = MutableStateFlow(
        try {
            BetTrendCalculator.OptimizationCriterion.valueOf(prefs.getString("trend_primary_criterion", "PNL") ?: "PNL")
        } catch (e: Exception) {
            BetTrendCalculator.OptimizationCriterion.PNL
        }
    )
    val trendPrimaryCriterion: StateFlow<BetTrendCalculator.OptimizationCriterion> = _trendPrimaryCriterion

    private val _trendSecondaryCriterion = MutableStateFlow(
        try {
            BetTrendCalculator.OptimizationCriterion.valueOf(prefs.getString("trend_secondary_criterion", "MAX_DD") ?: "MAX_DD")
        } catch (e: Exception) {
            BetTrendCalculator.OptimizationCriterion.MAX_DD
        }
    )
    val trendSecondaryCriterion: StateFlow<BetTrendCalculator.OptimizationCriterion> = _trendSecondaryCriterion

    private val _trendColdRule = MutableStateFlow(
        try {
            BetTrendCalculator.ColdRule.valueOf(prefs.getString("trend_cold_rule", "STRICT") ?: "STRICT")
        } catch (e: Exception) {
            BetTrendCalculator.ColdRule.STRICT
        }
    )
    val trendColdRule: StateFlow<BetTrendCalculator.ColdRule> = _trendColdRule

    private val _trendEqualizePayout = MutableStateFlow(prefs.getBoolean("trend_equalize_payout", false))
    val trendEqualizePayout: StateFlow<Boolean> = _trendEqualizePayout

    private val _trendPushOrProfit = MutableStateFlow(prefs.getBoolean("trend_push_or_profit", false))
    val trendPushOrProfit: StateFlow<Boolean> = _trendPushOrProfit

    private val _trendRiskCap = MutableStateFlow(prefs.getBoolean("trend_risk_cap", true))
    val trendRiskCap: StateFlow<Boolean> = _trendRiskCap

    private val _trendUnderwaterFilter = MutableStateFlow(prefs.getBoolean("trend_underwater_filter", true))
    val trendUnderwaterFilter: StateFlow<Boolean> = _trendUnderwaterFilter

    private val _trendTransitionRule = MutableStateFlow(
        try {
            BetTrendCalculator.SwitchTransitionRule.valueOf(
                prefs.getString("trend_transition_rule", BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE.name)
                    ?: BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE.name
            )
        } catch (_: Exception) {
            BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE
        }
    )
    val trendTransitionRule: StateFlow<BetTrendCalculator.SwitchTransitionRule> = _trendTransitionRule

    private val _trendSwitchSensitivity = MutableStateFlow(
        try {
            BetTrendCalculator.SwitchSensitivity.valueOf(
                prefs.getString("trend_switch_sensitivity", BetTrendCalculator.SwitchSensitivity.MODERATE.name)
                    ?: BetTrendCalculator.SwitchSensitivity.MODERATE.name
            )
        } catch (_: Exception) {
            BetTrendCalculator.SwitchSensitivity.MODERATE
        }
    )
    val trendSwitchSensitivity: StateFlow<BetTrendCalculator.SwitchSensitivity> = _trendSwitchSensitivity

    private val _trendShowIdealOverlay = MutableStateFlow(prefs.getBoolean("trend_show_ideal_overlay", false))
    val trendShowIdealOverlay: StateFlow<Boolean> = _trendShowIdealOverlay

    private val _trendStartMode = MutableStateFlow(
        try {
            BetTrendCalculator.BettingStartMode.valueOf(
                prefs.getString("trend_start_mode", BetTrendCalculator.BettingStartMode.FROM_SPIN_1.name)
                    ?: BetTrendCalculator.BettingStartMode.FROM_SPIN_1.name
            )
        } catch (_: Exception) {
            BetTrendCalculator.BettingStartMode.FROM_SPIN_1
        }
    )
    val trendStartMode: StateFlow<BetTrendCalculator.BettingStartMode> = _trendStartMode

    private val _trendEquityChartHeightDp = MutableStateFlow(prefs.getFloat("trend_equity_chart_height", 145f))
    val trendEquityChartHeightDp: StateFlow<Float> = _trendEquityChartHeightDp

    private val _trendLedgerTableHeightDp = MutableStateFlow(prefs.getFloat("trend_ledger_table_height", 210f))
    val trendLedgerTableHeightDp: StateFlow<Float> = _trendLedgerTableHeightDp

    // Live un-scrubbed chips held on felt for the upcoming spin
    private val _liveChips = MutableStateFlow<List<PlacedChip>>(emptyList())

    // Most recent spin bets placed in live mode
    private val _lastSpinLiveChips = MutableStateFlow<List<PlacedChip>>(emptyList())
    val lastSpinLiveChips: StateFlow<List<PlacedChip>> = _lastSpinLiveChips

    // Active Roulette/Spin session name that anchors all associated gameplay sessions
    private val _activeRouletteSessionName = MutableStateFlow<String>(_uiState.value.wheelName.ifBlank { "New Session..." })
    val activeRouletteSessionName: StateFlow<String> = _activeRouletteSessionName

    // Game Play Recorder States (Scoped strictly to the loaded Roulette/Spin session)
    @OptIn(ExperimentalCoroutinesApi::class)
    val gameplaySessions: StateFlow<List<com.roulettetactician.data.GameplaySessionEntity>> = _activeRouletteSessionName
        .flatMapLatest { sessionName ->
            gameplaySessionDao.getSessionsForRouletteSession(sessionName)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counts of associated gameplay sessions grouped by spin session name
    val gameplaySessionCounts: StateFlow<Map<String, Int>> = gameplaySessionDao.getAllSessions()
        .map { list -> list.groupBy { it.rouletteSessionName }.mapValues { it.value.size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _activeGameplaySession = MutableStateFlow<com.roulettetactician.data.GameplaySessionEntity?>(null)
    val activeGameplaySession: StateFlow<com.roulettetactician.data.GameplaySessionEntity?> = _activeGameplaySession

    private val _gameplayRecords = MutableStateFlow<List<com.roulettetactician.logic.GameplaySpinRecord>>(emptyList())
    val gameplayRecords: StateFlow<List<com.roulettetactician.logic.GameplaySpinRecord>> = _gameplayRecords

    private val _chipRemovalMode = MutableStateFlow(com.roulettetactician.logic.ChipRemovalMode.SINGLE_CHIP)
    val chipRemovalMode: StateFlow<com.roulettetactician.logic.ChipRemovalMode> = _chipRemovalMode

    private val _hasUnsavedSpinEdits = MutableStateFlow(false)
    val hasUnsavedSpinEdits: StateFlow<Boolean> = _hasUnsavedSpinEdits

    private val _pendingEditSpinIndex = MutableStateFlow<Int?>(null)
    val pendingEditSpinIndex: StateFlow<Int?> = _pendingEditSpinIndex

    // --- Undo / Redo for Chip Placements & Actions ---
    private val _chipUndoStack = MutableStateFlow<List<List<PlacedChip>>>(emptyList())
    val canUndoChipAction: StateFlow<Boolean> = _chipUndoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _chipRedoStack = MutableStateFlow<List<List<PlacedChip>>>(emptyList())
    val canRedoChipAction: StateFlow<Boolean> = _chipRedoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun markChipsModified() {
        val timeIdx = _timeTravelIndex.value
        if (timeIdx != null) {
            _hasUnsavedSpinEdits.value = true
            _pendingEditSpinIndex.value = timeIdx
        } else if (spinHistory.value.isNotEmpty()) {
            _hasUnsavedSpinEdits.value = true
            _pendingEditSpinIndex.value = spinHistory.value.size
            _liveChips.value = _uiState.value.chips
        } else {
            _liveChips.value = _uiState.value.chips
        }
    }

    private fun recordChipActionForUndo() {
        val current = _uiState.value.chips
        _chipUndoStack.update { stack ->
            (stack + listOf(current)).takeLast(30)
        }
        _chipRedoStack.value = emptyList()
    }

    fun undoChipAction() {
        val undoStack = _chipUndoStack.value
        if (undoStack.isEmpty()) return

        val previousState = undoStack.last()
        val newUndoStack = undoStack.dropLast(1)
        val currentState = _uiState.value.chips

        _chipUndoStack.value = newUndoStack
        _chipRedoStack.update { it + listOf(currentState) }

        _uiState.update { state ->
            state.copy(
                chips = previousState,
                totalBet = previousState.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    fun redoChipAction() {
        val redoStack = _chipRedoStack.value
        if (redoStack.isEmpty()) return

        val nextState = redoStack.last()
        val newRedoStack = redoStack.dropLast(1)
        val currentState = _uiState.value.chips

        _chipRedoStack.value = newRedoStack
        _chipUndoStack.update { it + listOf(currentState) }

        _uiState.update { state ->
            state.copy(
                chips = nextState,
                totalBet = nextState.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    fun clearChipUndoRedo() {
        _chipUndoStack.value = emptyList()
        _chipRedoStack.value = emptyList()
    }

    // --- Undo / Redo for Spin History ---
    private data class SpinHistorySnapshot(
        val spinNumbers: List<Int>,
        val gameplayRecords: List<com.roulettetactician.logic.GameplaySpinRecord>
    )

    private val _spinUndoStack = MutableStateFlow<List<SpinHistorySnapshot>>(emptyList())
    val canUndoSpinAction: StateFlow<Boolean> = _spinUndoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _spinRedoStack = MutableStateFlow<List<SpinHistorySnapshot>>(emptyList())
    val canRedoSpinAction: StateFlow<Boolean> = _spinRedoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private fun recordSpinActionForUndo() {
        val currentNumbers = spinHistory.value.reversed().map { it.number }
        val currentRecords = _gameplayRecords.value
        _spinUndoStack.update { stack ->
            (stack + listOf(SpinHistorySnapshot(currentNumbers, currentRecords))).takeLast(30)
        }
        _spinRedoStack.value = emptyList()
    }

    fun undoSpinAction() {
        val undoStack = _spinUndoStack.value
        if (undoStack.isEmpty()) return

        val previousState = undoStack.last()
        val newUndoStack = undoStack.dropLast(1)
        val currentNumbers = spinHistory.value.reversed().map { it.number }
        val currentRecords = _gameplayRecords.value
        val currentState = SpinHistorySnapshot(currentNumbers, currentRecords)

        _spinUndoStack.value = newUndoStack
        _spinRedoStack.update { it + listOf(currentState) }

        _timeTravelIndex.value = null
        viewModelScope.launch {
            restoreSpinsFromNumbers(previousState.spinNumbers)
            restoreGameplayRecords(previousState.gameplayRecords)
            autoSyncBackupToFolder()
        }
    }

    fun redoSpinAction() {
        val redoStack = _spinRedoStack.value
        if (redoStack.isEmpty()) return

        val nextState = redoStack.last()
        val newRedoStack = redoStack.dropLast(1)
        val currentNumbers = spinHistory.value.reversed().map { it.number }
        val currentRecords = _gameplayRecords.value
        val currentState = SpinHistorySnapshot(currentNumbers, currentRecords)

        _spinRedoStack.value = newRedoStack
        _spinUndoStack.update { it + listOf(currentState) }

        _timeTravelIndex.value = null
        viewModelScope.launch {
            restoreSpinsFromNumbers(nextState.spinNumbers)
            restoreGameplayRecords(nextState.gameplayRecords)
            autoSyncBackupToFolder()
        }
    }

    fun clearSpinUndoRedo() {
        _spinUndoStack.value = emptyList()
        _spinRedoStack.value = emptyList()
    }

    private suspend fun restoreSpinsFromNumbers(numbers: List<Int>) {
        val baseTime = System.currentTimeMillis() - (numbers.size * 1000L)
        val spinEntities = numbers.mapIndexed { index, n ->
            SpinResult(
                number = n,
                timestamp = baseTime + (index * 1000L)
            )
        }
        spinDao.replaceAllSpins(spinEntities)
    }

    private suspend fun restoreGameplayRecords(records: List<com.roulettetactician.logic.GameplaySpinRecord>) {
        _gameplayRecords.value = records
        val activeSession = _activeGameplaySession.value
        if (activeSession != null) {
            val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(records)
            gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
        }
    }

    private suspend fun shiftGameplayRecordsOnInsert(insertIdx: Int, newNumber: Int) {
        val currentRecords = _gameplayRecords.value
        if (currentRecords.isEmpty()) return
        val activeSession = _activeGameplaySession.value ?: return
        val startBank = activeSession.startingBankroll
        val isUS = _uiState.value.tableType == TableType.US

        val newSpinIndex = insertIdx + 1
        val updated = mutableListOf<com.roulettetactician.logic.GameplaySpinRecord>()
        for (rec in currentRecords) {
            if (rec.spinIndex < newSpinIndex) {
                updated.add(rec)
            } else {
                updated.add(rec.copy(spinIndex = rec.spinIndex + 1))
            }
        }
        updated.add(
            com.roulettetactician.logic.GameplaySpinRecord(
                spinIndex = newSpinIndex,
                spunNumber = newNumber,
                chips = emptyList(),
                totalStake = 0.0,
                totalPayout = 0.0,
                netProfit = 0.0,
                bankroll = startBank
            )
        )
        updated.sortBy { it.spinIndex }
        val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(updated, startBank, isUS)
        _gameplayRecords.value = recalculated
        val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
        gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
    }

    private suspend fun shiftGameplayRecordsOnDelete(deleteIdx: Int) {
        val currentRecords = _gameplayRecords.value
        if (currentRecords.isEmpty()) return
        val activeSession = _activeGameplaySession.value ?: return
        val startBank = activeSession.startingBankroll
        val isUS = _uiState.value.tableType == TableType.US

        val deletedSpinIndex = deleteIdx + 1
        val updated = mutableListOf<com.roulettetactician.logic.GameplaySpinRecord>()
        for (rec in currentRecords) {
            if (rec.spinIndex < deletedSpinIndex) {
                updated.add(rec)
            } else if (rec.spinIndex > deletedSpinIndex) {
                updated.add(rec.copy(spinIndex = rec.spinIndex - 1))
            }
        }
        val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(updated, startBank, isUS)
        _gameplayRecords.value = recalculated
        val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
        gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
    }

    private suspend fun shiftGameplayRecordsOnBulkDiscard(discardCount: Int) {
        if (discardCount <= 0) return
        val currentRecords = _gameplayRecords.value
        if (currentRecords.isEmpty()) return
        val activeSession = _activeGameplaySession.value ?: return
        val startBank = activeSession.startingBankroll
        val isUS = _uiState.value.tableType == TableType.US

        val updated = currentRecords
            .filter { it.spinIndex > discardCount }
            .map { it.copy(spinIndex = it.spinIndex - discardCount) }
        val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(updated, startBank, isUS)
        _gameplayRecords.value = recalculated
        val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
        gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Disaster Recovery: Check if DB is empty and auto-restore from backup if available
            val folderUriStr = prefs.getString("backup_folder_uri", null)
            if (!folderUriStr.isNullOrBlank()) {
                val sessions = sessionDao.getAllSessionsList()
                val gameplays = gameplaySessionDao.getAllSessionsList()
                if (sessions.isEmpty() && gameplays.isEmpty()) {
                    try {
                        val treeUri = android.net.Uri.parse(folderUriStr)
                        val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                        val backupFile = documentDir?.findFile("roulette_tactician_backup.json")
                        if (backupFile != null && backupFile.canRead()) {
                            context.contentResolver.openInputStream(backupFile.uri)?.use { stream ->
                                val json = stream.bufferedReader().readText()
                                restoreFullBackupFromJson(json)
                            }
                        }
                    } catch (e: Exception) {
                        // Safe ignore if folder permission revoked or file missing
                    }
                }
            }
        }

        viewModelScope.launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _activeRouletteSessionName.flatMapLatest { sessionName ->
                gameplaySessionDao.getSessionsForRouletteSession(sessionName)
            }.collect { list ->
                val activeSessionName = _activeRouletteSessionName.value
                val currentActive = _activeGameplaySession.value
                if (list.isEmpty()) {
                    if (gameplaySessionDao.getCountForRouletteSession(activeSessionName) == 0) {
                        // Create default initial game session for the active loaded session if none exist
                        val initial = com.roulettetactician.data.GameplaySessionEntity(
                            rouletteSessionName = activeSessionName,
                            name = "Game 1",
                            timestamp = System.currentTimeMillis(),
                            startingBankroll = 1000.0,
                            insideBaseUnit = 1.0,
                            insideMinUnit = 1.0,
                            outsideBaseUnit = 5.0,
                            outsideMinUnit = 5.0,
                            recordsJson = "[]"
                        )
                        val newId = gameplaySessionDao.insertSession(initial)
                        val created = initial.copy(id = newId)
                        selectGameplaySession(created)
                    }
                } else {
                    // Prune duplicate empty "Game 1" sessions created by previous startup race conditions
                    val emptyGameOnes = list.filter { it.name == "Game 1" && (it.recordsJson.isBlank() || it.recordsJson == "[]") }
                    if (emptyGameOnes.size > 1) {
                        val duplicatesToDelete = emptyGameOnes.drop(1)
                        duplicatesToDelete.forEach { dup ->
                            gameplaySessionDao.deleteSession(dup)
                        }
                    }

                    val effectiveList = list.filterNot { item ->
                        emptyGameOnes.drop(1).any { it.id == item.id }
                    }.ifEmpty { list }

                    if (currentActive == null || currentActive.rouletteSessionName != activeSessionName || effectiveList.none { it.id == currentActive.id }) {
                        selectGameplaySession(effectiveList.first())
                    } else {
                        val match = effectiveList.find { it.id == currentActive.id }
                        if (match != null) {
                            _activeGameplaySession.value = match
                        }
                    }
                }
            }
        }
    }

    // Time Machine / Replay State: null = LIVE (following latest spin), 1..totalSpins = historical checkpoint
    private val _timeTravelIndex = MutableStateFlow<Int?>(null)
    val timeTravelIndex: StateFlow<Int?> = _timeTravelIndex

    // Computed Heatmap Stats observing history, uiState, and timeTravelIndex
    val heatmapStats: StateFlow<HeatmapCalculator.HeatmapStats> = combine(spinHistory, uiState, _timeTravelIndex) { history, state, timeIndex ->
        val chronologicalNumbers = history.reversed().map { it.number }
        val effectiveNumbers = if (timeIndex == null || timeIndex >= chronologicalNumbers.size) {
            history.map { it.number }
        } else {
            val clamped = timeIndex.coerceIn(1, chronologicalNumbers.size)
            chronologicalNumbers.take(clamped).reversed()
        }
        HeatmapCalculator.calculateStats(
            effectiveNumbers,
            state.analysisWindow,
            state.hotOverrides,
            state.coldOverrides
        )
    }.stateIn(
        viewModelScope, 
        SharingStarted.WhileSubscribed(5000), 
        HeatmapCalculator.HeatmapStats(emptyMap(), emptyMap(), 0)
    )

    // --- Time Machine / Replay Controls ---

    fun setTimeTravelIndex(index: Int?) {
        val total = spinHistory.value.size
        val target = if (index == null || total == 0 || index > total) null else index.coerceIn(1, total)
        _timeTravelIndex.value = target

        // Synchronize felt chips with the target spin or live
        if (target != null) {
            val record = _gameplayRecords.value.find { it.spinIndex == target }
            val spinChips = record?.chips ?: emptyList()
            _uiState.update { it.copy(chips = spinChips, totalBet = spinChips.sumOf { c -> c.value }) }
        } else {
            _uiState.update { it.copy(chips = _liveChips.value, totalBet = _liveChips.value.sumOf { c -> c.value }) }
        }
        _hasUnsavedSpinEdits.value = false
        _pendingEditSpinIndex.value = null
    }

    fun stepBack() {
        val total = spinHistory.value.size
        if (total <= 1) return
        val current = _timeTravelIndex.value ?: total
        if (current > 1) {
            setTimeTravelIndex(current - 1)
        }
    }

    fun stepForward() {
        val total = spinHistory.value.size
        if (total <= 1) return
        val current = _timeTravelIndex.value ?: total
        if (current + 1 > total) {
            setTimeTravelIndex(null) // Snap back to LIVE
        } else {
            setTimeTravelIndex(current + 1)
        }
    }

    fun returnToLive() {
        setTimeTravelIndex(null)
    }

    fun goToStart() {
        val total = spinHistory.value.size
        if (total > 0) {
            setTimeTravelIndex(1)
        }
    }

    // --- Table State Persistence ---

    fun saveTableState(scale: Float, x: Float, y: Float) {
        prefs.edit().apply {
            putFloat("table_scale", scale)
            putFloat("table_offset_x", x)
            putFloat("table_offset_y", y)
            apply()
        }
    }

    fun loadTableState() {
        val scale = prefs.getFloat("table_scale", 0.89f)
        val x = prefs.getFloat("table_offset_x", -230f)
        val y = prefs.getFloat("table_offset_y", 0f)
        
        viewModelScope.launch {
            _tableRestoreEvents.emit(TableState(scale, x, y))
        }
    }

    // --- Spin Logic ---

    fun addSpin(number: Int) {
        recordSpinActionForUndo()
        _timeTravelIndex.value = null // Auto-return to LIVE when new spin is registered
        viewModelScope.launch {
            spinDao.insertSpin(SpinResult(number = number))

            // Record round in active gameplay session
            val currentChips = _uiState.value.chips
            if (currentChips.isNotEmpty()) {
                _lastSpinLiveChips.value = currentChips
            }
            val activeSession = _activeGameplaySession.value
            val startBank = activeSession?.startingBankroll ?: 1000.0
            val isUS = _uiState.value.tableType == TableType.US
            val currentBankroll = _gameplayRecords.value.lastOrNull()?.bankroll ?: startBank

            val eval = com.roulettetactician.logic.GameplayCalculator.evaluateRound(currentChips, number, isUS)
            val newBankroll = currentBankroll + eval.netProfit
            val maxExistingIndex = _gameplayRecords.value.maxOfOrNull { it.spinIndex } ?: 0
            val newIndex = maxOf(maxExistingIndex + 1, spinHistory.value.size + 1)

            val newRecord = com.roulettetactician.logic.GameplaySpinRecord(
                spinIndex = newIndex,
                spunNumber = number,
                chips = currentChips,
                totalStake = eval.totalStake,
                totalPayout = eval.totalPayout,
                netProfit = eval.netProfit,
                bankroll = newBankroll
            )

            val updatedRecords = _gameplayRecords.value + newRecord
            _gameplayRecords.value = updatedRecords

            if (activeSession != null) {
                val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(updatedRecords)
                gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
            }
            autoSyncBackupToFolder()
        }
    }

    fun clearHistory() {
        if (spinHistory.value.isNotEmpty()) {
            recordSpinActionForUndo()
        }
        _timeTravelIndex.value = null
        viewModelScope.launch {
            spinDao.clearAll()
            if (_gameplayRecords.value.isNotEmpty()) {
                restoreGameplayRecords(emptyList())
            }
            autoSyncBackupToFolder()
        }
    }

    /**
     * Discards the first (oldest) [count] spins from the chronological spin history.
     * Supports Undo and Redo via the spin history snapshot stack.
     */
    fun discardFirstSpins(count: Int) {
        if (count <= 0) return
        val currentNumbers = spinHistory.value.reversed().map { it.number }
        if (currentNumbers.isEmpty()) return
        val actualDiscard = count.coerceAtMost(currentNumbers.size)
        if (actualDiscard <= 0) return

        recordSpinActionForUndo()
        _timeTravelIndex.value = null
        val remainingNumbers = currentNumbers.drop(actualDiscard)
        viewModelScope.launch {
            restoreSpinsFromNumbers(remainingNumbers)
            shiftGameplayRecordsOnBulkDiscard(actualDiscard)
            autoSyncBackupToFolder()
        }
    }

    /**
     * Retains only the latest (newest) [count] spins in the chronological spin history,
     * discarding all older spins. Supports Undo and Redo via the spin history snapshot stack.
     */
    fun retainLatestSpins(count: Int) {
        if (count < 0) return
        val currentNumbers = spinHistory.value.reversed().map { it.number }
        if (currentNumbers.isEmpty() || currentNumbers.size <= count) return
        val discardCount = currentNumbers.size - count
        discardFirstSpins(discardCount)
    }

    fun deleteSpin(spin: SpinResult) {
        recordSpinActionForUndo()
        _timeTravelIndex.value = null
        viewModelScope.launch {
            val currentSpins = spinDao.getAllSpins().first()
            val chronological = currentSpins.reversed()
            val targetIdx = chronological.indexOfFirst { it.id == spin.id }
            spinDao.deleteSpin(spin)
            if (targetIdx >= 0 && _gameplayRecords.value.isNotEmpty()) {
                shiftGameplayRecordsOnDelete(targetIdx)
            }
            autoSyncBackupToFolder()
        }
    }

    fun updateSpin(spin: SpinResult, newNumber: Int) {
        recordSpinActionForUndo()
        viewModelScope.launch {
            val currentSpins = spinDao.getAllSpins().first()
            val chronological = currentSpins.reversed()
            val targetIdx = chronological.indexOfFirst { it.id == spin.id }
            spinDao.updateSpin(spin.copy(number = newNumber))
            if (targetIdx >= 0 && _gameplayRecords.value.isNotEmpty()) {
                val currentRecords = _gameplayRecords.value.toMutableList()
                val spinIdx = targetIdx + 1
                val rIdx = currentRecords.indexOfFirst { it.spinIndex == spinIdx }
                if (rIdx >= 0) {
                    currentRecords[rIdx] = currentRecords[rIdx].copy(spunNumber = newNumber)
                    val activeSession = _activeGameplaySession.value
                    if (activeSession != null) {
                        val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(
                            currentRecords,
                            activeSession.startingBankroll,
                            _uiState.value.tableType == TableType.US
                        )
                        _gameplayRecords.value = recalculated
                        val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
                        gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
                    }
                }
            }
            autoSyncBackupToFolder()
        }
    }

    fun insertSpinBefore(targetSpin: SpinResult, newNumber: Int) {
        recordSpinActionForUndo()
        _timeTravelIndex.value = null
        viewModelScope.launch {
            val currentSpins = spinDao.getAllSpins().first()
            val chronological = currentSpins.reversed().toMutableList()
            val targetIdx = chronological.indexOfFirst { it.id == targetSpin.id }
            if (targetIdx >= 0) {
                val newNumbers = chronological.map { it.number }.toMutableList()
                newNumbers.add(targetIdx, newNumber)
                restoreSpinsFromNumbers(newNumbers)
                shiftGameplayRecordsOnInsert(targetIdx, newNumber)
                autoSyncBackupToFolder()
            }
        }
    }

    fun insertSpinAfter(targetSpin: SpinResult, newNumber: Int) {
        recordSpinActionForUndo()
        _timeTravelIndex.value = null
        viewModelScope.launch {
            val currentSpins = spinDao.getAllSpins().first()
            val chronological = currentSpins.reversed().toMutableList()
            val targetIdx = chronological.indexOfFirst { it.id == targetSpin.id }
            if (targetIdx >= 0) {
                val insertPos = targetIdx + 1
                val newNumbers = chronological.map { it.number }.toMutableList()
                newNumbers.add(insertPos, newNumber)
                restoreSpinsFromNumbers(newNumbers)
                shiftGameplayRecordsOnInsert(insertPos, newNumber)
                autoSyncBackupToFolder()
            }
        }
    }

    // --- Spin Log Scanner (camera -> Gemini Vision -> append) ---

    private val _geminiApiKey = MutableStateFlow(
        prefs.getString("gemini_api_key", null)?.takeIf { it.isNotBlank() }
            ?: com.roulettetactician.BuildConfig.GEMINI_API_KEY
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey

    fun setGeminiApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString("gemini_api_key", trimmed).apply()
        _geminiApiKey.value = trimmed
    }

    private val _spinScanState = MutableStateFlow<SpinScanState>(SpinScanState.Idle)
    val spinScanState: StateFlow<SpinScanState> = _spinScanState

    /** Extracts the spin log from a captured photo and prepares a de-duplicated append preview. */
    fun scanSpinLog(bitmap: android.graphics.Bitmap) {
        val activeKey = _geminiApiKey.value
        if (activeKey.isBlank()) {
            _spinScanState.value = SpinScanState.Error(
                "Gemini API key is not set. Tap the Key icon to enter your API key."
            )
            bitmap.recycle()
            return
        }

        _spinScanState.value = SpinScanState.Processing
        viewModelScope.launch {
            _spinScanState.value = try {
                val raw = spinLogExtractor.extract(bitmap, activeKey)
                val allowDoubleZero = _uiState.value.tableType == TableType.US
                val sanitized = com.roulettetactician.logic.SpinLogMerger.sanitize(raw.spinsNewestFirst, allowDoubleZero)
                if (!raw.logFound || sanitized.spinsOldestFirst.isEmpty()) {
                    SpinScanState.Error(
                        if (!raw.logFound) "No spin history found. Frame the results column/ribbon and retake."
                        else "Spin log was unreadable (glare?). Tilt the phone slightly and retake."
                    )
                } else {
                    val existing = spinDao.getAllSpins().first().reversed().map { it.number }
                    val merge = com.roulettetactician.logic.SpinLogMerger.merge(existing, sanitized.spinsOldestFirst)
                    SpinScanState.Review(
                        layout = raw.layout,
                        scannedOldestFirst = sanitized.spinsOldestFirst,
                        overlap = merge.overlap,
                        newSpins = merge.newSpins,
                        droppedOlder = sanitized.droppedOlder,
                        possibleGap = merge.possibleGap
                    )
                }
            } catch (e: Exception) {
                SpinScanState.Error(e.message ?: "Extraction failed")
            } finally {
                bitmap.recycle()
            }
        }
    }

    /** Appends the reviewed new spins (oldest-first) to the persistent history. */
    fun confirmScannedSpins() {
        val review = _spinScanState.value as? SpinScanState.Review ?: return
        _spinScanState.value = SpinScanState.Idle
        if (review.newSpins.isEmpty()) return
        recordSpinActionForUndo()
        _timeTravelIndex.value = null
        viewModelScope.launch {
            val base = System.currentTimeMillis()
            // Auto-generated ids preserve insertion (chronological) order; timestamps kept monotonic too.
            spinDao.insertAll(review.newSpins.mapIndexed { i, n -> SpinResult(number = n, timestamp = base + i) })
            autoSyncBackupToFolder()
        }
    }

    fun dismissSpinScan() {
        _spinScanState.value = SpinScanState.Idle
    }

    // --- Chip Logic (Game Play Recorder) ---

    fun selectChipValue(value: Int) {
        _uiState.update { it.copy(selectedChipValue = value) }
    }

    fun setChipRemovalMode(mode: com.roulettetactician.logic.ChipRemovalMode) {
        _chipRemovalMode.value = mode
    }

    fun placeChip(xPercent: Float, yPercent: Float, betId: String) {
        recordChipActionForUndo()
        _uiState.update { state ->
            val newChip = PlacedChip(
                id = System.nanoTime(),
                value = state.selectedChipValue,
                xPercent = xPercent,
                yPercent = yPercent,
                betId = betId
            )
            val stacked = com.roulettetactician.logic.GameplayCalculator.stackOrAddChip(state.chips, newChip)
            state.copy(
                chips = stacked,
                totalBet = stacked.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    fun updateChipPosition(id: Long, xPercent: Float, yPercent: Float, betId: String) {
        recordChipActionForUndo()
        _uiState.update { state ->
            val updated = state.chips.map { 
                if (it.id == id) it.copy(xPercent = xPercent, yPercent = yPercent, betId = betId) else it  
            }
            state.copy(chips = updated)
        }
        markChipsModified()
    }

    fun removeChip(id: Long) {
        recordChipActionForUndo()
        _uiState.update { state ->
            val updated = com.roulettetactician.logic.GameplayCalculator.removeOrDeductChip(
                state.chips,
                id,
                _chipRemovalMode.value,
                state.selectedChipValue
            )
            state.copy(
                chips = updated,
                totalBet = updated.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    fun clearChips() {
        if (_uiState.value.chips.isNotEmpty()) {
            recordChipActionForUndo()
        }
        _uiState.update { it.copy(chips = emptyList(), totalBet = 0) }
        markChipsModified()
    }

    fun increaseAllPlacedChipsByBaseUnit() {
        if (_uiState.value.chips.isEmpty()) return
        recordChipActionForUndo()
        val activeSession = _activeGameplaySession.value
        val insideBase = activeSession?.insideBaseUnit ?: 1.0
        val outsideBase = activeSession?.outsideBaseUnit ?: 5.0
        val isUS = _uiState.value.tableType == TableType.US

        _uiState.update { state ->
            val updated = com.roulettetactician.logic.GameplayCalculator.increaseAllByBaseUnit(
                state.chips,
                insideBase,
                outsideBase,
                isUS
            )
            state.copy(
                chips = updated,
                totalBet = updated.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    fun decreaseAllPlacedChipsByBaseUnit() {
        if (_uiState.value.chips.isEmpty()) return
        recordChipActionForUndo()
        val activeSession = _activeGameplaySession.value
        val insideBase = activeSession?.insideBaseUnit ?: 1.0
        val outsideBase = activeSession?.outsideBaseUnit ?: 5.0
        val isUS = _uiState.value.tableType == TableType.US

        _uiState.update { state ->
            val updated = com.roulettetactician.logic.GameplayCalculator.decreaseAllByBaseUnit(
                state.chips,
                insideBase,
                outsideBase,
                isUS
            )
            state.copy(
                chips = updated,
                totalBet = updated.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    /**
     * Retrieves the list of chips placed in the previous spin.
     * In time travel mode (viewing spin K), returns chips from the closest prior spin (e.g. spin K-1).
     * In live mode, returns chips from the last recorded spin with bets.
     */
    fun getPreviousSpinBets(): List<PlacedChip> {
        val timeIdx = _timeTravelIndex.value
        return if (timeIdx != null) {
            _gameplayRecords.value
                .filter { it.spinIndex < timeIdx && it.chips.isNotEmpty() }
                .maxByOrNull { it.spinIndex }
                ?.chips
                ?: emptyList()
        } else {
            _gameplayRecords.value
                .lastOrNull { it.chips.isNotEmpty() }
                ?.chips
                ?: _lastSpinLiveChips.value
        }
    }

    /**
     * Adds / stacks the bets from the previous spin onto the current table.
     */
    fun addPreviousSpinBets() {
        val prevBets = getPreviousSpinBets()
        if (prevBets.isEmpty()) return
        recordChipActionForUndo()

        _uiState.update { state ->
            var currentChips = state.chips
            prevBets.forEachIndexed { idx, prevChip ->
                val newChip = prevChip.copy(
                    id = System.nanoTime() + idx,
                    stack = prevChip.safeStack()
                )
                currentChips = com.roulettetactician.logic.GameplayCalculator.stackOrAddChip(currentChips, newChip)
            }
            state.copy(
                chips = currentChips,
                totalBet = currentChips.sumOf { it.value }
            )
        }

        markChipsModified()
    }

    // --- Game Play Session Management ---

    fun loadGameplaySessionsForRouletteSession(rouletteSessionName: String) {
        _activeRouletteSessionName.value = rouletteSessionName
    }

    fun selectGameplaySession(session: com.roulettetactician.data.GameplaySessionEntity) {
        _activeGameplaySession.value = session
        val rawRecords = com.roulettetactician.logic.GameplayCalculator.deserializeRecords(session.recordsJson)
        // Deduplicate records by spinIndex (preserving the record with chips or the latest entry)
        val hadDuplicates = rawRecords.size != rawRecords.distinctBy { it.spinIndex }.size
        val sanitizedRaw = if (hadDuplicates) {
            rawRecords
                .groupBy { it.spinIndex }
                .map { (_, group) ->
                    if (group.size == 1) group.first()
                    else group.maxByOrNull { it.chips.isNotEmpty() } ?: group.last()
                }
                .sortedBy { it.spinIndex }
        } else {
            rawRecords
        }
        val isUS = _uiState.value.tableType == TableType.US
        val records = com.roulettetactician.logic.GameplayCalculator.recalculateSession(sanitizedRaw, session.startingBankroll, isUS)
        _gameplayRecords.value = records
        if (hadDuplicates) {
            viewModelScope.launch {
                val cleanJson = com.roulettetactician.logic.GameplayCalculator.serializeRecords(records)
                gameplaySessionDao.updateSession(session.copy(recordsJson = cleanJson))
            }
        }
        if (records.isNotEmpty()) {
            val lastWithChips = records.lastOrNull { it.chips.isNotEmpty() }?.chips
            if (lastWithChips != null) {
                _lastSpinLiveChips.value = lastWithChips
            }
        }
        _hasUnsavedSpinEdits.value = false
        _pendingEditSpinIndex.value = null

        val currentIndex = _timeTravelIndex.value
        if (currentIndex != null) {
            val spinRecord = records.find { it.spinIndex == currentIndex }
            val spinChips = spinRecord?.chips ?: emptyList()
            _uiState.update { it.copy(chips = spinChips, totalBet = spinChips.sumOf { c -> c.value }) }
        } else {
            _uiState.update { it.copy(chips = _liveChips.value, totalBet = _liveChips.value.sumOf { c -> c.value }) }
        }
    }

    fun createGameplaySession(
        name: String,
        startingBankroll: Double = 1000.0,
        insideBaseUnit: Double = 1.0,
        insideMinUnit: Double = 1.0,
        outsideBaseUnit: Double = 5.0,
        outsideMinUnit: Double = 5.0
    ) {
        viewModelScope.launch {
            val defaultName = "Game ${gameplaySessions.value.size + 1}"
            val targetName = name.trim().ifEmpty { defaultName }
            val targetRouletteSession = _activeRouletteSessionName.value
            val newSession = com.roulettetactician.data.GameplaySessionEntity(
                rouletteSessionName = targetRouletteSession,
                name = targetName,
                timestamp = System.currentTimeMillis(),
                startingBankroll = startingBankroll,
                insideBaseUnit = insideBaseUnit,
                insideMinUnit = insideMinUnit,
                outsideBaseUnit = outsideBaseUnit,
                outsideMinUnit = outsideMinUnit,
                recordsJson = "[]"
            )
            val newId = gameplaySessionDao.insertSession(newSession)
            val created = newSession.copy(id = newId)
            _activeGameplaySession.value = created
            selectGameplaySession(created)
            autoSyncBackupToFolder()
        }
    }

    fun updateGameplaySessionConfig(
        startingBankroll: Double,
        insideBaseUnit: Double,
        insideMinUnit: Double,
        outsideBaseUnit: Double,
        outsideMinUnit: Double
    ) {
        val current = _activeGameplaySession.value ?: return
        val bankrollChanged = current.startingBankroll != startingBankroll
        val updated = current.copy(
            startingBankroll = startingBankroll,
            insideBaseUnit = insideBaseUnit,
            insideMinUnit = insideMinUnit,
            outsideBaseUnit = outsideBaseUnit,
            outsideMinUnit = outsideMinUnit
        )
        _activeGameplaySession.value = updated

        viewModelScope.launch {
            if (bankrollChanged) {
                val isUS = _uiState.value.tableType == TableType.US
                val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(
                    _gameplayRecords.value,
                    startingBankroll,
                    isUS
                )
                _gameplayRecords.value = recalculated
                val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
                gameplaySessionDao.updateSession(updated.copy(recordsJson = json))
            } else {
                gameplaySessionDao.updateSession(updated)
            }
            autoSyncBackupToFolder()
        }
    }

    fun deleteGameplaySession(session: com.roulettetactician.data.GameplaySessionEntity) {
        viewModelScope.launch {
            gameplaySessionDao.deleteSession(session)
            autoSyncBackupToFolder()
        }
    }

    fun renameGameplaySession(session: com.roulettetactician.data.GameplaySessionEntity, newName: String) {
        val cleanName = newName.trim().ifEmpty { session.name }
        if (cleanName == session.name) return
        val updated = session.copy(name = cleanName)
        if (_activeGameplaySession.value?.id == session.id) {
            _activeGameplaySession.value = updated
        }
        viewModelScope.launch {
            gameplaySessionDao.updateSession(updated)
            autoSyncBackupToFolder()
        }
    }

    fun reassignGameplaySession(session: com.roulettetactician.data.GameplaySessionEntity, targetRouletteSessionName: String) {
        val cleanTarget = targetRouletteSessionName.trim().ifEmpty { "New Session..." }
        if (cleanTarget == session.rouletteSessionName) return
        viewModelScope.launch {
            val updated = session.copy(rouletteSessionName = cleanTarget)
            gameplaySessionDao.updateSession(updated)
            autoSyncBackupToFolder()
        }
    }

    fun saveSpinEdits(spinIndex: Int = _pendingEditSpinIndex.value ?: _timeTravelIndex.value ?: spinHistory.value.size.coerceAtLeast(1)) {
        val currentFeltChips = _uiState.value.chips
        val records = _gameplayRecords.value
            .groupBy { it.spinIndex }
            .map { (_, group) ->
                if (group.size == 1) group.first()
                else group.maxByOrNull { it.chips.isNotEmpty() } ?: group.last()
            }
            .toMutableList()
        val chronological = spinHistory.value.reversed().map { it.number }
        val activeSession = _activeGameplaySession.value
        val startBank = activeSession?.startingBankroll ?: 1000.0
        val isUS = _uiState.value.tableType == TableType.US

        // Ensure all historical spins have an entry
        for (idx in 1..chronological.size) {
            val existing = records.indexOfFirst { it.spinIndex == idx }
            if (existing >= 0) {
                if (idx == spinIndex) {
                    records[existing] = records[existing].copy(chips = currentFeltChips)
                }
            } else {
                val spunNum = chronological[idx - 1]
                val chipList = if (idx == spinIndex) currentFeltChips else emptyList()
                records.add(
                    com.roulettetactician.logic.GameplaySpinRecord(
                        spinIndex = idx,
                        spunNumber = spunNum,
                        chips = chipList,
                        totalStake = 0.0,
                        totalPayout = 0.0,
                        netProfit = 0.0,
                        bankroll = startBank
                    )
                )
            }
        }
        records.sortBy { it.spinIndex }

        val recalculated = com.roulettetactician.logic.GameplayCalculator.recalculateSession(records, startBank, isUS)
        _gameplayRecords.value = recalculated
        _hasUnsavedSpinEdits.value = false
        _pendingEditSpinIndex.value = null
        _liveChips.value = currentFeltChips
        _lastSpinLiveChips.value = currentFeltChips

        if (activeSession != null) {
            viewModelScope.launch {
                val json = com.roulettetactician.logic.GameplayCalculator.serializeRecords(recalculated)
                gameplaySessionDao.updateSession(activeSession.copy(recordsJson = json))
                autoSyncBackupToFolder()
            }
        }
    }

    fun discardSpinEdits(spinIndex: Int = _pendingEditSpinIndex.value ?: _timeTravelIndex.value ?: 1) {
        val record = _gameplayRecords.value.find { it.spinIndex == spinIndex }
        val originalChips = record?.chips ?: emptyList()
        _uiState.update { it.copy(chips = originalChips, totalBet = originalChips.sumOf { c -> c.value }) }
        _liveChips.value = originalChips
        _hasUnsavedSpinEdits.value = false
        _pendingEditSpinIndex.value = null
    }

    fun saveCurrentChipsToLastSpin() {
        val lastIndex = spinHistory.value.size
        if (lastIndex > 0) {
            saveSpinEdits(lastIndex)
        }
    }

    /**
     * Clears all chips and places a $25 chip on each provided number.
     */
    fun placeStrategyBets(numbers: List<Int>) {
        recordChipActionForUndo()
        _uiState.update { state ->
            val isUS = state.tableType == TableType.US
            val newChips = numbers.map { num ->
                val target = com.roulettetactician.logic.Geometry.getNumberTarget(num, isUS)
                PlacedChip(
                    id = System.nanoTime() + num, // Ensure unique-ish IDs
                    value = 25,
                    xPercent = target.xPercent,
                    yPercent = target.yPercent,
                    betId = target.betId
                )
            }
            state.copy(
                chips = newChips,
                totalBet = newChips.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    /**
     * Applies recommended trend bets directly to the Felt tab's active chips.
     */
    fun applyTrendBets(trendChips: List<PlacedChip>) {
        recordChipActionForUndo()
        _uiState.update { state ->
            state.copy(
                chips = trendChips,
                totalBet = trendChips.sumOf { it.value }
            )
        }
        markChipsModified()
    }

    // --- Config ---

    fun setTableType(type: TableType) {
        _uiState.update { it.copy(tableType = type) }
    }

    fun setWheelName(name: String) {
        _uiState.update { it.copy(wheelName = name) }
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            _activeRouletteSessionName.value = trimmed
        }
    }

    fun setAnalysisWindow(window: Int) {
        _uiState.update { it.copy(analysisWindow = window) }
    }

    // --- MA & Triggers Persistent Updates ---

    fun setMaType(type: MovingAverageCalculator.MAType) {
        _maType.value = type
        prefs.edit().putString("ma_type", type.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaPeriod(period: Int) {
        _maPeriod.value = period
        prefs.edit().putInt("ma_period", period).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaActiveCategory(category: MACategoryTab) {
        _maActiveCategory.value = category
        prefs.edit().putString("ma_category", category.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaAlmaOffset(offset: Double) {
        _maAlmaOffset.value = offset
        prefs.edit().putFloat("ma_alma_offset", offset.toFloat()).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaAlmaSigma(sigma: Double) {
        _maAlmaSigma.value = sigma
        prefs.edit().putFloat("ma_alma_sigma", sigma.toFloat()).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaKamaFast(fast: Int) {
        _maKamaFast.value = fast
        prefs.edit().putInt("ma_kama_fast", fast).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setMaKamaSlow(slow: Int) {
        _maKamaSlow.value = slow
        prefs.edit().putInt("ma_kama_slow", slow).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTriggerThresholds(
        rb: Int = _triggerThresholdRB.value,
        hl: Int = _triggerThresholdHL.value,
        oe: Int = _triggerThresholdOE.value,
        dozens: Int = _triggerThresholdDozens.value,
        columns: Int = _triggerThresholdColumns.value,
        coldRb: Int = _triggerColdThresholdRB.value,
        coldHl: Int = _triggerColdThresholdHL.value,
        coldOe: Int = _triggerColdThresholdOE.value,
        coldDozens: Int = _triggerColdThresholdDozens.value,
        coldColumns: Int = _triggerColdThresholdColumns.value
    ) {
        _triggerThresholdRB.value = rb
        _triggerThresholdHL.value = hl
        _triggerThresholdOE.value = oe
        _triggerThresholdDozens.value = dozens
        _triggerThresholdColumns.value = columns
        _triggerColdThresholdRB.value = coldRb
        _triggerColdThresholdHL.value = coldHl
        _triggerColdThresholdOE.value = coldOe
        _triggerColdThresholdDozens.value = coldDozens
        _triggerColdThresholdColumns.value = coldColumns

        prefs.edit()
            .putInt("trigger_rb", rb)
            .putInt("trigger_hl", hl)
            .putInt("trigger_oe", oe)
            .putInt("trigger_dozens", dozens)
            .putInt("trigger_columns", columns)
            .putInt("trigger_cold_rb", coldRb)
            .putInt("trigger_cold_hl", coldHl)
            .putInt("trigger_cold_oe", coldOe)
            .putInt("trigger_cold_dozens", coldDozens)
            .putInt("trigger_cold_columns", coldColumns)
            .apply()
        autoSyncCurrentSessionConfig()
    }

    // --- Persistent Bet Trend Updaters ---

    fun setTrendActiveFamily(family: BetTrendCalculator.BetFamily) {
        _trendActiveFamily.value = family
        prefs.edit().putString("trend_family", family.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendLookbackWindow(window: Int) {
        _trendLookbackWindow.value = window
        prefs.edit().putInt("trend_lookback", window).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendSelectedProgression(prog: BetTrendCalculator.ProgressionType) {
        _trendSelectedProgression.value = prog
        prefs.edit().putString("trend_progression", prog.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendColdLimit(limit: Int) {
        _trendColdLimit.value = limit
        prefs.edit().putInt("trend_cold_limit", limit).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendPrimaryCriterion(crit: BetTrendCalculator.OptimizationCriterion) {
        _trendPrimaryCriterion.value = crit
        prefs.edit().putString("trend_primary_criterion", crit.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendSecondaryCriterion(crit: BetTrendCalculator.OptimizationCriterion) {
        _trendSecondaryCriterion.value = crit
        prefs.edit().putString("trend_secondary_criterion", crit.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendColdRule(rule: BetTrendCalculator.ColdRule) {
        _trendColdRule.value = rule
        prefs.edit().putString("trend_cold_rule", rule.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendEqualizePayout(equalize: Boolean) {
        _trendEqualizePayout.value = equalize
        prefs.edit().putBoolean("trend_equalize_payout", equalize).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendPushOrProfit(pushOrProfit: Boolean) {
        _trendPushOrProfit.value = pushOrProfit
        prefs.edit().putBoolean("trend_push_or_profit", pushOrProfit).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendRiskCap(cap: Boolean) {
        _trendRiskCap.value = cap
        prefs.edit().putBoolean("trend_risk_cap", cap).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendUnderwaterFilter(filter: Boolean) {
        _trendUnderwaterFilter.value = filter
        prefs.edit().putBoolean("trend_underwater_filter", filter).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendTransitionRule(rule: BetTrendCalculator.SwitchTransitionRule) {
        _trendTransitionRule.value = rule
        prefs.edit().putString("trend_transition_rule", rule.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendSwitchSensitivity(sensitivity: BetTrendCalculator.SwitchSensitivity) {
        _trendSwitchSensitivity.value = sensitivity
        prefs.edit().putString("trend_switch_sensitivity", sensitivity.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendShowIdealOverlay(show: Boolean) {
        _trendShowIdealOverlay.value = show
        prefs.edit().putBoolean("trend_show_ideal_overlay", show).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendStartMode(mode: BetTrendCalculator.BettingStartMode) {
        _trendStartMode.value = mode
        prefs.edit().putString("trend_start_mode", mode.name).apply()
        autoSyncCurrentSessionConfig()
    }

    fun setTrendEquityChartHeight(height: Float) {
        _trendEquityChartHeightDp.value = height
        prefs.edit().putFloat("trend_equity_chart_height", height).apply()
    }

    fun setTrendLedgerTableHeight(height: Float) {
        _trendLedgerTableHeightDp.value = height
        prefs.edit().putFloat("trend_ledger_table_height", height).apply()
    }

    private fun autoSyncCurrentSessionConfig() {
        val activeName = _activeRouletteSessionName.value
        if (activeName.isNotBlank() && activeName != "New Session...") {
            viewModelScope.launch(Dispatchers.IO) {
                val existing = sessionDao.getAllSessionsList().find { it.name == activeName }
                if (existing != null) {
                    val updated = existing.copy(
                        maType = _maType.value.name,
                        maPeriod = _maPeriod.value,
                        maCategory = _maActiveCategory.value.name,
                        maAlmaOffset = _maAlmaOffset.value,
                        maAlmaSigma = _maAlmaSigma.value,
                        maKamaFast = _maKamaFast.value,
                        maKamaSlow = _maKamaSlow.value,
                        triggerRB = _triggerThresholdRB.value,
                        triggerHL = _triggerThresholdHL.value,
                        triggerOE = _triggerThresholdOE.value,
                        triggerDozens = _triggerThresholdDozens.value,
                        triggerColumns = _triggerThresholdColumns.value,
                        trendFamily = _trendActiveFamily.value.name,
                        trendLookback = _trendLookbackWindow.value,
                        trendProgression = _trendSelectedProgression.value.name,
                        trendColdLimit = _trendColdLimit.value,
                        trendPrimaryCriterion = _trendPrimaryCriterion.value.name,
                        trendSecondaryCriterion = _trendSecondaryCriterion.value.name,
                        trendColdRule = _trendColdRule.value.name,
                        trendEqualizePayout = _trendEqualizePayout.value,
                        trendPushOrProfit = _trendPushOrProfit.value,
                        trendRiskCap = _trendRiskCap.value,
                        trendUnderwaterFilter = _trendUnderwaterFilter.value,
                        trendTransitionRule = _trendTransitionRule.value.name,
                        trendSwitchSensitivity = _trendSwitchSensitivity.value.name,
                        trendShowIdealOverlay = _trendShowIdealOverlay.value,
                        trendStartMode = _trendStartMode.value.name
                    )
                    sessionDao.insertSession(updated)
                }
            }
        }
    }

    // --- Sessions ---

    fun saveCurrentSession(
        trendFamily: BetTrendCalculator.BetFamily = _trendActiveFamily.value,
        trendLookback: Int = _trendLookbackWindow.value,
        trendProgression: BetTrendCalculator.ProgressionType = _trendSelectedProgression.value,
        trendColdLimit: Int = _trendColdLimit.value,
        maType: MovingAverageCalculator.MAType = _maType.value,
        maPeriod: Int = _maPeriod.value,
        maCategory: MACategoryTab = _maActiveCategory.value,
        maAlmaOffset: Double = _maAlmaOffset.value,
        maAlmaSigma: Double = _maAlmaSigma.value,
        maKamaFast: Int = _maKamaFast.value,
        maKamaSlow: Int = _maKamaSlow.value,
        triggerRB: Int = _triggerThresholdRB.value,
        triggerHL: Int = _triggerThresholdHL.value,
        triggerOE: Int = _triggerThresholdOE.value,
        triggerDozens: Int = _triggerThresholdDozens.value,
        triggerColumns: Int = _triggerThresholdColumns.value,
        trendPrimaryCriterion: BetTrendCalculator.OptimizationCriterion = _trendPrimaryCriterion.value,
        trendSecondaryCriterion: BetTrendCalculator.OptimizationCriterion = _trendSecondaryCriterion.value,
        trendColdRule: BetTrendCalculator.ColdRule = _trendColdRule.value,
        trendEqualizePayout: Boolean = _trendEqualizePayout.value,
        trendPushOrProfit: Boolean = _trendPushOrProfit.value,
        trendRiskCap: Boolean = _trendRiskCap.value,
        trendUnderwaterFilter: Boolean = _trendUnderwaterFilter.value,
        trendTransitionRule: BetTrendCalculator.SwitchTransitionRule = _trendTransitionRule.value,
        trendSwitchSensitivity: BetTrendCalculator.SwitchSensitivity = _trendSwitchSensitivity.value,
        trendShowIdealOverlay: Boolean = _trendShowIdealOverlay.value,
        trendStartMode: BetTrendCalculator.BettingStartMode = _trendStartMode.value
    ) {
        viewModelScope.launch {
            val state = _uiState.value
            val history = spinHistory.first()

            // history is newest-first (ORDER BY id DESC), so reverse to get oldest-first
            // for stable serialization: "oldest,...,newest"
            val oldestFirstNumbers = history.reversed().map { it.number }
            val historyStr = SessionDataValidator.serializeHistory(oldestFirstNumbers)
            val hotStr = SessionDataValidator.serializeOverrides(state.hotOverrides)
            val coldStr = SessionDataValidator.serializeOverrides(state.coldOverrides)
            val chipsStr = SessionDataValidator.serializeChips(state.chips)

            val session = SessionEntity(
                name = state.wheelName,
                timestamp = System.currentTimeMillis(),
                hotOverrides = hotStr,
                coldOverrides = coldStr,
                analysisWindow = state.analysisWindow,
                history = historyStr,
                chips = chipsStr,
                trendFamily = trendFamily.name,
                trendLookback = trendLookback,
                trendProgression = trendProgression.name,
                trendColdLimit = trendColdLimit,
                trendPrimaryCriterion = trendPrimaryCriterion.name,
                trendSecondaryCriterion = trendSecondaryCriterion.name,
                trendColdRule = trendColdRule.name,
                trendEqualizePayout = trendEqualizePayout,
                trendPushOrProfit = trendPushOrProfit,
                trendRiskCap = trendRiskCap,
                trendUnderwaterFilter = trendUnderwaterFilter,
                trendTransitionRule = trendTransitionRule.name,
                trendSwitchSensitivity = trendSwitchSensitivity.name,
                trendShowIdealOverlay = trendShowIdealOverlay,
                trendStartMode = trendStartMode.name,
                maType = maType.name,
                maPeriod = maPeriod,
                maCategory = maCategory.name,
                maAlmaOffset = maAlmaOffset,
                maAlmaSigma = maAlmaSigma,
                maKamaFast = maKamaFast,
                maKamaSlow = maKamaSlow,
                triggerRB = triggerRB,
                triggerHL = triggerHL,
                triggerOE = triggerOE,
                triggerDozens = triggerDozens,
                triggerColumns = triggerColumns
            )
            sessionDao.insertSession(session)
            prefs.edit()
                .putString("trend_family", trendFamily.name)
                .putInt("trend_lookback", trendLookback)
                .putString("trend_progression", trendProgression.name)
                .putInt("trend_cold_limit", trendColdLimit)
                .putString("trend_primary_criterion", trendPrimaryCriterion.name)
                .putString("trend_secondary_criterion", trendSecondaryCriterion.name)
                .putString("trend_cold_rule", trendColdRule.name)
                .putBoolean("trend_equalize_payout", trendEqualizePayout)
                .putBoolean("trend_push_or_profit", trendPushOrProfit)
                .putBoolean("trend_risk_cap", trendRiskCap)
                .putBoolean("trend_underwater_filter", trendUnderwaterFilter)
                .putString("trend_transition_rule", trendTransitionRule.name)
                .putString("trend_switch_sensitivity", trendSwitchSensitivity.name)
                .putBoolean("trend_show_ideal_overlay", trendShowIdealOverlay)
                .putString("trend_start_mode", trendStartMode.name)
                .apply()
            val oldSessionName = _activeRouletteSessionName.value
            val newSessionName = state.wheelName.ifBlank { "New Session..." }
            if (oldSessionName != newSessionName && (oldSessionName == "My Wheel" || oldSessionName == "New Session...")) {
                gameplaySessionDao.updateRouletteSessionName(oldSessionName, newSessionName)
            }
            _activeRouletteSessionName.value = newSessionName
            autoSyncBackupToFolder()
        }
    }

    fun loadSession(session: SessionEntity) {
        _timeTravelIndex.value = null
        _activeRouletteSessionName.value = session.name
        clearSpinUndoRedo()
        viewModelScope.launch {
            // 1. Fast Validation & Parse of History
            val validation = SessionDataValidator.parseAndValidateHistory(session.history)
            val nums = validation.numbers // oldest-first: [oldest, ..., newest]

            // Prepare SpinResult entities with strictly monotonic timestamps
            // so order is 100% deterministic and preserved
            val baseTime = System.currentTimeMillis() - (nums.size * 1000L)
            val spinEntities = nums.mapIndexed { index, n ->
                SpinResult(
                    number = n,
                    timestamp = baseTime + (index * 1000L)
                )
            }

            // 2. Atomically Replace History in Room
            // Runs clearAll + insertAll inside a single SQLite transaction
            // Prevents intermediate Flow emissions, race conditions, and out-of-order writes
            spinDao.replaceAllSpins(spinEntities)

            // 3. Restore Config, Overrides & Chips safely
            val hot = SessionDataValidator.parseOverrides(session.hotOverrides)
            val cold = SessionDataValidator.parseOverrides(session.coldOverrides)
            val chips = SessionDataValidator.parseChips(session.chips)

            _uiState.update { 
                it.copy(
                    wheelName = session.name,
                    analysisWindow = session.analysisWindow,
                    hotOverrides = if (hot.isNotEmpty()) hot else it.hotOverrides,
                    coldOverrides = if (cold.isNotEmpty()) cold else it.coldOverrides,
                    chips = chips,
                    totalBet = chips.sumOf { c -> c.value }
                ) 
            }

            // Session configuration event emission (MA, Bet Trend, and Triggers retain user persistence across sessions)
            val mat = try {
                MovingAverageCalculator.MAType.valueOf(session.maType)
            } catch (e: Exception) {
                MovingAverageCalculator.MAType.SMA
            }
            val mac = try {
                MACategoryTab.valueOf(session.maCategory)
            } catch (e: Exception) {
                MACategoryTab.ALL
            }

            // Emit session config with persistent Bet Trend, MA, and Trigger settings
            _sessionConfigEvents.emit(
                SessionConfigEvent(
                    trendFamily = _trendActiveFamily.value,
                    trendLookback = _trendLookbackWindow.value,
                    trendProgression = _trendSelectedProgression.value,
                    trendColdLimit = _trendColdLimit.value,
                    maType = mat,
                    maPeriod = session.maPeriod,
                    maCategory = mac,
                    maAlmaOffset = session.maAlmaOffset,
                    maAlmaSigma = session.maAlmaSigma,
                    maKamaFast = session.maKamaFast,
                    maKamaSlow = session.maKamaSlow,
                    triggerRB = session.triggerRB,
                    triggerHL = session.triggerHL,
                    triggerOE = session.triggerOE,
                    triggerDozens = session.triggerDozens,
                    triggerColumns = session.triggerColumns,
                    trendPrimaryCriterion = _trendPrimaryCriterion.value,
                    trendSecondaryCriterion = _trendSecondaryCriterion.value,
                    trendColdRule = _trendColdRule.value,
                    trendEqualizePayout = _trendEqualizePayout.value,
                    trendPushOrProfit = _trendPushOrProfit.value,
                    trendRiskCap = _trendRiskCap.value,
                    trendUnderwaterFilter = _trendUnderwaterFilter.value,
                    trendTransitionRule = _trendTransitionRule.value,
                    trendSwitchSensitivity = _trendSwitchSensitivity.value,
                    trendShowIdealOverlay = _trendShowIdealOverlay.value,
                    trendStartMode = _trendStartMode.value
                )
            )

            loadGameplaySessionsForRouletteSession(session.name)
            autoSyncCurrentSessionConfig()
        }
    }

    fun deleteSession(session: SessionEntity) {
        viewModelScope.launch {
            // Delete all associated gameplay sessions first to maintain database integrity
            gameplaySessionDao.deleteSessionsForRouletteSession(session.name)
            sessionDao.deleteSession(session)
            if (_activeRouletteSessionName.value == session.name) {
                _activeRouletteSessionName.value = "New Session..."
                _uiState.update { it.copy(wheelName = "") }
            }
            autoSyncBackupToFolder()
        }
    }

    fun importSessionFromFile(sessionName: String, fileContent: String, onComplete: ((Int) -> Unit)? = null) {
        viewModelScope.launch {
            val validation = SessionDataValidator.parseAndValidateHistory(fileContent)
            val nums = validation.numbers // 1st spin to last spin in order
            if (nums.isEmpty()) {
                onComplete?.invoke(0)
                return@launch
            }

            val historyStr = SessionDataValidator.serializeHistory(nums)
            val session = SessionEntity(
                name = sessionName.ifBlank { "Imported Session" },
                timestamp = System.currentTimeMillis(),
                hotOverrides = "",
                coldOverrides = "",
                analysisWindow = 37,
                history = historyStr,
                chips = "",
                trendFamily = _trendActiveFamily.value.name,
                trendLookback = _trendLookbackWindow.value,
                trendProgression = _trendSelectedProgression.value.name,
                trendColdLimit = _trendColdLimit.value,
                trendPrimaryCriterion = _trendPrimaryCriterion.value.name,
                trendSecondaryCriterion = _trendSecondaryCriterion.value.name,
                trendColdRule = _trendColdRule.value.name,
                trendEqualizePayout = _trendEqualizePayout.value,
                trendPushOrProfit = _trendPushOrProfit.value,
                trendRiskCap = _trendRiskCap.value,
                trendUnderwaterFilter = _trendUnderwaterFilter.value,
                trendTransitionRule = _trendTransitionRule.value.name,
                trendSwitchSensitivity = _trendSwitchSensitivity.value.name,
                trendShowIdealOverlay = _trendShowIdealOverlay.value,
                trendStartMode = _trendStartMode.value.name,
                maType = _maType.value.name,
                maPeriod = _maPeriod.value,
                maCategory = _maActiveCategory.value.name,
                maAlmaOffset = _maAlmaOffset.value,
                maAlmaSigma = _maAlmaSigma.value,
                maKamaFast = _maKamaFast.value,
                maKamaSlow = _maKamaSlow.value,
                triggerRB = _triggerThresholdRB.value,
                triggerHL = _triggerThresholdHL.value,
                triggerOE = _triggerThresholdOE.value,
                triggerDozens = _triggerThresholdDozens.value,
                triggerColumns = _triggerThresholdColumns.value
            )
            sessionDao.insertSession(session)
            _activeRouletteSessionName.value = session.name
            autoSyncBackupToFolder()

            // Load directly into active game state
            val baseTime = System.currentTimeMillis() - (nums.size * 1000L)
            val spinEntities = nums.mapIndexed { index, n ->
                SpinResult(
                    number = n,
                    timestamp = baseTime + (index * 1000L)
                )
            }
            spinDao.replaceAllSpins(spinEntities)

            _uiState.update {
                it.copy(
                    wheelName = session.name,
                    chips = emptyList(),
                    totalBet = 0
                )
            }

            onComplete?.invoke(nums.size)
        }
    }

    suspend fun getExportSpinsContent(session: SessionEntity? = null): String {
        val numbers = if (session != null) {
            SessionDataValidator.parseAndValidateHistory(session.history).numbers
        } else {
            val history = spinHistory.first()
            history.reversed().map { it.number } // 1st to last in that order
        }
        return SessionDataValidator.serializeHistoryForFile(numbers)
    }

    // --- Overrides ---

    
    fun setManualHot(index: Int, number: Int?) {
        _uiState.update { state ->
            val newHot = state.hotOverrides.toMutableList()
            if (index in newHot.indices) newHot[index] = number
            state.copy(hotOverrides = newHot)
        }
    }

    fun setManualCold(index: Int, number: Int?) {
        _uiState.update { state ->
            val newCold = state.coldOverrides.toMutableList()
            if (index in newCold.indices) newCold[index] = number
            state.copy(coldOverrides = newCold)
        }
    }

    // --- External Storage Backup & Restore for Safe Retention ---

    private val _backupFolderUri = MutableStateFlow(prefs.getString("backup_folder_uri", null))
    val backupFolderUri: StateFlow<String?> = _backupFolderUri

    fun autoSyncBackupToFolder() {
        val folderUriStr = _backupFolderUri.value
        if (!folderUriStr.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val uri = android.net.Uri.parse(folderUriStr)
                    saveBackupToFolder(context, uri)
                } catch (e: Exception) {
                    // Silent background sync
                }
            }
        }
    }

    fun setBackupFolderUri(uri: String?) {
        prefs.edit().putString("backup_folder_uri", uri).apply()
        _backupFolderUri.value = uri
        if (uri != null) {
            autoSyncBackupToFolder()
        }
    }

    suspend fun exportFullBackupJson(): String = withContext(Dispatchers.IO) {
        val sessions = sessionDao.getAllSessionsList()
        val gameplay = gameplaySessionDao.getAllSessionsList()
        val bundle = BackupDataBundle(
            version = 1,
            appVersion = "2.0",
            exportTimestamp = System.currentTimeMillis(),
            rouletteSessions = sessions,
            gameplaySessions = gameplay
        )
        GsonBuilder().setPrettyPrinting().create().toJson(bundle)
    }

    suspend fun saveBackupToFolder(context: android.content.Context, treeUri: android.net.Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(Exception("Cannot access selected folder"))
            if (!documentDir.canWrite()) {
                return@withContext Result.failure(Exception("No write permission for selected folder"))
            }
            val fileName = "roulette_tactician_backup.json"
            val existingFile = documentDir.findFile(fileName)
            val targetFile = existingFile ?: documentDir.createFile("application/json", fileName)
                ?: return@withContext Result.failure(Exception("Failed to create backup file in folder"))

            val jsonContent = exportFullBackupJson()
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { out ->
                out.bufferedWriter().use { writer ->
                    writer.write(jsonContent)
                }
            } ?: return@withContext Result.failure(Exception("Could not open file for writing"))

            Result.success(targetFile.name ?: fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFullBackupFromJson(jsonString: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val bundle = Gson().fromJson(jsonString, BackupDataBundle::class.java)
                ?: return@withContext Result.failure(Exception("Invalid backup file format"))

            val sessionCount = bundle.rouletteSessions.size
            val gameplayCount = bundle.gameplaySessions.size

            if (bundle.rouletteSessions.isNotEmpty()) {
                sessionDao.insertSessions(bundle.rouletteSessions)
            }
            if (bundle.gameplaySessions.isNotEmpty()) {
                gameplaySessionDao.insertSessions(bundle.gameplaySessions)
            }

            val currentActive = _uiState.value.wheelName
            if (currentActive.isNotBlank()) {
                val restoredActive = bundle.rouletteSessions.find { it.name == currentActive }
                if (restoredActive != null) {
                    withContext(Dispatchers.Main) {
                        loadSession(restoredActive)
                    }
                }
            }

            Result.success(Pair(sessionCount, gameplayCount))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
