package com.roulettetactician.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roulettetactician.logic.BetTrendCalculator
import com.roulettetactician.logic.HeatmapCalculator
import com.roulettetactician.ui.model.PlacedChip
import com.roulettetactician.ui.model.TableType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Bet Trend & Predictive Analysis Dashboard.
 * Integrates walk-forward backtesting, full progression optimization,
 * shared felt recommendation rendering, resizable equity trajectory, and resizable detailed spin ledger.
 */
@Composable
fun BetTrendDashboard(
    spins: List<Int>,
    tableType: TableType,
    heatmapStats: HeatmapCalculator.HeatmapStats,
    hotOverrides: List<Int?>,
    coldOverrides: List<Int?>,
    onApplyBetsToFelt: (List<PlacedChip>) -> Unit,
    modifier: Modifier = Modifier,
    activeFamily: BetTrendCalculator.BetFamily? = null,
    onActiveFamilyChange: ((BetTrendCalculator.BetFamily) -> Unit)? = null,
    lookbackWindow: Int? = null,
    onLookbackWindowChange: ((Int) -> Unit)? = null,
    selectedProgression: BetTrendCalculator.ProgressionType? = null,
    onSelectedProgressionChange: ((BetTrendCalculator.ProgressionType) -> Unit)? = null,
    coldLimit: Int? = null,
    onColdLimitChange: ((Int) -> Unit)? = null,
    heatMode: FeltHeatMode = FeltHeatMode.GLOBAL_HEAT,
    outsideHeatMap: Map<String, HeatmapCalculator.OutsideHeatInfo>? = null,
    fullSpins: List<Int>? = null,
    timeTravelIndex: Int? = null,
    onTimeTravelChange: ((Int?) -> Unit)? = null,
    primaryCriterion: BetTrendCalculator.OptimizationCriterion? = null,
    onPrimaryCriterionChange: ((BetTrendCalculator.OptimizationCriterion) -> Unit)? = null,
    secondaryCriterion: BetTrendCalculator.OptimizationCriterion? = null,
    onSecondaryCriterionChange: ((BetTrendCalculator.OptimizationCriterion) -> Unit)? = null,
    coldRule: BetTrendCalculator.ColdRule? = null,
    onColdRuleChange: ((BetTrendCalculator.ColdRule) -> Unit)? = null,
    equalizePayout: Boolean? = null,
    onEqualizePayoutChange: ((Boolean) -> Unit)? = null,
    pushOrProfit: Boolean? = null,
    onPushOrProfitChange: ((Boolean) -> Unit)? = null,
    riskCap: Boolean? = null,
    onRiskCapChange: ((Boolean) -> Unit)? = null,
    underwaterFilter: Boolean? = null,
    onUnderwaterFilterChange: ((Boolean) -> Unit)? = null,
    transitionRule: BetTrendCalculator.SwitchTransitionRule? = null,
    onTransitionRuleChange: ((BetTrendCalculator.SwitchTransitionRule) -> Unit)? = null,
    switchSensitivity: BetTrendCalculator.SwitchSensitivity? = null,
    onSwitchSensitivityChange: ((BetTrendCalculator.SwitchSensitivity) -> Unit)? = null,
    showIdealOverlay: Boolean? = null,
    onShowIdealOverlayChange: ((Boolean) -> Unit)? = null,
    startMode: BetTrendCalculator.BettingStartMode? = null,
    onStartModeChange: ((BetTrendCalculator.BettingStartMode) -> Unit)? = null,
    equityChartHeight: Dp? = null,
    onEquityChartHeightChange: ((Dp) -> Unit)? = null,
    ledgerTableHeight: Dp? = null,
    onLedgerTableHeightChange: ((Dp) -> Unit)? = null,
    leftScrollState: ScrollState? = null,
    rightScrollState: ScrollState? = null,
    startBankroll: Double = 1000.0,
    baseInsideBet: Double = 1.0,
    baseOutsideBet: Double = 5.0
) {
    // Configurable persistent settings with hoisting and rememberSaveable support
    var internalActiveFamily by rememberSaveable { mutableStateOf(BetTrendCalculator.BetFamily.COMBINED) }
    val currentActiveFamily = activeFamily ?: internalActiveFamily
    val setFamily: (BetTrendCalculator.BetFamily) -> Unit = {
        internalActiveFamily = it
        onActiveFamilyChange?.invoke(it)
    }

    var internalLookbackWindow by rememberSaveable { mutableIntStateOf(37) }
    val currentLookbackWindow = lookbackWindow ?: internalLookbackWindow
    val setLookback: (Int) -> Unit = {
        internalLookbackWindow = it
        onLookbackWindowChange?.invoke(it)
    }

    var internalProgression by rememberSaveable { mutableStateOf(BetTrendCalculator.ProgressionType.AUTO) }
    val currentProgression = selectedProgression ?: internalProgression
    val setProgression: (BetTrendCalculator.ProgressionType) -> Unit = {
        internalProgression = it
        onSelectedProgressionChange?.invoke(it)
    }

    var internalColdLimit by rememberSaveable { mutableIntStateOf(5) }
    val currentColdLimit = coldLimit ?: internalColdLimit
    val setColdLimit: (Int) -> Unit = {
        internalColdLimit = it
        onColdLimitChange?.invoke(it)
    }

    var internalColdRule by rememberSaveable { mutableStateOf(BetTrendCalculator.ColdRule.STRICT) }
    val currentColdRule = coldRule ?: internalColdRule
    val setColdRule: (BetTrendCalculator.ColdRule) -> Unit = {
        internalColdRule = it
        onColdRuleChange?.invoke(it)
    }

    var internalEqualizePayout by rememberSaveable { mutableStateOf(false) }
    val currentEqualizePayout = equalizePayout ?: internalEqualizePayout
    val setEqualizePayout: (Boolean) -> Unit = {
        internalEqualizePayout = it
        onEqualizePayoutChange?.invoke(it)
    }

    var internalPushOrProfit by rememberSaveable { mutableStateOf(false) }
    val currentPushOrProfit = pushOrProfit ?: internalPushOrProfit
    val setPushOrProfit: (Boolean) -> Unit = {
        internalPushOrProfit = it
        onPushOrProfitChange?.invoke(it)
    }

    var internalRiskCap by rememberSaveable { mutableStateOf(true) }
    val currentRiskCap = riskCap ?: internalRiskCap
    val setRiskCap: (Boolean) -> Unit = {
        internalRiskCap = it
        onRiskCapChange?.invoke(it)
    }

    var internalUnderwaterFilter by rememberSaveable { mutableStateOf(true) }
    val currentUnderwaterFilter = underwaterFilter ?: internalUnderwaterFilter
    val setUnderwaterFilter: (Boolean) -> Unit = {
        internalUnderwaterFilter = it
        onUnderwaterFilterChange?.invoke(it)
    }

    var internalTransitionRule by rememberSaveable { mutableStateOf(BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE) }
    val currentTransitionRule = transitionRule ?: internalTransitionRule
    val setTransitionRule: (BetTrendCalculator.SwitchTransitionRule) -> Unit = {
        internalTransitionRule = it
        onTransitionRuleChange?.invoke(it)
    }

    var internalSwitchSensitivity by rememberSaveable { mutableStateOf(BetTrendCalculator.SwitchSensitivity.MODERATE) }
    val currentSwitchSensitivity = switchSensitivity ?: internalSwitchSensitivity
    val setSwitchSensitivity: (BetTrendCalculator.SwitchSensitivity) -> Unit = {
        internalSwitchSensitivity = it
        onSwitchSensitivityChange?.invoke(it)
    }

    var internalShowIdealOverlay by rememberSaveable { mutableStateOf(false) }
    val currentShowIdealOverlay = showIdealOverlay ?: internalShowIdealOverlay
    val setShowIdealOverlay: (Boolean) -> Unit = {
        internalShowIdealOverlay = it
        onShowIdealOverlayChange?.invoke(it)
    }

    var internalStartMode by rememberSaveable { mutableStateOf(BetTrendCalculator.BettingStartMode.FROM_SPIN_1) }
    val currentStartMode = startMode ?: internalStartMode
    val setStartMode: (BetTrendCalculator.BettingStartMode) -> Unit = {
        internalStartMode = it
        onStartModeChange?.invoke(it)
    }

    var showQualityDetailsDialog by remember { mutableStateOf(false) }

    var internalPrimaryCriterion by rememberSaveable { mutableStateOf(BetTrendCalculator.OptimizationCriterion.PNL) }
    val currentPrimaryCriterion = primaryCriterion ?: internalPrimaryCriterion
    val setPrimaryCriterion: (BetTrendCalculator.OptimizationCriterion) -> Unit = {
        internalPrimaryCriterion = it
        onPrimaryCriterionChange?.invoke(it)
    }

    var internalSecondaryCriterion by rememberSaveable { mutableStateOf(BetTrendCalculator.OptimizationCriterion.MAX_DD) }
    val currentSecondaryCriterion = secondaryCriterion ?: internalSecondaryCriterion
    val setSecondaryCriterion: (BetTrendCalculator.OptimizationCriterion) -> Unit = {
        internalSecondaryCriterion = it
        onSecondaryCriterionChange?.invoke(it)
    }

    // Resizable panel dimensions with hoisting and rememberSaveable support
    var internalEquityChartHeight by rememberSaveable { mutableFloatStateOf(145f) }
    val currentEquityChartHeight = equityChartHeight ?: internalEquityChartHeight.dp
    val setEquityChartHeight: (Dp) -> Unit = {
        internalEquityChartHeight = it.value
        onEquityChartHeightChange?.invoke(it)
    }

    var internalLedgerTableHeight by rememberSaveable { mutableFloatStateOf(210f) }
    val currentLedgerTableHeight = ledgerTableHeight ?: internalLedgerTableHeight.dp
    val setLedgerTableHeight: (Dp) -> Unit = {
        internalLedgerTableHeight = it.value
        onLedgerTableHeightChange?.invoke(it)
    }

    val effectiveLeftScrollState = leftScrollState ?: rememberScrollState()
    val effectiveRightScrollState = rightScrollState ?: rememberScrollState()

    // Dialog & Dropdown visibility states
    var showProgressionDropdown by remember { mutableStateOf(false) }
    var showColdDropdown by remember { mutableStateOf(false) }
    var showColdRuleDropdown by remember { mutableStateOf(false) }
    var showTransitionRuleDropdown by remember { mutableStateOf(false) }
    var showSensitivityDropdown by remember { mutableStateOf(false) }
    var showLookbackManualDialog by remember { mutableStateOf(false) }
    var showProgressionExplanationDialog by remember { mutableStateOf(false) }
    var manualLookbackText by remember { mutableStateOf("") }

    val isUS = tableType == TableType.US
    val lastSpunNumber = spins.lastOrNull()

    // Full session backtest results for Equity Trajectory, Detailed Spin Ledger, and All Spins telemetry
    val fullTrendResult = remember(
        fullSpins,
        isUS,
        currentProgression,
        currentActiveFamily,
        currentColdLimit,
        currentColdRule,
        currentLookbackWindow,
        startBankroll,
        baseInsideBet,
        baseOutsideBet,
        currentPrimaryCriterion,
        currentSecondaryCriterion,
        currentEqualizePayout,
        currentPushOrProfit,
        currentRiskCap,
        currentUnderwaterFilter,
        currentTransitionRule,
        currentSwitchSensitivity,
        currentStartMode
    ) {
        val sessionSpins = if (!fullSpins.isNullOrEmpty()) fullSpins else spins
        BetTrendCalculator.optimize(
            spins = sessionSpins,
            isUS = isUS,
            targetProgression = currentProgression,
            family = currentActiveFamily,
            coldLimit = currentColdLimit,
            lookbackWindow = currentLookbackWindow,
            startBankroll = startBankroll,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            primaryCriterion = currentPrimaryCriterion,
            secondaryCriterion = currentSecondaryCriterion,
            coldRule = currentColdRule,
            equalizePayout = currentEqualizePayout,
            pushOrProfit = currentPushOrProfit,
            riskCap = currentRiskCap,
            underwaterFilter = currentUnderwaterFilter,
            transitionRule = currentTransitionRule,
            switchSensitivity = currentSwitchSensitivity,
            startMode = currentStartMode
        )
    }

    val fullEquityPoints = fullTrendResult.equityPoints
    val fullSpinLogs = fullTrendResult.spinLogs

    // The effective progression to evaluate at the current replay position:
    // If the user selected AUTO, use the session's optimized best progression (e.g. FIBONACCI)
    // so the replay position's multipliers, recommendations, and stakes match the session ledger!
    val effectiveReplayProgression = if (currentProgression == BetTrendCalculator.ProgressionType.AUTO) {
        fullTrendResult.bestProgression
    } else {
        currentProgression
    }

    // Total session spins count (stable during replay scrubbing so findOptimalConfig only runs when session or settings change)
    val totalSessionSpins = fullSpins?.size ?: spins.size
    val isScrubbing = timeTravelIndex != null && timeTravelIndex < totalSessionSpins

    // Calculate Recommendation Result for the current replay point using the resolved progression
    val trendResult = remember(
        isScrubbing,
        spins,
        fullTrendResult,
        isUS,
        effectiveReplayProgression,
        currentActiveFamily,
        currentColdLimit,
        currentColdRule,
        currentLookbackWindow,
        startBankroll,
        baseInsideBet,
        baseOutsideBet,
        currentPrimaryCriterion,
        currentSecondaryCriterion,
        currentEqualizePayout,
        currentPushOrProfit,
        currentRiskCap,
        currentUnderwaterFilter,
        currentTransitionRule,
        currentSwitchSensitivity,
        currentStartMode
    ) {
        if (!isScrubbing) {
            fullTrendResult
        } else {
            BetTrendCalculator.optimize(
            spins = spins,
            isUS = isUS,
            targetProgression = effectiveReplayProgression,
            family = currentActiveFamily,
            coldLimit = currentColdLimit,
            lookbackWindow = currentLookbackWindow,
            startBankroll = startBankroll,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            primaryCriterion = currentPrimaryCriterion,
            secondaryCriterion = currentSecondaryCriterion,
            coldRule = currentColdRule,
            equalizePayout = currentEqualizePayout,
            pushOrProfit = currentPushOrProfit,
            riskCap = currentRiskCap,
            underwaterFilter = currentUnderwaterFilter,
            transitionRule = currentTransitionRule,
            switchSensitivity = currentSwitchSensitivity,
            startMode = currentStartMode
        )
        }
    }

    // Global Optimal Configuration evaluated asynchronously in background across all Progressions, Families, Cold Limits, and Cold Rules
    val globalOptimalConfig by produceState<BetTrendCalculator.GlobalOptimalConfig?>(
        initialValue = null,
        fullSpins,
        totalSessionSpins,
        isUS,
        currentLookbackWindow,
        startBankroll,
        baseInsideBet,
        baseOutsideBet,
        currentPrimaryCriterion,
        currentSecondaryCriterion,
        currentEqualizePayout,
        currentPushOrProfit,
        currentRiskCap,
        currentUnderwaterFilter,
        currentTransitionRule,
        currentSwitchSensitivity,
        currentStartMode
    ) {
        val analysisSpins = if (!fullSpins.isNullOrEmpty()) fullSpins else spins
        value = withContext(Dispatchers.Default) {
            BetTrendCalculator.findGlobalOptimalConfig(
                spins = analysisSpins,
                isUS = isUS,
                lookbackWindow = currentLookbackWindow,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                primaryCriterion = currentPrimaryCriterion,
                secondaryCriterion = currentSecondaryCriterion,
                equalizePayout = currentEqualizePayout,
                pushOrProfit = currentPushOrProfit,
                riskCap = currentRiskCap,
                underwaterFilter = currentUnderwaterFilter,
                transitionRule = currentTransitionRule,
                switchSensitivity = currentSwitchSensitivity,
                startMode = currentStartMode
            )
        }
    }

    val applyOptimalConfig: (BetTrendCalculator.GlobalOptimalConfig) -> Unit = { opt ->
        setProgression(opt.progression)
        if (!opt.progression.isFixedLayout) {
            setFamily(opt.family)
            setColdLimit(opt.coldLimit)
            setColdRule(opt.coldRule)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "optimalFamilyGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    var viewPlayedBetsForReplay by rememberSaveable { mutableStateOf(false) }

    val isAfterLookbackWaiting = currentStartMode == BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK && spins.size < currentLookbackWindow

    // Convert Recommended Bets to PlacedChips with labels for the shared felt
    val recommendedChips = remember(trendResult, isAfterLookbackWaiting) {
        if (isAfterLookbackWaiting || trendResult.totalUnits == 0 || trendResult.nextTotalStake <= 0.0) {
            emptyList()
        } else {
            trendResult.recommendedBets
                .filter { it.units > 0 && it.dollarAmount > 0.0 }
                .mapIndexed { idx, rec ->
                    PlacedChip(
                        id = (idx + 1000L),
                        value = max(1, rec.dollarAmount.roundToInt()),
                        xPercent = rec.bet.xPercent,
                        yPercent = rec.bet.yPercent,
                        betId = rec.bet.betId
                    )
                }
        }
    }

    val chipLabels = remember(trendResult, isAfterLookbackWaiting) {
        if (isAfterLookbackWaiting || trendResult.totalUnits == 0 || trendResult.nextTotalStake <= 0.0) {
            emptyMap()
        } else {
            trendResult.recommendedBets
                .filter { it.units > 0 && it.dollarAmount > 0.0 }
                .mapIndexed { idx, rec ->
                    (idx + 1000L) to "$${max(1, rec.dollarAmount.roundToInt())}"
                }.toMap()
        }
    }

    val currentReplaySpin = timeTravelIndex ?: spins.size
    val currentReplayRecord = remember(fullSpinLogs, currentReplaySpin) {
        fullSpinLogs.find { it.spinIndex == currentReplaySpin }
    }
    val nextReplayRecord = remember(fullSpinLogs, currentReplaySpin) {
        fullSpinLogs.find { it.spinIndex == currentReplaySpin + 1 }
    }
    val isReplayMode = isScrubbing || (spins.size < totalSessionSpins)

    val displayedChips = remember(viewPlayedBetsForReplay, currentReplayRecord, nextReplayRecord, recommendedChips, isReplayMode, baseInsideBet, baseOutsideBet) {
        if (viewPlayedBetsForReplay && currentReplayRecord != null) {
            if (currentReplayRecord.outcome == "SEED" || currentReplayRecord.multiplier <= 0 || currentReplayRecord.stake <= 0.0 || currentReplayRecord.placedBets.isEmpty()) {
                emptyList()
            } else {
                val mult = currentReplayRecord.multiplier
                currentReplayRecord.placedBets.mapIndexed { idx, bet ->
                    val baseBet = if (bet.isInside) baseInsideBet else baseOutsideBet
                    val amount = max(1, (bet.baseUnits * baseBet * mult).roundToInt())
                    PlacedChip(
                        id = (idx + 2000L),
                        value = amount,
                        xPercent = bet.xPercent,
                        yPercent = bet.yPercent,
                        betId = bet.betId
                    )
                }
            }
        } else if (isReplayMode && nextReplayRecord != null) {
            if (nextReplayRecord.outcome == "SEED" || nextReplayRecord.multiplier <= 0 || nextReplayRecord.stake <= 0.0 || nextReplayRecord.placedBets.isEmpty()) {
                emptyList()
            } else {
                val mult = nextReplayRecord.multiplier
                nextReplayRecord.placedBets.mapIndexed { idx, bet ->
                    val baseBet = if (bet.isInside) baseInsideBet else baseOutsideBet
                    val amount = max(1, (bet.baseUnits * baseBet * mult).roundToInt())
                    PlacedChip(
                        id = (idx + 1000L),
                        value = amount,
                        xPercent = bet.xPercent,
                        yPercent = bet.yPercent,
                        betId = bet.betId
                    )
                }
            }
        } else {
            recommendedChips
        }
    }

    val displayedChipLabels = remember(viewPlayedBetsForReplay, currentReplayRecord, nextReplayRecord, chipLabels, isReplayMode, baseInsideBet, baseOutsideBet) {
        if (viewPlayedBetsForReplay && currentReplayRecord != null) {
            if (currentReplayRecord.outcome == "SEED" || currentReplayRecord.multiplier <= 0 || currentReplayRecord.stake <= 0.0 || currentReplayRecord.placedBets.isEmpty()) {
                emptyMap()
            } else {
                val mult = currentReplayRecord.multiplier
                currentReplayRecord.placedBets.mapIndexed { idx, bet ->
                    val baseBet = if (bet.isInside) baseInsideBet else baseOutsideBet
                    val amount = max(1, (bet.baseUnits * baseBet * mult).roundToInt())
                    (idx + 2000L) to "$$amount"
                }.toMap()
            }
        } else if (isReplayMode && nextReplayRecord != null) {
            if (nextReplayRecord.outcome == "SEED" || nextReplayRecord.multiplier <= 0 || nextReplayRecord.stake <= 0.0 || nextReplayRecord.placedBets.isEmpty()) {
                emptyMap()
            } else {
                val mult = nextReplayRecord.multiplier
                nextReplayRecord.placedBets.mapIndexed { idx, bet ->
                    val baseBet = if (bet.isInside) baseInsideBet else baseOutsideBet
                    val amount = max(1, (bet.baseUnits * baseBet * mult).roundToInt())
                    (idx + 1000L) to "$$amount"
                }.toMap()
            }
        } else {
            chipLabels
        }
    }

    val displayedTotalStake = remember(displayedChips) {
        displayedChips.sumOf { it.value }
    }

    val displayedMultiplier = remember(viewPlayedBetsForReplay, currentReplayRecord, nextReplayRecord, trendResult, isReplayMode) {
        if (viewPlayedBetsForReplay && currentReplayRecord != null) {
            currentReplayRecord.multiplier
        } else if (isReplayMode && nextReplayRecord != null) {
            nextReplayRecord.multiplier
        } else {
            trendResult.currentMultiplier
        }
    }

    // Manual Lookback Input Dialog
    if (showLookbackManualDialog) {
        AlertDialog(
            onDismissRequest = { showLookbackManualDialog = false },
            title = {
                Text("Set Lookback Window", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column {
                    Text("Enter number of trailing spins to analyze (5 - 200):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = manualLookbackText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() }) manualLookbackText = input
                            },
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = manualLookbackText.toIntOrNull()
                        if (parsed != null && parsed in 5..200) {
                            setLookback(parsed)
                        }
                        showLookbackManualDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Apply", fontSize = 11.sp, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLookbackManualDialog = false }) {
                    Text("Cancel", fontSize = 11.sp, color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    if (showProgressionExplanationDialog) {
        ProgressionExplanationDialog(
            detail = BetTrendCalculator.getProgressionDetail(globalOptimalConfig?.progression ?: trendResult.bestProgression),
            criteriaInfo = "Optimization Goal: ① ${currentPrimaryCriterion.displayName} (65%) + ② ${currentSecondaryCriterion.displayName} (35%)",
            optimalConfig = globalOptimalConfig,
            onApplyConfig = applyOptimalConfig,
            onApplyFamilyColdConfig = { fam, cold, rule ->
                setProgression(BetTrendCalculator.ProgressionType.AUTO)
                setFamily(fam)
                setColdLimit(cold)
                setColdRule(rule)
            },
            onDismiss = { showProgressionExplanationDialog = false }
        )
    }

    if (showQualityDetailsDialog) {
        val q = fullTrendResult.quality
        AlertDialog(
            onDismissRequest = { showQualityDetailsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📈", fontSize = 16.sp)
                    Text("Trend Quality & Health", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Overall Score card
                    val scoreColor = when {
                        q.score >= 80 -> Color(0xFF10B981)
                        q.score >= 60 -> Color(0xFF0D9488)
                        q.score >= 40 -> Color(0xFFF59E0B)
                        else -> Color(0xFFF43F5E)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(scoreColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, scoreColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Score: ${q.score} / 100",
                                    color = scoreColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = q.summary,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(scoreColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = q.grade,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Metric Breakdowns
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. Linearity
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Linearity (R² Fit):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text("${(q.rSquared * 100).roundToInt()}% (${if (q.slope >= 0) "+" else ""}${"%.2f".format(q.slope)}/spin)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        // 2. Underwater Time
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Underwater (< $1000):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text("${q.totalUnderwaterPercent.roundToInt()}%", fontSize = 11.sp, color = if (q.totalUnderwaterPercent > 30) Color(0xFFF43F5E) else Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        // 3. Recent 50% Underwater Time
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Recent 50% Underwater:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text("${q.recentUnderwaterPercent.roundToInt()}% ${if (q.recentUnderwaterPercent > 30) "⚠️ High" else "✓ Safe"}", fontSize = 11.sp, color = if (q.recentUnderwaterPercent > 30) Color(0xFFF43F5E) else Color(0xFF10B981), fontWeight = FontWeight.Bold)
                        }
                        // 4. Ulcer Index
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Drawdown Stress (Ulcer):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text("${"%.2f".format(q.ulcerIndex)}", fontSize = 11.sp, color = if (q.ulcerIndex < 3.0) Color(0xFF10B981) else if (q.ulcerIndex < 6.0) Color(0xFFF59E0B) else Color(0xFFF43F5E), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Text(
                        text = "Guidance: Smooth upward linearity with low drawdown and minimal underwater time is awarded an A/A+ rating. When Safe Trend is enabled, configurations spending >30% of recent spins underwater are penalized.",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 13.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQualityDetailsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Got It", fontSize = 11.sp, color = Color.White)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // ================= TOP CONTROL TOOLBAR (ULTRA-COMPACT SLIMLINE) =================
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 3-Way Segmented Button: Inside | Outside | Combined (or Fixed Layout indicator)
                if (currentProgression.isFixedLayout) {
                    Row(
                        modifier = Modifier
                            .height(20.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "⚡ Fixed Layout",
                            fontSize = 8.5.sp,
                            color = Color(0xFFFDE68A),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .height(20.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                            .padding(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            Triple(BetTrendCalculator.BetFamily.INSIDE, "Inside", Color(0xFFDC2626)),
                            Triple(BetTrendCalculator.BetFamily.OUTSIDE, "Outside", Color(0xFF2563EB)),
                            Triple(BetTrendCalculator.BetFamily.COMBINED, "Combined", Color(0xFF10B981))
                        ).forEach { (family, label, activeBg) ->
                            val isSel = currentActiveFamily == family
                            val isOptimal = globalOptimalConfig?.let {
                                !it.progression.isFixedLayout && it.family == family
                            } ?: false

                            val glowBorder = if (isOptimal) {
                                Modifier.border(
                                    width = 1.2.dp,
                                    color = Color(0xFFFBBF24).copy(alpha = glowAlpha),
                                    shape = RoundedCornerShape(2.5.dp)
                                )
                            } else {
                                Modifier
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .then(glowBorder)
                                    .background(
                                        if (isSel) activeBg else if (isOptimal) Color(0xFFF59E0B).copy(alpha = 0.18f * glowAlpha) else Color.Transparent,
                                        RoundedCornerShape(2.5.dp)
                                    )
                                    .clickable { setFamily(family) }
                                    .padding(horizontal = if (isOptimal) 4.5.dp else 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (isOptimal) {
                                        Text(
                                            text = "★",
                                            color = Color(0xFFFBBF24).copy(alpha = glowAlpha),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Text(
                                        text = label,
                                        color = if (isSel) Color.White else if (isOptimal) Color(0xFFFDE68A) else Color(0xFF94A3B8),
                                        fontWeight = if (isSel || isOptimal) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { showProgressionExplanationDialog = true }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Family Walk-Forward Settings",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }

                // Progression Selector
                Box {
                    Button(
                        onClick = { showProgressionDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = if (currentProgression.isFixedLayout) Color(0xFF292524) else Color(0xFF334155)),
                        shape = RoundedCornerShape(3.dp),
                        border = if (currentProgression.isFixedLayout) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)) else null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = "Prog: ${currentProgression.displayName.take(11)}",
                            fontSize = 8.5.sp,
                            color = Color(0xFFFCD34D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DropdownMenu(
                        expanded = showProgressionDropdown,
                        onDismissRequest = { showProgressionDropdown = false }
                    ) {
                        BetTrendCalculator.ProgressionType.values().forEach { prog ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(prog.displayName, fontSize = 11.sp)
                                        Text(
                                            if (prog.isFixedLayout) "Fixed Layout" else prog.category,
                                            fontSize = 9.sp,
                                            color = if (prog.isFixedLayout) Color(0xFFF59E0B) else Color.Gray,
                                            fontWeight = if (prog.isFixedLayout) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    setProgression(prog)
                                    showProgressionDropdown = false
                                }
                            )
                        }
                    }
                }

                // Prioritize 2 Criteria Selector (Active when Auto-Optimizing)
                if (currentProgression == BetTrendCalculator.ProgressionType.AUTO) {
                    Row(
                        modifier = Modifier
                            .height(20.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(3.dp))
                            .padding(1.dp),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            BetTrendCalculator.OptimizationCriterion.PNL,
                            BetTrendCalculator.OptimizationCriterion.HIT_RATE,
                            BetTrendCalculator.OptimizationCriterion.MAX_DD,
                            BetTrendCalculator.OptimizationCriterion.QUALITY
                        ).forEach { crit ->
                            val isPrimary = crit == currentPrimaryCriterion
                            val isSecondary = crit == currentSecondaryCriterion
                            val isSelected = isPrimary || isSecondary
                            val badge = if (isPrimary) "① " else if (isSecondary) "② " else ""
                            val bg = when {
                                isPrimary -> Color(0xFF10B981)
                                isSecondary -> Color(0xFF0284C7)
                                else -> Color.Transparent
                            }
                            val textColor = when {
                                isSelected -> Color.White
                                else -> Color(0xFF64748B)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(bg, RoundedCornerShape(2.5.dp))
                                    .clickable {
                                        if (crit == currentPrimaryCriterion) {
                                            val prev = currentPrimaryCriterion
                                            setPrimaryCriterion(currentSecondaryCriterion)
                                            setSecondaryCriterion(prev)
                                        } else if (crit == currentSecondaryCriterion) {
                                            val prev = currentPrimaryCriterion
                                            setPrimaryCriterion(currentSecondaryCriterion)
                                            setSecondaryCriterion(prev)
                                        } else {
                                            setSecondaryCriterion(crit)
                                        }
                                    }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$badge${crit.shortName}",
                                    color = textColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Cold Banned Filter
                Box {
                    Button(
                        onClick = { showColdDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = if (currentColdLimit == 0) "Cold: Off" else "Cold: ${currentColdLimit} Banned",
                            fontSize = 8.5.sp,
                            color = Color(0xFF67E8F9),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DropdownMenu(
                        expanded = showColdDropdown,
                        onDismissRequest = { showColdDropdown = false }
                    ) {
                        listOf(0, 3, 5, 7).forEach { count ->
                            DropdownMenuItem(
                                text = { Text(if (count == 0) "Disabled (0)" else "$count Coldest Banned", fontSize = 11.sp) },
                                onClick = {
                                    setColdLimit(count)
                                    showColdDropdown = false
                                }
                            )
                        }
                    }
                }

                // Cold Avoidance Rule Selector (Option 1 UI: next to Cold: 5 chip)
                if (currentColdLimit > 0) {
                    Box {
                        Button(
                            onClick = { showColdRuleDropdown = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (currentColdRule) {
                                    BetTrendCalculator.ColdRule.STRICT -> Color(0xFF334155)
                                    BetTrendCalculator.ColdRule.PENALTY -> Color(0xFF0284C7)
                                    BetTrendCalculator.ColdRule.WATERFALL -> Color(0xFF0D9488)
                                    BetTrendCalculator.ColdRule.HYBRID -> Color(0xFF7C3AED)
                                }
                            ),
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = "Rule: ${currentColdRule.shortName} ▾",
                                fontSize = 8.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        DropdownMenu(
                            expanded = showColdRuleDropdown,
                            onDismissRequest = { showColdRuleDropdown = false }
                        ) {
                            BetTrendCalculator.ColdRule.values().forEach { rule ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = rule.displayName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (rule == currentColdRule) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (rule == currentColdRule) Color(0xFF38BDF8) else Color.White
                                                )
                                                if (rule == currentColdRule) {
                                                    Text(" ✓", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(
                                                text = rule.description,
                                                fontSize = 8.5.sp,
                                                color = Color(0xFF94A3B8),
                                                lineHeight = 11.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        setColdRule(rule)
                                        showColdRuleDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Equalize Payout / Dutching Chip (Inside bets: all bets win same amount)
                Button(
                    onClick = { setEqualizePayout(!currentEqualizePayout) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentEqualizePayout) Color(0xFFF59E0B) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        text = if (currentEqualizePayout) "⚖️ Equalized: On" else "⚖️ Equalize",
                        fontSize = 8.5.sp,
                        color = if (currentEqualizePayout) Color.Black else Color(0xFFCBD5E1),
                        fontWeight = if (currentEqualizePayout) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                // Push or Profit Only Chip (Outside/Combined: zero partial losses)
                Button(
                    onClick = { setPushOrProfit(!currentPushOrProfit) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentPushOrProfit) Color(0xFF10B981) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        text = if (currentPushOrProfit) "🛡️ Push/Win: On" else "🛡️ Push/Win",
                        fontSize = 8.5.sp,
                        color = if (currentPushOrProfit) Color.Black else Color(0xFFCBD5E1),
                        fontWeight = if (currentPushOrProfit) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                // Option A: Deficit-Aware Cap & 20% Bankroll Risk Ceiling
                Button(
                    onClick = { setRiskCap(!currentRiskCap) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentRiskCap) Color(0xFF6366F1) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        text = if (currentRiskCap) "🎯 Risk Cap: On" else "🎯 Risk Cap",
                        fontSize = 8.5.sp,
                        color = if (currentRiskCap) Color.White else Color(0xFFCBD5E1),
                        fontWeight = if (currentRiskCap) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                // Underwater Hard Filter / Safeguard Toggle
                Button(
                    onClick = { setUnderwaterFilter(!currentUnderwaterFilter) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentUnderwaterFilter) Color(0xFF0D9488) else Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Text(
                        text = if (currentUnderwaterFilter) "🛡️ Safe Trend: On" else "🛡️ Safe Trend",
                        fontSize = 8.5.sp,
                        color = if (currentUnderwaterFilter) Color.White else Color(0xFFCBD5E1),
                        fontWeight = if (currentUnderwaterFilter) FontWeight.Bold else FontWeight.SemiBold
                    )
                }

                // Betting Start Mode Chip (Spin 1 vs After Lookback)
                var showStartModeDropdown by remember { mutableStateOf(false) }
                Box {
                    Button(
                        onClick = { showStartModeDropdown = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentStartMode == BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK) Color(0xFFD97706) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = if (currentStartMode == BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK) "⏳ After Lookback" else "🎬 Spin 1",
                            fontSize = 8.5.sp,
                            color = if (currentStartMode == BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK) Color.White else Color(0xFFCBD5E1),
                            fontWeight = if (currentStartMode == BetTrendCalculator.BettingStartMode.AFTER_LOOKBACK) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }

                    DropdownMenu(
                        expanded = showStartModeDropdown,
                        onDismissRequest = { showStartModeDropdown = false },
                        modifier = Modifier.background(Color(0xFF1E293B))
                    ) {
                        BetTrendCalculator.BettingStartMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            color = if (mode == currentStartMode) Color(0xFFF59E0B) else Color.White,
                                            fontWeight = if (mode == currentStartMode) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = mode.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 8.5.sp
                                        )
                                    }
                                },
                                onClick = {
                                    setStartMode(mode)
                                    showStartModeDropdown = false
                                }
                            )
                        }
                    }
                }

                if (currentProgression == BetTrendCalculator.ProgressionType.AUTO) {
                    // Transition Rule Selector Chip
                    Box {
                        Button(
                            onClick = { showTransitionRuleDropdown = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (currentTransitionRule == BetTrendCalculator.SwitchTransitionRule.COMPLETE_CYCLE) Color(0xFF7C3AED) else Color(0xFF475569)
                            ),
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = "🔄 ${currentTransitionRule.shortName}",
                                fontSize = 8.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showTransitionRuleDropdown,
                            onDismissRequest = { showTransitionRuleDropdown = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            BetTrendCalculator.SwitchTransitionRule.values().forEach { rule ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = rule.displayName,
                                                color = if (rule == currentTransitionRule) Color(0xFFA78BFA) else Color.White,
                                                fontWeight = if (rule == currentTransitionRule) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = rule.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        setTransitionRule(rule)
                                        showTransitionRuleDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    // Switch Sensitivity Selector Chip
                    Box {
                        Button(
                            onClick = { showSensitivityDropdown = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (currentSwitchSensitivity) {
                                    BetTrendCalculator.SwitchSensitivity.AGGRESSIVE -> Color(0xFFDB2777)
                                    BetTrendCalculator.SwitchSensitivity.MODERATE -> Color(0xFF2563EB)
                                    BetTrendCalculator.SwitchSensitivity.CONSERVATIVE -> Color(0xFF059669)
                                }
                            ),
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Text(
                                text = "🎯 ${currentSwitchSensitivity.shortName}",
                                fontSize = 8.5.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        DropdownMenu(
                            expanded = showSensitivityDropdown,
                            onDismissRequest = { showSensitivityDropdown = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            BetTrendCalculator.SwitchSensitivity.values().forEach { sens ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = sens.displayName,
                                                color = if (sens == currentSwitchSensitivity) Color(0xFF60A5FA) else Color.White,
                                                fontWeight = if (sens == currentSwitchSensitivity) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = sens.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 8.5.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        setSwitchSensitivity(sens)
                                        showSensitivityDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // One-Tap Apply to Felt Button
                Button(
                    onClick = { onApplyBetsToFelt(recommendedChips) },
                    enabled = recommendedChips.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        disabledContainerColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(3.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = if (recommendedChips.isNotEmpty()) Color.Black else Color.Gray
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = if (isAfterLookbackWaiting) "Observing Seeds" else "Apply Bets",
                        color = if (recommendedChips.isNotEmpty()) Color.Black else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }

                // Lookback Slider + Interactive Tap Badge (1-spin increments)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Lookback:",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Tappable badge for manual input
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .clickable {
                                manualLookbackText = currentLookbackWindow.toString()
                                showLookbackManualDialog = true
                            }
                            .padding(horizontal = 4.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = "${currentLookbackWindow}s",
                            color = Color(0xFFFBBF24),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Ultra-compact 1-spin incremental slider (valueRange 5 to 100)
                    CompactLookbackSlider(
                        value = currentLookbackWindow,
                        onValueChange = { setLookback(it) },
                        valueRange = 5..100,
                        modifier = Modifier.width(70.dp).height(14.dp)
                    )
                }
            }
        }


        Spacer(Modifier.height(4.dp))

        // ================= SPLIT VIEWPORT (BOTH SCROLLABLE VERTICALLY) =================
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---------- LEFT SECTION (SCROLLABLE VERTICALLY) ----------
            Column(
                modifier = Modifier
                    .weight(1.22f)
                    .fillMaxHeight()
                    .verticalScroll(effectiveLeftScrollState),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Telemetry Cards Strip: 2 Distinct Sets of Stats (snug spacing, no gap)
                val currentReplaySpin = timeTravelIndex ?: spins.size
                val totalLoadedSpins = fullSpins?.size ?: spins.size
                val isReplaying = (timeTravelIndex != null && timeTravelIndex < totalLoadedSpins) || (spins.size < totalLoadedSpins)

                val badgeWidth = 76.dp
                val cardRowHeight = 22.dp

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                ) {
                    // Set 1: Replay Position Stats (dynamically updates as scrubber changes)
                    if (isReplaying) {
                        val replayPnl = trendResult.netProfit.roundToInt()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardRowHeight),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Replay Row Badge
                            Box(
                                modifier = Modifier
                                    .width(badgeWidth)
                                    .fillMaxHeight()
                                    .background(Color(0xFF78350F).copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⏱ REPLAY #$currentReplaySpin",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            CompactTelemetryCard(
                                label = "Bank",
                                value = "$${trendResult.endingBankroll.roundToInt()}",
                                valueColor = Color.White,
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            CompactTelemetryCard(
                                label = "P/L",
                                value = "${if (replayPnl >= 0) "+$" else "-$"}${abs(replayPnl)}",
                                valueColor = if (replayPnl >= 0) Color(0xFF10B981) else Color(0xFFF43F5E),
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            CompactTelemetryCard(
                                label = "Hit",
                                value = "${String.format("%.1f", trendResult.winRatePercent)}%",
                                valueColor = Color(0xFF34D399),
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            CompactTelemetryCard(
                                label = "MaxDD",
                                value = "-$${trendResult.maxDrawdown.roundToInt()}",
                                valueColor = Color(0xFFF87171),
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            CompactTelemetryCard(
                                label = "Max Stk",
                                value = "$${trendResult.maxStake.roundToInt()}",
                                valueColor = Color(0xFFFB923C),
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1f)
                            )
                            CompactTelemetryCard(
                                label = "Stake",
                                value = "$$displayedTotalStake (${displayedMultiplier}x)",
                                valueColor = Color(0xFFFBBF24),
                                borderColor = Color(0xFFD97706).copy(alpha = 0.45f),
                                modifier = Modifier.weight(1.15f)
                            )
                        }
                    }

                    // Set 2: All Spins Loaded Stats (constant full session stats based on last spin recommendation)
                    val fullPnl = fullTrendResult.netProfit.roundToInt()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cardRowHeight),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Full Session / All Spins Badge
                        Box(
                            modifier = Modifier
                                .width(badgeWidth)
                                .fillMaxHeight()
                                .background(Color(0xFF0F2942), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "● ALL SPINS ($totalLoadedSpins)",
                                color = Color(0xFF38BDF8),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        CompactTelemetryCard(
                            label = "Bank",
                            value = "$${fullTrendResult.endingBankroll.roundToInt()}",
                            valueColor = Color.White,
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactTelemetryCard(
                            label = "P/L",
                            value = "${if (fullPnl >= 0) "+$" else "-$"}${abs(fullPnl)}",
                            valueColor = if (fullPnl >= 0) Color(0xFF10B981) else Color(0xFFF43F5E),
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactTelemetryCard(
                            label = "Hit",
                            value = "${String.format("%.1f", fullTrendResult.winRatePercent)}%",
                            valueColor = Color(0xFF34D399),
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactTelemetryCard(
                            label = "MaxDD",
                            value = "-$${fullTrendResult.maxDrawdown.roundToInt()}",
                            valueColor = Color(0xFFF87171),
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactTelemetryCard(
                            label = "Max Stk",
                            value = "$${fullTrendResult.maxStake.roundToInt()}",
                            valueColor = Color(0xFFFB923C),
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1f)
                        )
                        CompactTelemetryCard(
                            label = "Stake",
                            value = "$${fullTrendResult.nextTotalStake.roundToInt()} (${fullTrendResult.currentMultiplier}x)",
                            valueColor = Color(0xFFFBBF24),
                            borderColor = Color(0xFF0284C7).copy(alpha = 0.45f),
                            modifier = Modifier.weight(1.15f)
                        )
                    }
                }

                // Cold Exclusions Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("Cold Avoided: ", color = Color(0xFF06B6D4), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    if (trendResult.coldBannedNumbers.isEmpty()) {
                        Text("None", color = Color.Gray, fontSize = 9.sp)
                    } else {
                        trendResult.coldBannedNumbers.forEach { n ->
                            Box(
                                modifier = Modifier
                                    .height(16.dp)
                                    .background(Color(0xFF083344), RoundedCornerShape(2.dp))
                                    .border(1.dp, Color(0xFF06B6D4), RoundedCornerShape(2.dp))
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("#$n", color = Color(0xFF67E8F9), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        modifier = Modifier
                            .height(16.dp)
                            .background(Color(0xFF0F172A), RoundedCornerShape(2.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            .clickable { showProgressionExplanationDialog = true }
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val bestSummary = globalOptimalConfig?.summaryText
                            ?: (fullTrendResult.idealProgression ?: fullTrendResult.bestProgression).displayName
                        Text(
                            text = "★ Best: $bestSummary (①${currentPrimaryCriterion.shortName} ②${currentSecondaryCriterion.shortName})",
                            color = Color(0xFFFBBF24),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }

                // Shared Felt Viewport (Read-Only Recommendation Mode with Header inside Green Felt)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF064E3B), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF047857), RoundedCornerShape(6.dp))
                        .padding(start = 3.dp, end = 3.dp, top = 1.5.dp, bottom = 3.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 0.5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewPlayedBetsForReplay && spins.isNotEmpty()) {
                                    if (currentReplayRecord?.outcome == "SEED" || currentReplayRecord?.stake == 0.0) "Spin #$currentReplaySpin (Lookback Observation - No Bets)"
                                    else "Bets Placed on Spin #$currentReplaySpin"
                                } else if (isReplaying) {
                                    if (nextReplayRecord?.outcome == "SEED" || nextReplayRecord?.stake == 0.0) "Spin #${currentReplaySpin + 1} (Lookback Observation - No Bets)"
                                    else "Recommended Bets for Next Spin (#${currentReplaySpin + 1})"
                                } else {
                                    if (isAfterLookbackWaiting) "Observing Lookback Window (No Bets Yet)"
                                    else if (currentProgression.isFixedLayout) "Recommended Bets (${currentProgression.displayName} • Fixed Layout)"
                                    else "Recommended Bets (Upcoming Spin #${spins.size + 1})"
                                },
                                color = Color(0xFFE2E8F0),
                                fontSize = 8.5.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(Modifier.width(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Total Stake Badge inside the Felt Header (updates for Played # vs Next #)
                                Box(
                                    modifier = Modifier
                                        .height(14.dp)
                                        .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                                        .border(1.dp, Color(0xFF4ADE80).copy(alpha = 0.75f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp, vertical = 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (displayedMultiplier > 1) "Total Stake: $$displayedTotalStake (${displayedMultiplier}x)" else "Total Stake: $$displayedTotalStake",
                                        color = Color(0xFF4ADE80),
                                        fontSize = 7.5.sp,
                                        lineHeight = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        style = TextStyle(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }

                                if (spins.isNotEmpty()) {
                                    // Swapped order: "Played #..." on LEFT, "Next #..." on RIGHT
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .background(
                                                if (viewPlayedBetsForReplay) Color(0xFFB45309) else Color(0xFF1E293B),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (viewPlayedBetsForReplay) Color(0xFFFBBF24) else Color(0xFF475569),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable { viewPlayedBetsForReplay = true }
                                            .padding(horizontal = 5.dp, vertical = 0.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Played #${currentReplaySpin}",
                                            color = if (viewPlayedBetsForReplay) Color.White else Color(0xFF94A3B8),
                                            fontSize = 7.5.sp,
                                            lineHeight = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(14.dp)
                                            .background(
                                                if (!viewPlayedBetsForReplay) Color(0xFF047857) else Color(0xFF1E293B),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (!viewPlayedBetsForReplay) Color(0xFF34D399) else Color(0xFF475569),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable { viewPlayedBetsForReplay = false }
                                            .padding(horizontal = 5.dp, vertical = 0.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Next #${currentReplaySpin + 1}",
                                            color = if (!viewPlayedBetsForReplay) Color.White else Color(0xFF94A3B8),
                                            fontSize = 7.5.sp,
                                            lineHeight = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SharedRouletteFelt(
                        tableType = tableType,
                        heatmapStats = heatmapStats,
                        hotOverrides = hotOverrides,
                        coldOverrides = coldOverrides,
                        chips = displayedChips,
                        coldBannedNumbers = trendResult.coldBannedNumbers.toSet(),
                        lastSpunNumber = lastSpunNumber,
                        heatMode = heatMode,
                        numberWeights = trendResult.numberWeights,
                        outsideHeatMap = outsideHeatMap,
                        isInteractive = false,
                        chipSize = 17.dp,
                        chipLabels = displayedChipLabels,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Wheel Rotor Heat Radar (Concept 1: Placed below where the felt is now, scrollable)
                WheelRotorRadarCard(
                    tableType = tableType,
                    numberWeights = trendResult.numberWeights,
                    coldBannedNumbers = trendResult.coldBannedNumbers.toSet(),
                    lastSpunNumber = lastSpunNumber,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- RIGHT SECTION (SCROLLABLE VERTICALLY WITH RESIZABLE CARDS) ----------
            Column(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .verticalScroll(effectiveRightScrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ================= RESIZABLE BANKROLL EQUITY CARD =================
                val effectiveScrubSpin = timeTravelIndex
                    ?: fullEquityPoints.lastOrNull()?.spinIndex
                    ?: fullSpinLogs.firstOrNull()?.spinIndex
                val scrubbedRecord = remember(effectiveScrubSpin, fullSpinLogs) {
                    if (effectiveScrubSpin != null) {
                        fullSpinLogs.find { it.spinIndex == effectiveScrubSpin }
                            ?: fullSpinLogs.firstOrNull { it.spinIndex <= effectiveScrubSpin }
                    } else {
                        fullSpinLogs.firstOrNull()
                    }
                }
                val scrubbedProgressionName = scrubbedRecord?.progressionUsed?.takeIf { it.isNotBlank() }
                    ?: fullTrendResult.bestProgression.displayName

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentEquityChartHeight)
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                        .padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Chart Header (2 Rows Bar)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 1.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Row 1: Title + Rating Badge on Left | Bankroll Stats + Zoom +/- on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Equity Trajectory",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val quality = fullTrendResult.quality
                                    val badgeColor = when {
                                        quality.score >= 80 -> Color(0xFF10B981) // Emerald
                                        quality.score >= 60 -> Color(0xFF0D9488) // Teal
                                        quality.score >= 40 -> Color(0xFFF59E0B) // Amber
                                        else -> Color(0xFFF43F5E) // Red
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(18.dp)
                                            .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                            .clickable { showQualityDetailsDialog = true }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "📈 ${quality.score} (${quality.grade})",
                                                color = badgeColor,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (quality.recentUnderwaterPercent > 30.0) {
                                                Text(
                                                    text = "⚠️",
                                                    fontSize = 7.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = "Base: $${startBankroll.roundToInt()}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "•",
                                            color = Color(0xFF64748B),
                                            fontSize = 7.sp
                                        )
                                        Text(
                                            text = "End: $${fullTrendResult.endingBankroll.roundToInt()}",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 7.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    // Height +/- resize controls permanently anchored within chart frame
                                    ResizeButtons(
                                        currentHeight = currentEquityChartHeight,
                                        onHeightChange = { setEquityChartHeight(it) },
                                        minHeight = 90.dp,
                                        maxHeight = 350.dp,
                                        step = 30.dp
                                    )
                                }
                            }

                            // Row 2: Ideal Benchmark Button + Info on Left | Active Scrubbed Progression on Right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val idealProgName = globalOptimalConfig?.progression?.displayName
                                    ?: (fullTrendResult.idealProgression ?: fullTrendResult.bestProgression).displayName
                                val conditionText = globalOptimalConfig?.conditionRowText ?: "Current Config"

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .height(18.dp)
                                            .background(
                                                if (currentShowIdealOverlay) Color(0xFFF59E0B) else Color(0xFF1E293B),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .border(
                                                1.dp,
                                                if (currentShowIdealOverlay) Color(0xFFFBBF24) else Color(0xFFF59E0B).copy(alpha = 0.5f),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable { setShowIdealOverlay(!currentShowIdealOverlay) }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Text(
                                                text = if (currentShowIdealOverlay) "⭐ Ideal: $idealProgName [ON]" else "⭐ Ideal: $idealProgName",
                                                fontSize = 7.2.sp,
                                                color = if (currentShowIdealOverlay) Color.Black else Color(0xFFFDE68A),
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = "• $conditionText",
                                                fontSize = 6.5.sp,
                                                color = if (currentShowIdealOverlay) Color(0xFF1E293B) else Color(0xFF94A3B8),
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(18.dp)
                                            .background(Color(0xFF0F172A), RoundedCornerShape(3.dp))
                                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                            .clickable { showProgressionExplanationDialog = true }
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Inspect Family Settings",
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                // Right: Progression used at current replay/session
                                Box(
                                    modifier = Modifier
                                        .height(18.dp)
                                        .background(Color(0xFF1E293B), RoundedCornerShape(3.dp))
                                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scrubbedProgressionName,
                                        color = Color(0xFF38BDF8),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                        BankrollEquityChart(
                            points = fullEquityPoints,
                            idealPoints = if (currentShowIdealOverlay) (globalOptimalConfig?.idealEquityPoints ?: fullTrendResult.idealEquityPoints) else null,
                            idealProgression = globalOptimalConfig?.progression ?: fullTrendResult.idealProgression,
                            startBankroll = startBankroll,
                            timeTravelIndex = effectiveScrubSpin,
                            onTimeTravelChange = { idx ->
                                onTimeTravelChange?.invoke(idx)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }

                // ================= RESIZABLE DETAILED SPIN LEDGER CARD =================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentLedgerTableHeight)
                        .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // Table Header with Resize buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Detailed Spin Ledger", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Text("(${fullSpinLogs.size} Rounds)", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                            }
                            ResizeButtons(
                                currentHeight = currentLedgerTableHeight,
                                onHeightChange = { setLedgerTableHeight(it) },
                                minHeight = 110.dp,
                                maxHeight = 500.dp,
                                step = 40.dp
                            )
                        }

                        // Column Titles (Compressed to single row)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(17.dp)
                                .background(Color(0xFF182234))
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Spin", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(26.dp))
                            Text("Hit", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(22.dp))
                            Text("Bets", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
                            Text("Prog", color = Color(0xFFFCD34D), fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(56.dp), textAlign = TextAlign.Start)
                            Text("Stake", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                            Text("P/L", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)
                            Text("Result", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                            Text("Bank", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
                        }

                        val ledgerListState = rememberLazyListState()

                        LaunchedEffect(timeTravelIndex, fullSpinLogs.size) {
                            val targetSpin = timeTravelIndex ?: fullSpinLogs.firstOrNull()?.spinIndex
                            if (targetSpin != null && fullSpinLogs.isNotEmpty()) {
                                val targetIdx = fullSpinLogs.indexOfFirst { it.spinIndex == targetSpin }
                                if (targetIdx >= 0) {
                                    val visibleInfo = ledgerListState.layoutInfo.visibleItemsInfo
                                    val isFullyVisible = visibleInfo.any {
                                        it.index == targetIdx &&
                                        it.offset >= ledgerListState.layoutInfo.viewportStartOffset &&
                                        (it.offset + it.size) <= ledgerListState.layoutInfo.viewportEndOffset
                                    }
                                    if (!isFullyVisible) {
                                        val halfVisible = (visibleInfo.size / 2).coerceAtLeast(1)
                                        val scrollTarget = (targetIdx - halfVisible).coerceAtLeast(0)
                                        ledgerListState.scrollToItem(scrollTarget)
                                    }
                                }
                            }
                        }

                        if (fullSpinLogs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No spins evaluated yet.", color = Color.DarkGray, fontSize = 9.sp)
                            }
                        } else {
                            LazyColumn(
                                state = ledgerListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(fullSpinLogs) { log ->
                                    val isCurrentTimeTravelSpin = log.spinIndex == (timeTravelIndex ?: fullEquityPoints.lastOrNull()?.spinIndex)
                                    SpinLedgerRow(
                                        log = log,
                                        isHighlighted = isCurrentTimeTravelSpin,
                                        onClick = { onTimeTravelChange?.invoke(log.spinIndex) }
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

/**
 * Compact resize stepper buttons for cards (+ / - height)
 */
@Composable
private fun ResizeButtons(
    currentHeight: Dp,
    onHeightChange: (Dp) -> Unit,
    minHeight: Dp,
    maxHeight: Dp,
    step: Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // Minus Button
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                .clickable {
                    if (currentHeight - step >= minHeight) onHeightChange(currentHeight - step)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("-", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        // Plus Button
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(Color(0xFF334155), RoundedCornerShape(2.dp))
                .clickable {
                    if (currentHeight + step <= maxHeight) onHeightChange(currentHeight + step)
                },
            contentAlignment = Alignment.Center
        ) {
            Text("+", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CompactTelemetryCard(
    label: String,
    value: String,
    valueColor: Color,
    borderColor: Color = Color(0xFF334155),
    modifier: Modifier = Modifier
) {
    val combinedLength = label.length + value.length
    val initialScale = when {
        combinedLength >= 13 -> 0.75f
        combinedLength >= 10 -> 0.85f
        else -> 1.0f
    }
    var scaleFactor by remember(value, label) { mutableFloatStateOf(initialScale) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 2.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((2 * scaleFactor).dp)
        ) {
            Text(
                text = label.uppercase(),
                color = Color(0xFF64748B),
                fontSize = (7.5f * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayoutResult ->
                    if ((textLayoutResult.hasVisualOverflow || textLayoutResult.didOverflowWidth) && scaleFactor > 0.60f) {
                        scaleFactor *= 0.88f
                    }
                }
            )
            Text(
                text = value,
                color = valueColor,
                fontSize = (9.0f * scaleFactor).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayoutResult ->
                    if ((textLayoutResult.hasVisualOverflow || textLayoutResult.didOverflowWidth) && scaleFactor > 0.60f) {
                        scaleFactor *= 0.88f
                    }
                }
            )
        }
    }
}

@Composable
private fun SpinLedgerRow(
    log: BetTrendCalculator.SpinLedgerRecord,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isGreen = log.spunNumber == 0 || log.spunNumber == 37
    val isRed = !isGreen && BetTrendCalculator.RED_NUMS.contains(log.spunNumber)
    val numBg = if (isGreen) Color(0xFF15803D) else if (isRed) Color(0xFFDC2626) else Color(0xFF1E293B)
    val isSeed = log.outcome == "SEED"
    val outcomeBg = if (isSeed) Color(0xFF1E293B) else if (log.outcome == "WIN") Color(0xFF065F46) else if (log.outcome == "LOSS") Color(0xFF991B1B) else Color(0xFF334155)
    val outcomeTxt = if (isSeed) Color(0xFF94A3B8) else if (log.outcome == "WIN") Color(0xFF34D399) else if (log.outcome == "LOSS") Color(0xFFF87171) else Color(0xFF94A3B8)
    val netTxt = if (isSeed) Color(0xFF64748B) else if (log.netGain > 0) Color(0xFF34D399) else if (log.netGain < 0) Color(0xFFF87171) else Color.Gray

    val rowBg = if (isHighlighted) Color(0xFF78350F).copy(alpha = 0.55f) else Color.Transparent

    var showBetsTooltip by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .border(
                if (isHighlighted) 1.dp else 0.dp,
                if (isHighlighted) Color(0xFFF59E0B).copy(alpha = 0.65f) else Color.Transparent,
                RoundedCornerShape(2.dp)
            )
            .pointerInput(log) {
                detectTapGestures(
                    onPress = {
                        showBetsTooltip = true
                        onClick?.invoke()
                        tryAwaitRelease()
                        showBetsTooltip = false
                    }
                )
            }
            .padding(horizontal = 6.dp, vertical = 1.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "#${log.spinIndex}",
                color = if (isHighlighted) Color(0xFFFCD34D) else Color(0xFF94A3B8),
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                fontSize = 8.sp,
                modifier = Modifier.width(26.dp)
            )

            // Spun Number Pill
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(numBg, RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (log.spunNumber == 37) "00" else "${log.spunNumber}",
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(6.dp))

            // Active Bets Summary text
            Text(
                log.activeBetsSummary,
                color = Color(0xFFE2E8F0),
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(4.dp))

            // Prog Column with Switch & Pending Indicators
            val progDisplayName = buildString {
                if (isSeed) {
                    append("👀 Seed")
                } else {
                    if (log.switchOccurred) append("🔀 ")
                    else if (log.isCyclePending) append("⏳ ")
                    append(log.progressionUsed.ifEmpty { "Flat" })
                    if (log.rawMultiplier > log.multiplier) append(" 🎯")
                }
            }
            val progTextColor = when {
                isSeed -> Color(0xFF94A3B8)
                log.switchOccurred -> Color(0xFF38BDF8)
                log.isCyclePending -> Color(0xFFFDE047)
                log.rawMultiplier > log.multiplier -> Color(0xFFA5B4FC)
                else -> Color(0xFFFCD34D)
            }
            Text(
                text = progDisplayName,
                color = progTextColor,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(56.dp)
            )

            // Stake
            Text(
                if (isSeed) "$0" else "$${log.stake.roundToInt()}",
                color = if (isSeed) Color(0xFF94A3B8) else Color(0xFFFCD34D),
                fontSize = 8.sp,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.End
            )

            // Net P/L
            val netStr = if (isSeed) "$0" else "${if (log.netGain >= 0) "+$" else "-$"}${abs(log.netGain.roundToInt())}"
            Text(netStr, color = netTxt, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.End)

            // Outcome Badge
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .padding(horizontal = 1.dp)
                    .background(outcomeBg, RoundedCornerShape(2.dp))
                    .then(if (isSeed) Modifier.border(0.5.dp, Color(0xFF475569), RoundedCornerShape(2.dp)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(log.outcome, color = outcomeTxt, fontSize = 7.sp, fontWeight = FontWeight.Bold)
            }

            // Bankroll
            Text("$${log.bankroll.roundToInt()}", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(38.dp), textAlign = TextAlign.End)
        }

        if (showBetsTooltip) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = androidx.compose.ui.unit.IntOffset(0, -110),
                properties = PopupProperties(focusable = false)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(min = 190.dp, max = 320.dp)
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
                                "Spin #${log.spinIndex} Bets",
                                color = Color(0xFF38BDF8),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Stake: $${log.stake.roundToInt()} (${log.multiplier}x)",
                                color = Color(0xFFFCD34D),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = log.activeBetsSummary,
                            color = Color.White,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 13.sp
                        )
                        if (log.coldAvoided.isNotEmpty() && log.coldAvoided != "None") {
                            Text(
                                text = "Cold Avoided: ${log.coldAvoided}",
                                color = Color(0xFF67E8F9),
                                fontSize = 8.5.sp
                            )
                        }
                        Text(
                            text = "Progression: ${log.progressionUsed.ifEmpty { "Flat Betting" }}",
                            color = Color(0xFFFBBF24),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (log.switchNote.isNotEmpty()) {
                            Text(
                                text = log.switchNote,
                                color = if (log.switchOccurred) Color(0xFF38BDF8) else Color(0xFFFDE047),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (log.isCyclePending) {
                            Text(
                                text = "⏳ Switch pending completion of current progression cycle",
                                color = Color(0xFFFDE047),
                                fontSize = 8.sp
                            )
                        }
                        if (log.rawMultiplier > log.multiplier) {
                            Text(
                                text = "🎯 Risk Capped from ${log.rawMultiplier}x down to ${log.multiplier}x (Deficit + 20% Bankroll Limit)",
                                color = Color(0xFF818CF8),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankrollEquityChart(
    points: List<BetTrendCalculator.EquityPoint>,
    idealPoints: List<BetTrendCalculator.EquityPoint>? = null,
    idealProgression: BetTrendCalculator.ProgressionType? = null,
    startBankroll: Double,
    timeTravelIndex: Int? = null,
    onTimeTravelChange: ((Int?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No equity data points", color = Color.Gray, fontSize = 9.sp)
        }
        return
    }

    var activePoint by remember { mutableStateOf<BetTrendCalculator.EquityPoint?>(null) }

    val density = LocalDensity.current

    val bankrolls = remember(points, idealPoints, startBankroll) {
        points.map { it.bankroll } + (idealPoints?.map { it.bankroll } ?: emptyList())
    }
    val minB = minOf(startBankroll, bankrolls.minOrNull() ?: startBankroll)
    val maxB = maxOf(startBankroll, bankrolls.maxOrNull() ?: startBankroll)
    val yTicks = remember(minB, maxB) {
        listOf(maxB, (maxB + minB) / 2.0, minB)
    }

    // Dynamic density-scaled typography
    val yTextSizePx = with(density) { 9.5f.sp.toPx() }
    val xTextSizePx = with(density) { 8.5f.sp.toPx() }
    val timeTagTextSizePx = with(density) { 8.5f.sp.toPx() }

    val yTextPaint = remember(yTextSizePx) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            textSize = yTextSizePx
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }
    }

    // Measure max tick label width dynamically so text never overflows the left boundary
    val maxTickTextWidth = remember(yTicks, yTextPaint) {
        yTicks.maxOfOrNull { tickVal ->
            yTextPaint.measureText("$${tickVal.roundToInt()}")
        } ?: 0f
    }

    val textGap = with(density) { 2.dp.toPx() }
    val leftMargin = with(density) { 1.dp.toPx() }
    val padLeft = leftMargin + maxTickTextWidth + textGap
    val padRight = with(density) { 6.dp.toPx() }
    val padTop = with(density) { 8.dp.toPx() }
    val padBottom = with(density) { 15.dp.toPx() }

    fun findClosestPoint(touchX: Float, width: Float): BetTrendCalculator.EquityPoint {
        if (points.size == 1) return points[0]
        val chartWidth = width - padLeft - padRight
        val frac = ((touchX - padLeft) / chartWidth).coerceIn(0f, 1f)
        val idx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
        return points[idx]
    }

    Box(
        modifier = modifier
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val closest = findClosestPoint(down.position.x, size.width.toFloat())
                    activePoint = closest
                    onTimeTravelChange?.invoke(closest.spinIndex)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            val pt = findClosestPoint(change.position.x, size.width.toFloat())
                            activePoint = pt
                            onTimeTravelChange?.invoke(pt.spinIndex)
                        }
                    } while (event.changes.any { it.pressed })
                    // Disappears immediately when finger lifts
                    activePoint = null
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val ySpan = max(50.0, maxB - minB)
            val yMin = minB - (ySpan * 0.04)
            val yMax = maxB + (ySpan * 0.04)
            val yRange = max(10.0, yMax - yMin)

            fun getX(idx: Int): Float {
                return padLeft + (idx.toFloat() / max(1, points.size - 1)) * (w - padLeft - padRight)
            }

            fun getY(b: Double): Float {
                return (h - padBottom) - (((b - yMin) / yRange) * (h - padTop - padBottom)).toFloat()
            }

            // Draw Y-axis horizontal grid lines and tick labels (Guaranteed within bounds)
            yTicks.forEach { tickVal ->
                val ty = getY(tickVal)
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.5f),
                    start = Offset(padLeft, ty),
                    end = Offset(w - padRight, ty),
                    strokeWidth = 1f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "$${tickVal.roundToInt()}",
                    padLeft - textGap,
                    ty + (yTextSizePx * 0.35f),
                    yTextPaint
                )
            }

            // Baseline at starting bankroll (Gold)
            val baseY = getY(startBankroll)
            drawLine(
                color = Color(0xFFFBBF24).copy(alpha = 0.5f),
                start = Offset(padLeft, baseY),
                end = Offset(w - padRight, baseY),
                strokeWidth = 1.5f
            )

            // X-Axis baseline
            drawLine(
                color = Color(0xFF475569),
                start = Offset(padLeft, h - padBottom),
                end = Offset(w - padRight, h - padBottom),
                strokeWidth = 1.5f
            )

            // Draw X-axis tick labels (Density-scaled)
            val xTextPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = xTextSizePx
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            val xIndices = if (points.size <= 4) {
                points.indices.toList()
            } else {
                listOf(
                    0,
                    points.size / 4,
                    points.size / 2,
                    (points.size * 3) / 4,
                    points.size - 1
                ).distinct()
            }

            xIndices.forEach { idx ->
                val px = getX(idx)
                // Small notch mark
                drawLine(
                    color = Color(0xFF64748B),
                    start = Offset(px, h - padBottom),
                    end = Offset(px, h - padBottom + with(density) { 2.dp.toPx() }),
                    strokeWidth = 1.2f
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "#${points[idx].spinIndex}",
                    px,
                    h - padBottom + with(density) { 10.dp.toPx() },
                    xTextPaint
                )
            }

            // Draw Ideal Benchmark Curve (Dashed Gold/Amber) if enabled
            if (!idealPoints.isNullOrEmpty()) {
                val idealPath = Path()
                idealPoints.forEachIndexed { i, pt ->
                    val px = getX(i)
                    val py = getY(pt.bankroll)
                    if (i == 0) idealPath.moveTo(px, py) else idealPath.lineTo(px, py)
                }
                drawPath(
                    idealPath,
                    color = Color(0xFFF59E0B),
                    style = Stroke(
                        width = 2.0f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            // Draw bankroll curve
            val path = Path()
            val fillPath = Path()

            points.forEachIndexed { i, pt ->
                val px = getX(i)
                val py = getY(pt.bankroll)
                if (i == 0) {
                    path.moveTo(px, py)
                    fillPath.moveTo(px, baseY)
                    fillPath.lineTo(px, py)
                } else {
                    path.lineTo(px, py)
                    fillPath.lineTo(px, py)
                }
            }

            val isProfitable = (points.lastOrNull()?.bankroll ?: startBankroll) >= startBankroll
            val lineColor = if (isProfitable) Color(0xFF10B981) else Color(0xFFF43F5E)

            // Fill area
            fillPath.lineTo(getX(points.size - 1), baseY)
            fillPath.close()

            val fillGradient = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.25f),
                    lineColor.copy(alpha = 0.02f)
                ),
                startY = padTop,
                endY = h - padBottom
            )
            drawPath(fillPath, brush = fillGradient)
            drawPath(path, color = lineColor, style = Stroke(width = 2.0f, cap = StrokeCap.Round))

            // Highlight active hovered point
            if (activePoint != null) {
                val ptIdx = points.indexOf(activePoint).coerceAtLeast(0)
                val hx = getX(ptIdx)
                val hy = getY(activePoint!!.bankroll)

                // Vertical Crosshair
                drawLine(
                    color = Color(0xFF38BDF8),
                    start = Offset(hx, padTop),
                    end = Offset(hx, h - padBottom),
                    strokeWidth = 1.5f
                )

                // Outer Halo & Center Dot
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = Offset(hx, hy)
                )
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = 4.dp.toPx(),
                    center = Offset(hx, hy)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(hx, hy)
                )
            }

            // Time Machine Historical Position Cursor (Gold/Amber vertical indicator line + pin)
            val effectiveTimeIndex = timeTravelIndex ?: points.lastOrNull()?.spinIndex
            val timePointIdx = if (effectiveTimeIndex != null) {
                points.indexOfLast { it.spinIndex <= effectiveTimeIndex }.takeIf { it >= 0 } ?: (points.size - 1)
            } else {
                points.size - 1
            }

            if (timePointIdx in points.indices) {
                val tx = getX(timePointIdx)
                val timePoint = points[timePointIdx]
                val ty = getY(timePoint.bankroll)

                // Vertical Guide Line (Gold/Amber)
                drawLine(
                    color = Color(0xFFF59E0B).copy(alpha = 0.85f),
                    start = Offset(tx, padTop),
                    end = Offset(tx, h - padBottom),
                    strokeWidth = 2.0f
                )

                // Illuminated Outer Ring & Center Pin
                drawCircle(
                    color = Color(0xFFF59E0B).copy(alpha = 0.35f),
                    radius = 7.5.dp.toPx(),
                    center = Offset(tx, ty)
                )
                drawCircle(
                    color = Color(0xFFF59E0B),
                    radius = 4.dp.toPx(),
                    center = Offset(tx, ty)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(tx, ty)
                )

                // Indicator Tag on Canvas
                val timeTagPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#FBBF24")
                    textSize = timeTagTextSizePx
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val boundaryMargin = with(density) { 32.dp.toPx() }
                    textAlign = if (tx > w - boundaryMargin) android.graphics.Paint.Align.RIGHT else if (tx < padLeft + boundaryMargin) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "Spin #${timePoint.spinIndex}",
                    tx,
                    padTop + timeTagTextSizePx,
                    timeTagPaint
                )
            }
        }

        // Floating Tooltip showing all detailed spin ledger info on touch/drag
        if (activePoint != null) {
            val pt = activePoint!!
            val rec = pt.record
            val isGreen = pt.spunNumber == 0 || pt.spunNumber == 37
            val isRed = !isGreen && BetTrendCalculator.RED_NUMS.contains(pt.spunNumber)
            val numColor = if (isGreen) Color(0xFF15803D) else if (isRed) Color(0xFFDC2626) else Color(0xFF1E293B)
            val netGain = rec?.netGain ?: pt.netGain
            val outcome = rec?.outcome ?: (if (netGain > 0) "WIN" else if (netGain == 0.0) "PUSH" else "LOSS")
            val outcomeBg = if (outcome == "WIN") Color(0xFF065F46) else if (outcome == "LOSS") Color(0xFF991B1B) else Color(0xFF334155)
            val outcomeTxt = if (outcome == "WIN") Color(0xFF34D399) else if (outcome == "LOSS") Color(0xFFF87171) else Color(0xFF94A3B8)
            val netColor = if (netGain > 0) Color(0xFF34D399) else if (netGain < 0) Color(0xFFF87171) else Color.Gray

            // Determine if tooltip should appear on the left or right to prevent finger occlusion
            val ptIdx = points.indexOf(activePoint).coerceAtLeast(0)
            val isTouchOnRight = ptIdx > points.size / 2

            Box(
                modifier = Modifier
                    .align(if (isTouchOnRight) Alignment.TopStart else Alignment.TopEnd)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .widthIn(min = 190.dp, max = 290.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.96f), RoundedCornerShape(5.dp))
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(5.dp))
                    .padding(horizontal = 7.dp, vertical = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.5.dp)) {
                    // Row 1: Spin #, Hit Pill, Outcome Badge, Bank, Net P/L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Spin #${pt.spinIndex}",
                                color = Color(0xFF94A3B8),
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Spun number badge
                            Box(
                                modifier = Modifier
                                    .background(numColor, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.5.dp, vertical = 0.5.dp)
                            ) {
                                Text(
                                    if (pt.spunNumber == 37) "00" else "${pt.spunNumber}",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Outcome Badge
                            Box(
                                modifier = Modifier
                                    .background(outcomeBg, RoundedCornerShape(2.dp))
                                    .padding(horizontal = 3.dp, vertical = 0.5.dp)
                            ) {
                                Text(
                                    outcome,
                                    color = outcomeTxt,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Bank & P/L
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val idealPt = if (!idealPoints.isNullOrEmpty() && ptIdx in idealPoints.indices) {
                                idealPoints[ptIdx]
                            } else null

                            if (idealPt != null) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Actual: $${pt.bankroll.roundToInt()}",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Ideal: $${idealPt.bankroll.roundToInt()}",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Text(
                                    "Bank: $${pt.bankroll.roundToInt()}",
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            val netSign = if (netGain >= 0) "+$" else "-$"
                            Text(
                                "$netSign${abs(netGain.roundToInt())}",
                                color = netColor,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Row 2: Bets Placed
                    val betsText = rec?.activeBetsSummary?.ifEmpty { "None" } ?: "None"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            "Bets:",
                            color = Color(0xFF64748B),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            betsText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 11.sp
                        )
                    }

                    // Row 3: Prog, Stake, Cold Avoided
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val progText = buildString {
                                if (rec?.switchOccurred == true) append("🔀 ")
                                else if (rec?.isCyclePending == true) append("⏳ ")
                                append(rec?.progressionUsed?.ifEmpty { "Flat" } ?: "Flat")
                            }
                            Text(
                                "Prog: $progText",
                                color = if (rec?.switchOccurred == true) Color(0xFF38BDF8) else if (rec?.isCyclePending == true) Color(0xFFFDE047) else Color(0xFFFCD34D),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val stakeVal = rec?.stake?.roundToInt() ?: 0
                            val multVal = rec?.multiplier ?: 1
                            Text(
                                "Stake: $$stakeVal (${multVal}x)",
                                color = Color(0xFFFCD34D),
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (rec != null && rec.coldAvoided.isNotEmpty() && rec.coldAvoided != "None") {
                            Text(
                                "Cold: ${rec.coldAvoided}",
                                color = Color(0xFF67E8F9),
                                fontSize = 7.sp
                            )
                        }
                    }

                    // Row 4: Switch Note if applicable
                    if (rec != null && rec.switchNote.isNotEmpty()) {
                        Text(
                            text = rec.switchNote,
                            color = if (rec.switchOccurred) Color(0xFF38BDF8) else Color(0xFFFDE047),
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else if (rec != null && rec.isCyclePending) {
                        Text(
                            text = "⏳ Switch pending completion of cycle",
                            color = Color(0xFFFDE047),
                            fontSize = 7.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ultra-compact 1-spin incremental slider without bulky Material touch target padding
 */
@Composable
fun CompactLookbackSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Int> = 5..100,
    modifier: Modifier = Modifier
) {
    val rangeSpan = (valueRange.endInclusive - valueRange.start).toFloat()
    var widthPx by remember { mutableFloatStateOf(1f) }

    Canvas(
        modifier = modifier
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx).coerceIn(0f, 1f)
                    val newVal = (valueRange.start + frac * rangeSpan).roundToInt()
                    onValueChange(newVal)
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures { change, _ ->
                    val frac = (change.position.x / widthPx).coerceIn(0f, 1f)
                    val newVal = (valueRange.start + frac * rangeSpan).roundToInt()
                    onValueChange(newVal)
                }
            }
    ) {
        widthPx = size.width
        val trackH = 3.dp.toPx()
        val centerY = size.height / 2f
        val thumbRadius = 5.5.dp.toPx()

        val fraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
        val thumbX = thumbRadius + fraction * (size.width - thumbRadius * 2)

        // Inactive track
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(0f, centerY - trackH / 2),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
        )
        // Active track
        drawRoundRect(
            color = Color(0xFFF59E0B),
            topLeft = Offset(0f, centerY - trackH / 2),
            size = androidx.compose.ui.geometry.Size(thumbX, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2)
        )
        // Thumb
        drawCircle(
            color = Color(0xFFFBBF24),
            radius = thumbRadius,
            center = Offset(thumbX, centerY)
        )
    }
}

/**
 * Dialog displaying detailed mechanics and rules for a progression system
 */
@Composable
fun ProgressionExplanationDialog(
    detail: BetTrendCalculator.ProgressionDetail,
    criteriaInfo: String? = null,
    optimalConfig: BetTrendCalculator.GlobalOptimalConfig? = null,
    onApplyConfig: ((BetTrendCalculator.GlobalOptimalConfig) -> Unit)? = null,
    onApplyFamilyColdConfig: ((BetTrendCalculator.BetFamily, Int, BetTrendCalculator.ColdRule) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .fillMaxHeight(0.95f)
                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                    .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 3.dp)
            ) {
                // Dialog Box Title: "Strategies"
                Text(
                    text = "Strategies",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(3.dp))

                // Optimization Goal (Single Row, Minimal Top/Bottom Margin Within Section)
                if (criteriaInfo != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F2942), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = criteriaInfo,
                            color = Color(0xFF38BDF8),
                            fontSize = 9.5.sp,
                            lineHeight = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // Maximised Scrollable Content Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ================= SECTION 1: FAMILY WALK-FORWARD SETTINGS =================
                    if (optimalConfig != null && optimalConfig.familyOptimalColdConfigs.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "📊 FAMILY WALK-FORWARD SETTINGS",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Adaptive Progression",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 8.5.sp
                                    )
                                }
                                Text(
                                    "Optimal cold count & rule yielding the best rated graph per family:",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 8.5.sp
                                )

                                optimalConfig.familyOptimalColdConfigs.forEach { famOpt ->
                                    val (famName, famColor) = when (famOpt.family) {
                                        BetTrendCalculator.BetFamily.INSIDE -> "Inside" to Color(0xFFEF4444)
                                        BetTrendCalculator.BetFamily.OUTSIDE -> "Outside" to Color(0xFF3B82F6)
                                        BetTrendCalculator.BetFamily.COMBINED -> "Combined" to Color(0xFF10B981)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(4.dp))
                                            .padding(6.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = famName,
                                                        color = famColor,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (famOpt.optimalColdLimit == 0) "• Cold: Off" else "• Cold: ${famOpt.optimalColdLimit} (${famOpt.optimalColdRule.shortName})",
                                                        color = Color(0xFFFCD34D),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Text(
                                                    text = "⭐ Rating: ${famOpt.qualityScore} (${famOpt.qualityGrade})",
                                                    color = Color(0xFF34D399),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "P/L: ${if (famOpt.netProfit >= 0) "+$" else "-$"}${Math.abs(famOpt.netProfit).roundToInt()} | MaxDD: -$${famOpt.maxDrawdown.roundToInt()} | Win: ${famOpt.winRatePercent.roundToInt()}%",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 8.5.sp
                                                )
                                                if (onApplyFamilyColdConfig != null) {
                                                    Button(
                                                        onClick = {
                                                            onApplyFamilyColdConfig(famOpt.family, famOpt.optimalColdLimit, famOpt.optimalColdRule)
                                                            onDismiss()
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                        shape = RoundedCornerShape(3.dp),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(20.dp)
                                                    ) {
                                                        Text("Apply", color = Color.White, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ================= SECTION 2: BEST OF THE BEST CONFIG (WITH PROGRESSION DETAILS) =================
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (optimalConfig != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "🏆 BEST OF THE BEST CONFIG",
                                        color = Color(0xFFFBBF24),
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Score: ${optimalConfig.qualityScore} (${optimalConfig.qualityGrade})",
                                        color = Color(0xFF34D399),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Strategy", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                                        Text(optimalConfig.progression.displayName, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Family", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                                        Text(optimalConfig.family.name.lowercase().replaceFirstChar { it.uppercase() }, color = Color(0xFF38BDF8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Cold Limit", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                                        Text(if (optimalConfig.coldLimit == 0) "Off" else "${optimalConfig.coldLimit} Spins", color = Color(0xFFFCD34D), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Rule", color = Color(0xFF94A3B8), fontSize = 8.5.sp)
                                        Text(optimalConfig.coldRule.displayName, color = Color(0xFFF472B6), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("P/L: ${if (optimalConfig.netProfit >= 0) "+$" else "-$"}${Math.abs(optimalConfig.netProfit).roundToInt()}", color = if (optimalConfig.netProfit >= 0) Color(0xFF34D399) else Color(0xFFF87171), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    Text("MaxDD: -$${optimalConfig.maxDrawdown.roundToInt()}", color = Color(0xFFF87171), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Win: ${optimalConfig.winRatePercent.roundToInt()}%", color = Color(0xFF67E8F9), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (onApplyConfig != null) {
                                    Button(
                                        onClick = {
                                            onApplyConfig(optimalConfig)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(24.dp)
                                    ) {
                                        Text("⚡ Apply Optimal Configuration", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Divider separating summary metrics from progression details inside Best of the Best section
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .height(1.dp)
                                        .background(Color(0xFF334155))
                                )
                            } else {
                                Text(
                                    "🏆 BEST OF THE BEST CONFIG",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Progression Name, Category, and Risk Level Badge (inside Best of the Best Config section)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(detail.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(detail.category, color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                                }
                                val riskBg = when (detail.riskLevel) {
                                    "Low" -> Color(0xFF065F46)
                                    "Moderate" -> Color(0xFF854D0E)
                                    "High" -> Color(0xFF991B1B)
                                    else -> Color(0xFF701A75)
                                }
                                val riskTxt = when (detail.riskLevel) {
                                    "Low" -> Color(0xFF34D399)
                                    "Moderate" -> Color(0xFFFCD34D)
                                    "High" -> Color(0xFFF87171)
                                    else -> Color(0xFFF472B6)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(riskBg, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Text("Risk: ${detail.riskLevel}", color = riskTxt, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Progression Overview Description
                            Text(
                                text = detail.overview,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            // Win Rule
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF064E3B).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF059669), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("ON WIN:", color = Color(0xFF34D399), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(detail.winRule, color = Color.White, fontSize = 10.5.sp)
                                }
                            }

                            // Loss Rule
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF7F1D1D).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFDC2626), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("ON LOSS:", color = Color(0xFFF87171), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(2.dp))
                                    Text(detail.lossRule, color = Color.White, fontSize = 10.5.sp)
                                }
                            }

                            // Recommended Coverage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Recommended Layout:", color = Color(0xFF94A3B8), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                Text(detail.coverageRecommendation, color = Color(0xFF67E8F9), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Compact "Close" Button at the very bottom with minimal top/bottom margin
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 1.dp),
                        modifier = Modifier.height(20.dp)
                    ) {
                        Text(
                            text = "Close",
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

