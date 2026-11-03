package com.roulettetactician.logic

import com.roulettetactician.ui.model.TableType
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bet Trend & Predictive Analysis Engine.
 * Evaluates trailing spin persistence, cold number avoidance, candidate bet combinations,
 * and the complete suite of bet progressions to determine optimal next-round bet placements.
 */
object BetTrendCalculator {

    val RED_NUMS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

    // Clockwise rotor order for physical wheel circular distance calculation
    val SINGLE_ZERO_ROTOR = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
        5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26
    )

    val DOUBLE_ZERO_ROTOR = listOf(
        0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
        37, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2
    )

    private val SINGLE_ZERO_ROTOR_IDX = IntArray(38) { -1 }.apply {
        SINGLE_ZERO_ROTOR.forEachIndexed { idx, num -> this[num] = idx }
    }

    private val DOUBLE_ZERO_ROTOR_IDX = IntArray(38) { -1 }.apply {
        DOUBLE_ZERO_ROTOR.forEachIndexed { idx, num -> this[num] = idx }
    }

    private val PROXIMITY_DECAY_TABLE = DoubleArray(20) { d -> exp(-d / 2.8) }

    // Precomputed Circular Von Mises spatial kernel tables (kappa = 3.2, bandwidth d <= 6)
    private val VON_MISES_KERNEL_EU = DoubleArray(7) { d ->
        exp(3.2 * (kotlin.math.cos((2.0 * Math.PI * d) / 37.0) - 1.0))
    }
    private val VON_MISES_KERNEL_US = DoubleArray(7) { d ->
        exp(3.2 * (kotlin.math.cos((2.0 * Math.PI * d) / 38.0) - 1.0))
    }

    // Precomputed Gaussian landing dispersion kernel for Dealer Signature step velocity projection
    private val DEALER_DISPERSION_KERNEL = DoubleArray(3) { off ->
        0.35 * exp(-(off * off) / 2.0)
    }

    enum class BetFamily {
        INSIDE, OUTSIDE, COMBINED
    }

    enum class OptimizationCriterion(val displayName: String, val shortName: String) {
        PNL("Net Profit", "P/L"),
        HIT_RATE("Hit Rate", "HIT"),
        MAX_DD("Min Drawdown", "MaxDD"),
        QUALITY("Trend Quality", "Quality")
    }

    enum class ColdRule(val displayName: String, val shortName: String, val description: String) {
        STRICT("Strict Ban", "Strict", "Zero cold numbers allowed on any bet"),
        PENALTY("Soft Penalty", "Soft", "Discounts score per cold pocket; strong momentum can override"),
        WATERFALL("Fallback", "Fallback", "Prefers 0-cold bets; falls back so bets are never missed"),
        HYBRID("Split (Inside Strict)", "Split", "Strict avoidance on Inside bets; Soft on Outside bets")
    }

    enum class SwitchTransitionRule(val displayName: String, val shortName: String, val description: String) {
        IMMEDIATE_RESET("Reset to Base Unit", "Reset", "Switches immediately to Step 1 (1x Base Unit)"),
        COMPLETE_CYCLE("Complete Cycle First", "Cycle", "Completes current progression recovery cycle, then re-verifies if switch is still needed")
    }

    enum class SwitchSensitivity(val displayName: String, val shortName: String, val leadMargin: Double, val description: String) {
        AGGRESSIVE("Immediate (0%)", "0%", 0.0, "Switches as soon as any candidate takes the lead"),
        MODERATE("Moderate (5% Lead)", "5%", 0.05, "Candidate must outperform current strategy by at least 5%"),
        CONSERVATIVE("Conservative (10% Lead)", "10%", 0.10, "Candidate must outperform current strategy by at least 10%")
    }

    enum class BettingStartMode(val displayName: String, val shortName: String, val description: String) {
        FROM_SPIN_1("From Spin 1", "Spin 1", "Simulate betting from the very first spin using an expanding lookback window"),
        AFTER_LOOKBACK("After Lookback", "After Lookback", "Historical spins within lookback window are observational seeds; active betting begins right after lookback")
    }

    enum class ProgressionType(val displayName: String, val category: String) {
        AUTO("Auto-Optimize", "Dynamic"),
        FLAT("Flat Betting", "Fixed"),
        MARTINGALE("Martingale", "Negative"),
        GRAND_MARTINGALE("Grand Martingale", "Negative"),
        DALEMBERT("D'Alembert", "Negative"),
        DOUBLE_DALEMBERT("Double D'Alembert", "Negative"),
        FIBONACCI("Fibonacci", "Negative"),
        LABOUCHERE("Labouchere", "Negative"),
        TIER_ET_TOUT("Tier et Tout", "Negative"),
        PAROLI("Paroli (Reverse Martingale)", "Positive"),
        CONTRA_DALEMBERT("Contra D'Alembert", "Positive"),
        REVERSE_LABOUCHERE("Reverse Labouchere", "Positive"),
        OSCARS_GRIND("Oscar's Grind", "Positive"),
        SYSTEM_1326("1-3-2-6 System", "Positive"),
        PARLAY("Parlay", "Positive"),
        TRIPLE_LUX("Triple Lux", "Positive"),
        HOLLANDISH("Hollandish", "Positive"),
        GUETTING("Guetting", "Positive"),
        ALL_IN("All-In Dynamic", "Positive"),
        JAMES_BOND("James Bond", "Fixed"),
        ROMANOSKY("Romanosky", "Fixed"),
        KAVOURAS("Kavouras Bet", "Fixed"),
        ANDRUCCI("Andrucci", "Fixed"),
        SHOTWELL("Shotwell", "Fixed"),
        FOUR_PILLARS("Four Pillars", "Fixed"),
        EGD_SP_2("EGD SP 2.0", "Modern"),
        BLUE_RIBBON("Blue Ribbon", "Modern"),
        BEST_OF_ALL("Best of All Worlds", "Modern"),
        SPIN_OFF_3("Spin-off 3.0", "Modern");

        val isFixedLayout: Boolean
            get() = this in setOf(
                JAMES_BOND, ROMANOSKY, KAVOURAS, ANDRUCCI, SHOTWELL, FOUR_PILLARS
            )

        val fixedLayoutDescription: String?
            get() = when (this) {
                JAMES_BOND -> "James Bond: 14u High, 5u Line 13-18, 1u Straight 0"
                ROMANOSKY -> "Romanosky: 3u on 2 Dozens, 1u on 2 Corners"
                KAVOURAS -> "Kavouras: 1 Corner, 1 Line, 5 Splits"
                ANDRUCCI -> "Andrucci: 5 Hot Straights"
                SHOTWELL -> "Shotwell: 1 Line, 4 Rotor Straights"
                FOUR_PILLARS -> "Four Pillars: 4 Rotor Corners"
                else -> null
            }
    }

    data class CandidateBet(
        val betId: String,
        val name: String,
        val type: String,
        val numbers: List<Int>,
        val mask: Long,
        val payoutRatio: Int,
        val baseUnits: Int,
        val isInside: Boolean,
        val xPercent: Float,
        val yPercent: Float
    )

    data class TrendQualityMetrics(
        val score: Int = 50,
        val rSquared: Double = 0.0,
        val slope: Double = 0.0,
        val totalUnderwaterPercent: Double = 0.0,
        val recentUnderwaterPercent: Double = 0.0,
        val ulcerIndex: Double = 0.0,
        val grade: String = "C",
        val summary: String = "Normal"
    )

    data class TrendRecommendation(
        val bestProgression: ProgressionType,
        val recommendedBets: List<RecommendedBet>,
        val totalUnits: Int,
        val currentMultiplier: Int,
        val nextTotalStake: Double,
        val coldBannedNumbers: List<Int>,
        val netProfit: Double,
        val winRatePercent: Double,
        val maxDrawdown: Double,
        val endingBankroll: Double,
        val stabilityScore: Double,
        val equityPoints: List<EquityPoint>,
        val spinLogs: List<SpinLedgerRecord>,
        val numberWeights: Map<Int, Double> = emptyMap(),
        val rawMultiplier: Int = currentMultiplier,
        val maxStake: Double = 0.0,
        val quality: TrendQualityMetrics = TrendQualityMetrics(),
        val idealEquityPoints: List<EquityPoint> = emptyList(),
        val idealProgression: ProgressionType? = null,
        val startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1,
        val activeProgression: ProgressionType = bestProgression
    )

    data class RecommendedBet(
        val bet: CandidateBet,
        val units: Int,
        val dollarAmount: Double
    )

    data class EquityPoint(
        val spinIndex: Int,
        val spunNumber: Int,
        val bankroll: Double,
        val netGain: Double,
        val record: SpinLedgerRecord? = null
    )

    data class SpinLedgerRecord(
        val spinIndex: Int,
        val spunNumber: Int,
        val activeBetsSummary: String,
        val coldAvoided: String,
        val progressionUsed: String = "",
        val stake: Double,
        val multiplier: Int,
        val netGain: Double,
        val outcome: String, // WIN, LOSS, PUSH
        val bankroll: Double,
        val placedBets: List<CandidateBet> = emptyList(),
        val rawMultiplier: Int = multiplier,
        val switchOccurred: Boolean = false,
        val switchNote: String = "",
        val isCyclePending: Boolean = false
    )


    data class ProgressionDetail(
        val name: String,
        val category: String,
        val riskLevel: String,
        val overview: String,
        val winRule: String,
        val lossRule: String,
        val coverageRecommendation: String
    )

    fun getProgressionDetail(type: ProgressionType): ProgressionDetail {
        return when (type) {
            ProgressionType.AUTO -> ProgressionDetail(
                name = "Auto-Optimize",
                category = "Dynamic Optimization",
                riskLevel = "Adaptive",
                overview = "Evaluates all 28 progression models in parallel against the current trailing lookback window. Dynamically selects the system that maximized net gains while minimizing max drawdown (Drawdown-penalized Sortino ratio).",
                winRule = "Adapts dynamically to the winning system's rule set.",
                lossRule = "Switches or steps down stake to protect bankroll equity based on trailing variance.",
                coverageRecommendation = "Optimal across Inside, Outside, and Combined layouts."
            )
            ProgressionType.FLAT -> ProgressionDetail(
                name = "Flat Betting",
                category = "Fixed Staking",
                riskLevel = "Low",
                overview = "Wagers exactly the same base unit amount on every spin regardless of previous outcomes. Eliminates streak escalation risk and provides the ultimate benchmark for pure edge evaluation.",
                winRule = "Maintain 1x base stake.",
                lossRule = "Maintain 1x base stake.",
                coverageRecommendation = "Universal across all bet families."
            )
            ProgressionType.MARTINGALE -> ProgressionDetail(
                name = "Martingale",
                category = "Negative Progression",
                riskLevel = "High",
                overview = "Doubles the bet amount after every loss (1, 2, 4, 8, 16...). A single win recovers all accumulated previous losses plus yields an overall profit of 1 base unit.",
                winRule = "Reset immediately to 1 base unit.",
                lossRule = "Double previous stake (2x).",
                coverageRecommendation = "Best suited for Outside Even-Money bets (Red/Black, Odd/Even, High/Low)."
            )
            ProgressionType.GRAND_MARTINGALE -> ProgressionDetail(
                name = "Grand Martingale",
                category = "Negative Progression",
                riskLevel = "Extreme",
                overview = "Aggressive variant of the Martingale: doubles previous stake plus adds 1 additional unit after each loss (1, 3, 7, 15, 31...). On a win, yields 1 unit profit for every spin played in the losing streak.",
                winRule = "Reset immediately to 1 base unit.",
                lossRule = "Multiply previous stake by 2 and add 1 unit (2x + 1).",
                coverageRecommendation = "Outside Even-Money bets with high starting bankroll."
            )
            ProgressionType.DALEMBERT -> ProgressionDetail(
                name = "D'Alembert",
                category = "Negative Progression",
                riskLevel = "Moderate",
                overview = "Based on the equilibrium theorem: increase bet by 1 unit after every loss, and decrease by 1 unit after every win. Moderates bankroll swings compared to exponential systems.",
                winRule = "Decrease bet by 1 unit (minimum 1 unit).",
                lossRule = "Increase bet by 1 unit.",
                coverageRecommendation = "Even-Money and Dozen/Column bets."
            )
            ProgressionType.DOUBLE_DALEMBERT -> ProgressionDetail(
                name = "Double D'Alembert",
                category = "Negative Progression",
                riskLevel = "High",
                overview = "Faster recovery variation of D'Alembert: increases bet by 2 units on a loss and decreases by 1 unit on a win, accelerating recovery from multi-spin drawdowns.",
                winRule = "Decrease bet by 1 unit.",
                lossRule = "Increase bet by 2 units.",
                coverageRecommendation = "Outside Even-Money and combined multi-tier bets."
            )
            ProgressionType.FIBONACCI -> ProgressionDetail(
                name = "Fibonacci",
                category = "Negative Progression",
                riskLevel = "Moderate",
                overview = "Advances along the Fibonacci sequence (1, 1, 2, 3, 5, 8, 13, 21...) on each loss. After a win, moves back two steps in the sequence, allowing recovery with fewer wins than losses.",
                winRule = "Move back 2 numbers in the sequence (reset to 1 if within first 2 steps).",
                lossRule = "Move forward 1 step in the Fibonacci sequence.",
                coverageRecommendation = "Outside Even-Money bets."
            )
            ProgressionType.LABOUCHERE -> ProgressionDetail(
                name = "Labouchere",
                category = "Cancellation System",
                riskLevel = "High",
                overview = "Uses a custom number sequence (e.g., 1-2-3-4). Bet size equals the sum of the first and last numbers. Winning crosses off both ends; losing appends the lost bet amount to the end.",
                winRule = "Cross off the first and last numbers from the line.",
                lossRule = "Append the total amount lost to the end of the line.",
                coverageRecommendation = "Outside Even-Money bets."
            )
            ProgressionType.TIER_ET_TOUT -> ProgressionDetail(
                name = "Tier et Tout",
                category = "Capital Division",
                riskLevel = "Moderate",
                overview = "Splits an allocated stake into two portions: 1/3 for the first bet, and 2/3 for the second bet. If the 1/3 bet wins, profit is banked and stake increases; if both lose, the cycle resets.",
                winRule = "Bank profit, recalculate 1/3 and 2/3 from updated capital.",
                lossRule = "Bet remaining 2/3. If second bet loses, reset base stake.",
                coverageRecommendation = "Even-money and high-probability layout bets."
            )
            ProgressionType.PAROLI -> ProgressionDetail(
                name = "Paroli (Reverse Martingale)",
                category = "Positive Progression",
                riskLevel = "Low",
                overview = "Doubles the bet after each win for up to 3 consecutive wins (1 -> 2 -> 4). After 3 wins or any single loss, resets to 1 unit. Exploits winning runs while risking only 1 unit per cycle.",
                winRule = "Double previous bet (up to 3 consecutive wins max, then reset to 1).",
                lossRule = "Reset immediately to 1 base unit.",
                coverageRecommendation = "Outside Even-Money bets."
            )
            ProgressionType.CONTRA_DALEMBERT -> ProgressionDetail(
                name = "Contra D'Alembert",
                category = "Positive Progression",
                riskLevel = "Moderate",
                overview = "Inversion of D'Alembert: increases bet by 1 unit after a win, and decreases by 1 unit after a loss. Capitalizes on winning clusters while minimizing downswings.",
                winRule = "Increase bet by 1 unit.",
                lossRule = "Decrease bet by 1 unit (minimum 1 unit).",
                coverageRecommendation = "Outside Even-Money and Dozen bets."
            )
            ProgressionType.REVERSE_LABOUCHERE -> ProgressionDetail(
                name = "Reverse Labouchere",
                category = "Positive Cancellation",
                riskLevel = "High",
                overview = "Positive progression variant of Labouchere. Wager sum of first and last numbers in sequence. On a win, append the win amount to the line; on a loss, erase outer numbers to cap drawdown.",
                winRule = "Append win amount to the end of the line.",
                lossRule = "Cross off outer first and last numbers.",
                coverageRecommendation = "Even-Money and Dozen bets."
            )
            ProgressionType.OSCARS_GRIND -> ProgressionDetail(
                name = "Oscar's Grind",
                category = "Positive Progression",
                riskLevel = "Low",
                overview = "Targets exactly +1 unit of net profit per session cycle. Bet stays flat at 1 unit during losses; after a win following a loss, increases bet by +1 unit (never betting more than needed to hit +1 target).",
                winRule = "If net cycle profit < 1 unit, increase bet by 1 unit; if +1 reached, reset to 1 unit.",
                lossRule = "Keep bet exactly the same as previous spin.",
                coverageRecommendation = "Outside Even-Money bets."
            )
            ProgressionType.SYSTEM_1326 -> ProgressionDetail(
                name = "1-3-2-6 System",
                category = "Positive Cycle Progression",
                riskLevel = "Low",
                overview = "Wagers consecutive units in the pattern 1 -> 3 -> 2 -> 6 upon successive wins. Completing all 4 wins banks 12 units of profit. Any single loss resets stake immediately to 1 unit.",
                winRule = "Advance to next multiplier in sequence (1 -> 3 -> 2 -> 6). Reset after 6.",
                lossRule = "Reset immediately to 1 unit.",
                coverageRecommendation = "Even-Money bets (Red/Black, High/Low)."
            )
            ProgressionType.PARLAY -> ProgressionDetail(
                name = "Parlay",
                category = "Positive Compounding",
                riskLevel = "High",
                overview = "Re-invests the original stake plus entire net payout onto the subsequent spin. Aims for rapid exponential compounding during sustained cluster runs.",
                winRule = "Let full winnings ride (wager original bet + payout).",
                lossRule = "Reset to base stake.",
                coverageRecommendation = "Outside Even-Money and Double Streets."
            )
            ProgressionType.TRIPLE_LUX -> ProgressionDetail(
                name = "Triple Lux",
                category = "Positive Compounding",
                riskLevel = "Moderate",
                overview = "Three-stage positive progression: stage 1 banks safety reserve, stage 2 compounds half profits, stage 3 presses for high-multiplier breakout run before resetting.",
                winRule = "Advance tier and press stake according to stage multiplier.",
                lossRule = "Drop back to stage 1 base unit.",
                coverageRecommendation = "Combined inside and outside coverage."
            )
            ProgressionType.HOLLANDISH -> ProgressionDetail(
                name = "Hollandish",
                category = "Block Progression",
                riskLevel = "Moderate",
                overview = "Evaluates spins in blocks of 3. If a 3-spin block produces a net negative result, stake increases to next odd multiplier (1, 3, 5, 7, 9). Bet remains unchanged during the block.",
                winRule = "If 3-spin block is profitable, step down to previous level or reset to 1.",
                lossRule = "If 3-spin block is net loss, step up to next odd multiplier.",
                coverageRecommendation = "Outside Even-Money bets."
            )
            ProgressionType.GUETTING -> ProgressionDetail(
                name = "Guetting",
                category = "Step-Tier Progression",
                riskLevel = "Moderate",
                overview = "Four-tier step-ladder: Tier 1 (1.5, 2), Tier 2 (3, 4), Tier 3 (6, 8), Tier 4 (12, 16). Requires 2 consecutive wins at a level to graduate to the next tier, dropping 1 tier on loss.",
                winRule = "Advance within level on 1 win; graduate to next tier on 2 consecutive wins.",
                lossRule = "Drop to starting unit of previous tier.",
                coverageRecommendation = "Even-Money and Column/Dozen bets."
            )
            ProgressionType.ALL_IN -> ProgressionDetail(
                name = "All-In Dynamic",
                category = "High Volatility",
                riskLevel = "Extreme",
                overview = "Calculates maximum dynamic allocation based on model conviction score and short-term wheel bias. Intended for short high-intensity runs.",
                winRule = "Scale up stake proportional to model conviction.",
                lossRule = "Reset to conservative floor unit.",
                coverageRecommendation = "High-confidence combined candidate bets."
            )
            ProgressionType.JAMES_BOND -> ProgressionDetail(
                name = "James Bond",
                category = "Fixed Coverage",
                riskLevel = "Moderate",
                overview = "Fixed 20-unit layout covering 25 numbers (67.5% coverage): 14 units on High (19-36), 5 units on Six Line (13-18), and 1 unit on Zero hedge. Loses only on numbers 1-12.",
                winRule = "Maintain 20-unit proportional distribution.",
                lossRule = "Maintain 20-unit proportional distribution.",
                coverageRecommendation = "Fixed preset: High, 13-18 Six Line, and 0."
            )
            ProgressionType.ROMANOSKY -> ProgressionDetail(
                name = "Romanosky",
                category = "Fixed Coverage",
                riskLevel = "Low",
                overview = "High-probability system covering 32 of 37 numbers (86.5% coverage) using 8 units: 3 units each on 2 Dozens, and 1 unit each on 2 non-covered Corners.",
                winRule = "Yields +1 unit profit on any covered number hit. Maintain stake.",
                lossRule = "Maintain stake or optional 1-step recovery.",
                coverageRecommendation = "2 Dozens + 2 Corners."
            )
            ProgressionType.KAVOURAS -> ProgressionDetail(
                name = "Kavouras Bet",
                category = "Chaos Coverage",
                riskLevel = "Moderate",
                overview = "Asymmetric bet architecture covering 20 numbers with variable stakes: 1 Corner (1u), 1 Double Street (2u), and 5 Splits (1u each). Yields varied payout multiples (+1, +4, +10 units) based on which pocket hits.",
                winRule = "Maintain 8-unit asymmetric distribution.",
                lossRule = "Maintain 8-unit asymmetric distribution.",
                coverageRecommendation = "20 specific pocket distributions."
            )
            ProgressionType.ANDRUCCI -> ProgressionDetail(
                name = "Andrucci",
                category = "Chaos & Law of the Third",
                riskLevel = "High",
                overview = "Exploits Poisson distribution cluster theory: tracks hot repeated single numbers over a 30-37 spin window and wagers straight-up on active repeaters.",
                winRule = "Lock in 35:1 straight payout and re-evaluate repeater list.",
                lossRule = "Maintain straight wagers across repeater set for up to 35 spins.",
                coverageRecommendation = "Inside Straight-Up bets on hot repeaters."
            )
            ProgressionType.SHOTWELL -> ProgressionDetail(
                name = "Shotwell",
                category = "Wheel Sector Anchor",
                riskLevel = "Moderate",
                overview = "Combines 1 Six-Line (Double Street) with 4 single Straight-Up numbers spaced evenly at ~9-pocket intervals around the physical rotor to catch wheel quadrant bias.",
                winRule = "Collect 5:1 on Double Street or 35:1 on Straight-Up sniper.",
                lossRule = "Maintain 5-unit distribution.",
                coverageRecommendation = "1 Double Street + 4 rotor-spaced single numbers."
            )
            ProgressionType.FOUR_PILLARS -> ProgressionDetail(
                name = "Four Pillars",
                category = "Sector Symmetry",
                riskLevel = "Moderate",
                overview = "Places 4 balanced Corner/Split anchors across the 4 cardinal sectors of the roulette cylinder, ensuring coverage regardless of ball rotor entry angle.",
                winRule = "Collect 8:1 corner or 17:1 split payout; reset or compound.",
                lossRule = "Maintain 4-pillar quadrant balance.",
                coverageRecommendation = "4 Corner bets anchoring rotor quadrants."
            )
            ProgressionType.EGD_SP_2 -> ProgressionDetail(
                name = "EGD SP 2.0",
                category = "Modern Quantitative",
                riskLevel = "Adaptive",
                overview = "Quantitative volatility model that measures trailing rolling standard deviation and z-score bands. Scales unit size when variance narrows and protects capital when variance spikes.",
                winRule = "Scale stake according to trailing volatility expansion.",
                lossRule = "Dampen unit exposure when standard deviation breaches threshold.",
                coverageRecommendation = "Combined multi-tier candidate bets."
            )
            ProgressionType.BLUE_RIBBON -> ProgressionDetail(
                name = "Blue Ribbon",
                category = "Modern Hybrid",
                riskLevel = "Low",
                overview = "Conservative 5-tier safety progression: combines outside even-money stabilizers with inside corner boosters to maintain high hit frequency and minimal peak-to-trough drawdowns.",
                winRule = "Lock in tier profit and reset to level 1.",
                lossRule = "Advance 1 tier in 5-step stabilization ladder.",
                coverageRecommendation = "Outside bets + 2 Inside Corners."
            )
            ProgressionType.BEST_OF_ALL -> ProgressionDetail(
                name = "Best of All Worlds",
                category = "Modern Hybrid",
                riskLevel = "Moderate",
                overview = "Combines positive profit-pressing streaks (Paroli-style) with gentle D'Alembert loss recovery gates, capturing hot streaks while capping downside drawdown.",
                winRule = "Increase multiplier by 1 during winning streaks (up to 3x).",
                lossRule = "Step back 1 unit without exponential doubling.",
                coverageRecommendation = "Outside Even-Money and Dozen bets."
            )
            ProgressionType.SPIN_OFF_3 -> ProgressionDetail(
                name = "Spin-off 3.0",
                category = "Modern Momentum",
                riskLevel = "Moderate",
                overview = "Three-phase momentum rotation system tracking wheel sector cluster density. Automatically adjusts target bet families between Inside and Outside as persistence shifts.",
                winRule = "Advance momentum phase multiplier.",
                lossRule = "Rotate sector focus and reset phase stake to base unit.",
                coverageRecommendation = "Dynamic Inside/Outside rotation."
            )
        }
    }

    /**
     * Compute circular distance in pockets along the physical roulette rotor
     */
    fun getWheelDistance(numA: Int, numB: Int, isUS: Boolean): Int {
        if (numA !in 0..37 || numB !in 0..37) return 0
        val idxTable = if (isUS) DOUBLE_ZERO_ROTOR_IDX else SINGLE_ZERO_ROTOR_IDX
        val rotorSize = if (isUS) 38 else 37
        val idxA = idxTable[numA]
        val idxB = idxTable[numB]
        if (idxA == -1 || idxB == -1) return 0
        val direct = abs(idxA - idxB)
        return min(direct, rotorSize - direct)
    }

    /**
     * Converts a list of numbers to a 64-bit bitmask.
     * Number 0..36 maps to bit 0..36, Number 37 (00) maps to bit 37.
     */
    fun toBitmask(numbers: Collection<Int>): Long {
        var mask = 0L
        for (num in numbers) {
            if (num in 0..37) {
                mask = mask or (1L shl num)
            }
        }
        return mask
    }

    val EU_CANDIDATES: List<CandidateBet> by lazy { generateAllCandidates(isUS = false) }
    val US_CANDIDATES: List<CandidateBet> by lazy { generateAllCandidates(isUS = true) }

    fun getCandidates(isUS: Boolean): List<CandidateBet> = if (isUS) US_CANDIDATES else EU_CANDIDATES

    /**
     * Generates all standard roulette candidate bets with precalculated masks and felt coordinates.
     */
    fun generateAllCandidates(isUS: Boolean): List<CandidateBet> {
        val candidates = mutableListOf<CandidateBet>()
        val geo = Geometry

        val numW = geo.NUM_W
        val zeroW = geo.ZERO_W
        val mainH = geo.MAIN_H
        val dozH = geo.DOZ_H
        val outH = geo.OUT_H
        val colW = geo.COL_W

        fun getNumAt(row: Int, col: Int): Int = 3 - row + (col * 3)

        // 1. Straights (35:1)
        for (col in 0..11) {
            for (row in 0..2) {
                val n = getNumAt(row, col)
                val x = zeroW + ((col + 0.5f) / 12f * numW)
                val y = (row + 0.5f) / 3f * mainH
                candidates.add(
                    CandidateBet(
                        betId = "num-$n",
                        name = "Straight $n",
                        type = "straight",
                        numbers = listOf(n),
                        mask = 1L shl n,
                        payoutRatio = 35,
                        baseUnits = 1,
                        isInside = true,
                        xPercent = x,
                        yPercent = y
                    )
                )
            }
        }

        // Single 0
        val zeroY = if (isUS) mainH * 0.25f else mainH * 0.5f
        candidates.add(
            CandidateBet(
                betId = "num-0",
                name = "Straight 0",
                type = "straight",
                numbers = listOf(0),
                mask = 1L shl 0,
                payoutRatio = 35,
                baseUnits = 1,
                isInside = true,
                xPercent = zeroW * 0.5f,
                yPercent = zeroY
            )
        )

        // Double 00 (represented by 37)
        if (isUS) {
            candidates.add(
                CandidateBet(
                    betId = "num-00",
                    name = "Straight 00",
                    type = "straight",
                    numbers = listOf(37),
                    mask = 1L shl 37,
                    payoutRatio = 35,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW * 0.5f,
                    yPercent = mainH * 0.75f
                )
            )
        }

        // 2. Horizontal Splits (17:1)
        for (col in 0..11) {
            // Split between row 2 (bottom) and row 1 (mid)
            val b1 = getNumAt(2, col)
            val b2 = getNumAt(1, col)
            candidates.add(
                CandidateBet(
                    betId = "split-$b1-$b2",
                    name = "Split $b1/$b2",
                    type = "split",
                    numbers = listOf(b1, b2),
                    mask = (1L shl b1) or (1L shl b2),
                    payoutRatio = 17,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 0.5f) / 12f * numW),
                    yPercent = (2f / 3f) * mainH
                )
            )

            // Split between row 1 (mid) and row 0 (top)
            val t1 = getNumAt(1, col)
            val t2 = getNumAt(0, col)
            candidates.add(
                CandidateBet(
                    betId = "split-$t1-$t2",
                    name = "Split $t1/$t2",
                    type = "split",
                    numbers = listOf(t1, t2),
                    mask = (1L shl t1) or (1L shl t2),
                    payoutRatio = 17,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 0.5f) / 12f * numW),
                    yPercent = (1f / 3f) * mainH
                )
            )
        }

        // 3. Vertical Splits (17:1)
        for (col in 0..10) {
            for (row in 0..2) {
                val n1 = getNumAt(row, col)
                val n2 = getNumAt(row, col + 1)
                candidates.add(
                    CandidateBet(
                        betId = "split-$n1-$n2",
                        name = "Split $n1/$n2",
                        type = "split",
                        numbers = listOf(n1, n2),
                        mask = (1L shl n1) or (1L shl n2),
                        payoutRatio = 17,
                        baseUnits = 1,
                        isInside = true,
                        xPercent = zeroW + ((col + 1f) / 12f * numW),
                        yPercent = (row + 0.5f) / 3f * mainH
                    )
                )
            }
        }

        // Splits with 0
        if (!isUS) {
            for (row in 0..2) {
                val n = getNumAt(row, 0)
                candidates.add(
                    CandidateBet(
                        betId = "split-0-$n",
                        name = "Split 0/$n",
                        type = "split",
                        numbers = listOf(0, n),
                        mask = (1L shl 0) or (1L shl n),
                        payoutRatio = 17,
                        baseUnits = 1,
                        isInside = true,
                        xPercent = zeroW,
                        yPercent = (row + 0.5f) / 3f * mainH
                    )
                )
            }
        }

        // 4. Streets (11:1)
        for (col in 0..11) {
            val s1 = getNumAt(2, col)
            val s2 = getNumAt(1, col)
            val s3 = getNumAt(0, col)
            candidates.add(
                CandidateBet(
                    betId = "street-$s1",
                    name = "Street $s1-$s3",
                    type = "street",
                    numbers = listOf(s1, s2, s3),
                    mask = (1L shl s1) or (1L shl s2) or (1L shl s3),
                    payoutRatio = 11,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 0.5f) / 12f * numW),
                    yPercent = 0.02f
                )
            )
        }

        // 5. Corners (8:1)
        for (col in 0..10) {
            // Lower Corner (Row 1 & 2)
            val lc1 = getNumAt(1, col)
            val lc2 = getNumAt(2, col)
            val lc3 = getNumAt(1, col + 1)
            val lc4 = getNumAt(2, col + 1)
            candidates.add(
                CandidateBet(
                    betId = "corn-$lc1-$lc4",
                    name = "Corner $lc2-$lc3",
                    type = "corner",
                    numbers = listOf(lc1, lc2, lc3, lc4),
                    mask = (1L shl lc1) or (1L shl lc2) or (1L shl lc3) or (1L shl lc4),
                    payoutRatio = 8,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 1f) / 12f * numW),
                    yPercent = (2f / 3f) * mainH
                )
            )

            // Upper Corner (Row 0 & 1)
            val uc1 = getNumAt(0, col)
            val uc2 = getNumAt(1, col)
            val uc3 = getNumAt(0, col + 1)
            val uc4 = getNumAt(1, col + 1)
            candidates.add(
                CandidateBet(
                    betId = "corn-$uc1-$uc4",
                    name = "Corner $uc2-$uc3",
                    type = "corner",
                    numbers = listOf(uc1, uc2, uc3, uc4),
                    mask = (1L shl uc1) or (1L shl uc2) or (1L shl uc3) or (1L shl uc4),
                    payoutRatio = 8,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 1f) / 12f * numW),
                    yPercent = (1f / 3f) * mainH
                )
            )
        }

        // 6. Double Streets / Six-Lines (5:1)
        for (col in 0..10) {
            val nums = (0..2).flatMap { r -> listOf(getNumAt(r, col), getNumAt(r, col + 1)) }
            val minN = nums.minOrNull() ?: 1
            val maxN = nums.maxOrNull() ?: 6
            candidates.add(
                CandidateBet(
                    betId = "line-${getNumAt(0, col)}-${getNumAt(0, col + 1)}",
                    name = "Line $minN-$maxN",
                    type = "six_line",
                    numbers = nums,
                    mask = toBitmask(nums),
                    payoutRatio = 5,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW + ((col + 1f) / 12f * numW),
                    yPercent = 0.02f
                )
            )
        }

        // 7. Trios / Baskets (11:1) & First Four (8:1)
        if (!isUS) {
            candidates.add(
                CandidateBet(
                    betId = "trio-0-1-2",
                    name = "Trio 0,1,2",
                    type = "trio",
                    numbers = listOf(0, 1, 2),
                    mask = (1L shl 0) or (1L shl 1) or (1L shl 2),
                    payoutRatio = 11,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW,
                    yPercent = (1.5f / 3f) * mainH
                )
            )
            candidates.add(
                CandidateBet(
                    betId = "corner-0-1-2-3",
                    name = "Basket 0-1-2-3",
                    type = "corner",
                    numbers = listOf(0, 1, 2, 3),
                    mask = (1L shl 0) or (1L shl 1) or (1L shl 2) or (1L shl 3),
                    payoutRatio = 8,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW,
                    yPercent = mainH
                )
            )
        } else {
            // US Basket 0, 00, 2
            candidates.add(
                CandidateBet(
                    betId = "trio-0-00-2",
                    name = "Basket 0,00,2",
                    type = "trio",
                    numbers = listOf(0, 37, 2),
                    mask = (1L shl 0) or (1L shl 37) or (1L shl 2),
                    payoutRatio = 11,
                    baseUnits = 1,
                    isInside = true,
                    xPercent = zeroW,
                    yPercent = (1.5f / 3f) * mainH
                )
            )
        }

        // 8. Dozens (2:1)
        val doz1 = (1..12).toList()
        val doz2 = (13..24).toList()
        val doz3 = (25..36).toList()

        candidates.add(
            CandidateBet(
                betId = "doz-1",
                name = "1st 12 (1-12)",
                type = "dozen",
                numbers = doz1,
                mask = toBitmask(doz1),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + (0.5f / 3f * numW),
                yPercent = mainH + dozH * 0.5f
            )
        )
        candidates.add(
            CandidateBet(
                betId = "doz-2",
                name = "2nd 12 (13-24)",
                type = "dozen",
                numbers = doz2,
                mask = toBitmask(doz2),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + (1.5f / 3f * numW),
                yPercent = mainH + dozH * 0.5f
            )
        )
        candidates.add(
            CandidateBet(
                betId = "doz-3",
                name = "3rd 12 (25-36)",
                type = "dozen",
                numbers = doz3,
                mask = toBitmask(doz3),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + (2.5f / 3f * numW),
                yPercent = mainH + dozH * 0.5f
            )
        )

        // 9. Columns (2:1)
        val colTop = (3..36 step 3).toList()
        val colMid = (2..35 step 3).toList()
        val colBot = (1..34 step 3).toList()

        candidates.add(
            CandidateBet(
                betId = "2to1-top",
                name = "Col 3 (Top Row)",
                type = "column",
                numbers = colTop,
                mask = toBitmask(colTop),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + numW + colW * 0.5f,
                yPercent = (0.5f / 3f) * mainH
            )
        )
        candidates.add(
            CandidateBet(
                betId = "2to1-mid",
                name = "Col 2 (Mid Row)",
                type = "column",
                numbers = colMid,
                mask = toBitmask(colMid),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + numW + colW * 0.5f,
                yPercent = (1.5f / 3f) * mainH
            )
        )
        candidates.add(
            CandidateBet(
                betId = "2to1-bot",
                name = "Col 1 (Bot Row)",
                type = "column",
                numbers = colBot,
                mask = toBitmask(colBot),
                payoutRatio = 2,
                baseUnits = 1,
                isInside = false,
                xPercent = zeroW + numW + colW * 0.5f,
                yPercent = (2.5f / 3f) * mainH
            )
        )

        // 10. Even-Money Outside Bets (1:1)
        val lowNums = (1..18).toList()
        val evenNums = (1..36).filter { it % 2 == 0 }
        val redNumsList = RED_NUMS.toList()
        val blackNumsList = (1..36).filter { !RED_NUMS.contains(it) }
        val oddNums = (1..36).filter { it % 2 != 0 }
        val highNums = (19..36).toList()

        val outsideList = listOf(
            Triple("low", "Low (1-18)", lowNums),
            Triple("even", "EVEN", evenNums),
            Triple("red", "Red", redNumsList),
            Triple("black", "Black", blackNumsList),
            Triple("odd", "ODD", oddNums),
            Triple("high", "High (19-36)", highNums)
        )

        outsideList.forEachIndexed { idx, item ->
            candidates.add(
                CandidateBet(
                    betId = item.first,
                    name = item.second,
                    type = "even_money",
                    numbers = item.third,
                    mask = toBitmask(item.third),
                    payoutRatio = 1,
                    baseUnits = 1,
                    isInside = false,
                    xPercent = zeroW + ((idx + 0.5f) / 6f * numW),
                    yPercent = mainH + dozH + outH * 0.5f
                )
            )
        }

        return candidates
    }

    /**
     * Compute pocket frequencies with recency decay, wheel-rotor proximity reinforcement,
     * Empirical Bayes repeater concentration, and first-order Markov transition memory.
     */
    fun computeFrequencies(spins: List<Int>, isUS: Boolean): Map<Int, Double> {
        val maxPocket = if (isUS) 37 else 36
        val freqs = mutableMapOf<Int, Double>()
        for (i in 0..maxPocket) freqs[i] = 0.0

        val n = spins.size
        if (n == 0) return freqs

        val counts = IntArray(maxPocket + 1)
        spins.forEachIndexed { idx, s ->
            if (s in 0..maxPocket) {
                counts[s]++
            }
            val distFromEnd = (n - 1) - idx
            // Exponential recency decay
            var weight = max(0.15, exp(-distFromEnd / 18.0))

            // Proximity bonus relative to previous spin along physical wheel rotor
            if (idx > 0) {
                val prev = spins[idx - 1]
                val pocketDist = getWheelDistance(s, prev, isUS)
                val lambda = 2.8
                val maxMultiplier = 2.5
                val proximityBonus = (maxMultiplier - 1.0) * exp(-pocketDist / lambda)
                weight *= (1.0 + proximityBonus)

                freqs[prev] = (freqs[prev] ?: 0.0) + (weight * proximityBonus * 0.5)
            }

            freqs[s] = (freqs[s] ?: 0.0) + weight
        }

        // Empirical Bayes / Poisson Clumping repeater concentration & 1st-order Markov transition boost
        if (n >= 6) {
            for (p in 0..maxPocket) {
                val c = counts[p]
                if (c >= 2) {
                    freqs[p] = (freqs[p] ?: 0.0) * (1.0 + 0.12 * (c - 1))
                }
            }
            val lastSpin = spins[n - 1]
            for (k in 0 until n - 1) {
                if (spins[k] == lastSpin) {
                    val nextSpin = spins[k + 1]
                    if (nextSpin in 0..maxPocket) {
                        val transRecency = exp(-((n - 1 - k).toDouble() / 14.0))
                        freqs[nextSpin] = (freqs[nextSpin] ?: 0.0) + 0.45 * transRecency
                    }
                }
            }
        }

        return freqs
    }

    /**
     * Computes Macro-Scale Circular Von Mises Kernel Density Estimation (spatial rotor diffusion)
     * combined with Dealer Signature angular step velocity projection for Outside/Sector evaluation.
     */
    fun computeMacroRotorWeights(
        spins: List<Int>,
        baseFreqs: Map<Int, Double>,
        isUS: Boolean
    ): DoubleArray {
        val maxPocket = if (isUS) 37 else 36
        val totalPockets = maxPocket + 1
        val macro = DoubleArray(totalPockets)
        val kernel = if (isUS) VON_MISES_KERNEL_US else VON_MISES_KERNEL_EU

        // 1. Circular Von Mises spatial diffusion across physical wheel neighbors (d <= 6)
        for (q in 0..maxPocket) {
            val wq = baseFreqs[q] ?: 0.0
            if (wq > 0.0) {
                for (p in 0..maxPocket) {
                    val d = getWheelDistance(p, q, isUS)
                    if (d <= 6) {
                        macro[p] += wq * kernel[d]
                    }
                }
            }
        }

        // 2. Dealer Signature / Physical Rotor Step Velocity Projection
        val n = spins.size
        if (n >= 3) {
            val rotor = if (isUS) DOUBLE_ZERO_ROTOR else SINGLE_ZERO_ROTOR
            val rotorIdx = if (isUS) DOUBLE_ZERO_ROTOR_IDX else SINGLE_ZERO_ROTOR_IDX
            val rotorSize = rotor.size
            val lastSpin = spins[n - 1]
            val lastIdx = if (lastSpin in 0..maxPocket) rotorIdx[lastSpin] else -1

            if (lastIdx >= 0) {
                val startIdx = max(1, n - 12)
                for (idx in startIdx until n) {
                    val prevSpin = spins[idx - 1]
                    val currSpin = spins[idx]
                    val iPrev = if (prevSpin in 0..maxPocket) rotorIdx[prevSpin] else -1
                    val iCurr = if (currSpin in 0..maxPocket) rotorIdx[currSpin] else -1
                    if (iPrev >= 0 && iCurr >= 0) {
                        val step = (iCurr - iPrev + rotorSize) % rotorSize
                        val rec = exp(-((n - 1 - idx).toDouble() / 8.0))
                        val projIdx = (lastIdx + step) % rotorSize
                        for (off in -2..2) {
                            val targetPocket = rotor[(projIdx + off + rotorSize) % rotorSize]
                            macro[targetPocket] += rec * DEALER_DISPERSION_KERNEL[abs(off)]
                        }
                    }
                }
            }
        }

        return macro
    }

    /**
     * Identifies the K coldest (least frequent) numbers over the window, breaking ties by:
     * 1. True drought length descending (most consecutive spins missed since last hit)
     * 2. Physical wheel-sector isolation (lowest rotor-neighborhood recency heat when unseen)
     */
    fun getColdBannedNumbers(spins: List<Int>, count: Int, isUS: Boolean): Set<Int> {
        if (count <= 0) return emptySet()
        val maxPocket = if (isUS) 37 else 36
        val totalPockets = maxPocket + 1
        val counts = IntArray(totalPockets)
        val lastSeenIndex = IntArray(totalPockets) { -1 }
        val recencyByPocket = DoubleArray(totalPockets)

        val n = spins.size
        for (idx in 0 until n) {
            val s = spins[idx]
            if (s in 0..maxPocket) {
                counts[s]++
                lastSeenIndex[s] = idx
                val distFromEnd = (n - 1) - idx
                recencyByPocket[s] += max(0.15, exp(-distFromEnd / 18.0))
            }
        }

        // Precompute rotor neighborhood heat for unseen pockets (where counts[p] == 0 and drought == n)
        val rotorHeat = DoubleArray(totalPockets)
        if (n > 0) {
            for (p in 0..maxPocket) {
                if (counts[p] == 0) {
                    var heat = 0.0
                    for (q in 0..maxPocket) {
                        val qRecency = recencyByPocket[q]
                        if (qRecency > 0.0) {
                            val dist = getWheelDistance(p, q, isUS)
                            heat += qRecency * PROXIMITY_DECAY_TABLE[min(dist, PROXIMITY_DECAY_TABLE.size - 1)]
                        }
                    }
                    rotorHeat[p] = heat
                }
            }
        }

        return (0..maxPocket)
            .sortedWith { a, b ->
                val countCmp = counts[a].compareTo(counts[b])
                if (countCmp != 0) {
                    countCmp
                } else {
                    // True drought length = (n - 1) - lastSeenIndex (or n when lastSeenIndex == -1).
                    // Larger drought corresponds to smaller lastSeenIndex.
                    val droughtCmp = lastSeenIndex[a].compareTo(lastSeenIndex[b])
                    if (droughtCmp != 0) {
                        droughtCmp
                    } else {
                        val heatCmp = rotorHeat[a].compareTo(rotorHeat[b])
                        if (heatCmp != 0) heatCmp else a.compareTo(b)
                    }
                }
            }
            .take(count)
            .toSet()
    }

    /**
     * Creates a progression multiplier state tracker.
     */
    class ProgressionTracker(private val type: ProgressionType) {
        private var ls = 0 // loss streak
        private var ws = 0 // win streak
        private var netUnits = 0.0
        private val fibSeq = intArrayOf(1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89)
        private var fibIdx = 0
        private val labSeq = mutableListOf(1, 2, 3, 4)
        private var oscarGoal = 1.0
        private var oscarUnit = 1
        private val seq1326 = intArrayOf(1, 3, 2, 6)
        private var seqIdx1326 = 0
        private val guetLevels = intArrayOf(1, 2, 3, 4, 6, 8, 12, 16)
        private var guetIdx = 0
        private var tierStep = 0

        // Dedicated state for D'Alembert & Contra D'Alembert step progressions
        private var dalembertUnit = 1
        private var contraDalembertUnit = 1

        // Hollandish 3-spin block evaluation
        private var hollandishStage = 0
        private var hollandishSpinsInBlock = 0
        private var hollandishBlockWins = 0
        private var hollandishBlockLosses = 0

        // Modern progressions
        private var bestOfAllUnit = 1
        private val blueRibbonLevels = intArrayOf(1, 2, 3, 5, 8)
        private var blueRibbonIdx = 0
        private var spinOffPhase = 0
        private val egdLevels = intArrayOf(1, 2, 3, 5, 8, 12)
        private var egdIdx = 0

        fun getMultiplier(): Int {
            return when (type) {
                ProgressionType.MARTINGALE -> 2.0.pow(min(ls, 8)).toInt()
                ProgressionType.GRAND_MARTINGALE -> (2.0.pow(min(ls, 7)) + ls).toInt()
                ProgressionType.DALEMBERT -> dalembertUnit
                ProgressionType.DOUBLE_DALEMBERT -> dalembertUnit
                ProgressionType.FIBONACCI -> fibSeq[min(fibIdx, fibSeq.size - 1)]
                ProgressionType.LABOUCHERE -> {
                    if (labSeq.size < 2) labSeq.firstOrNull() ?: 1
                    else labSeq.first() + labSeq.last()
                }
                ProgressionType.TIER_ET_TOUT -> if (tierStep == 0) 2 else if (tierStep == 1) 4 else 1
                ProgressionType.PAROLI -> 2.0.pow(min(ws, 2)).toInt()
                ProgressionType.CONTRA_DALEMBERT -> contraDalembertUnit
                ProgressionType.REVERSE_LABOUCHERE -> {
                    if (labSeq.size < 2) labSeq.firstOrNull() ?: 1
                    else labSeq.first() + labSeq.last()
                }
                ProgressionType.OSCARS_GRIND -> oscarUnit
                ProgressionType.SYSTEM_1326 -> seq1326[seqIdx1326 % 4]
                ProgressionType.PARLAY -> min(8, 2.0.pow(min(ws, 2)).toInt())
                ProgressionType.TRIPLE_LUX -> min(9, 3.0.pow(min(ws, 1)).toInt())
                ProgressionType.HOLLANDISH -> 1 + (hollandishStage * 2)
                ProgressionType.GUETTING -> guetLevels[min(guetIdx, guetLevels.size - 1)]
                ProgressionType.ALL_IN -> min(5, 1 + ws * 2)

                // Modern Progressions
                ProgressionType.BEST_OF_ALL -> bestOfAllUnit
                ProgressionType.BLUE_RIBBON -> blueRibbonLevels[min(blueRibbonIdx, blueRibbonLevels.size - 1)]
                ProgressionType.SPIN_OFF_3 -> when (spinOffPhase) { 0 -> 1; 1 -> 2; 2 -> 4; else -> 1 }
                ProgressionType.EGD_SP_2 -> egdLevels[min(egdIdx, egdLevels.size - 1)]

                else -> 1 // Flat and fixed layouts
            }
        }

        fun record(won: Boolean, netGainedUnits: Double, isPush: Boolean = false) {
            if (isPush) return
            netUnits += netGainedUnits
            if (won) {
                ws++
                ls = 0
                fibIdx = max(0, fibIdx - 2)
                seqIdx1326 = (seqIdx1326 + 1) % 4
                guetIdx = min(guetLevels.size - 1, guetIdx + 1)
                tierStep = 0

                // D'Alembert decreases by 1 unit on win
                dalembertUnit = max(1, dalembertUnit - 1)
                // Contra D'Alembert increases by 1 unit on win
                contraDalembertUnit++

                // Paroli: max 3 consecutive wins (1 -> 2 -> 4), then resets to 1
                if (type == ProgressionType.PAROLI && ws >= 3) {
                    ws = 0
                }
                // Parlay: reset after 3 wins
                if (type == ProgressionType.PARLAY && ws >= 3) {
                    ws = 0
                }
                // Triple Lux: reset after 2 wins (1 -> 3 -> 9)
                if (type == ProgressionType.TRIPLE_LUX && ws >= 2) {
                    ws = 0
                }

                // Hollandish block evaluation
                hollandishBlockWins++
                hollandishSpinsInBlock++
                if (hollandishSpinsInBlock >= 3) {
                    if (hollandishBlockWins > hollandishBlockLosses) {
                        hollandishStage = max(0, hollandishStage - 1)
                    } else {
                        hollandishStage++
                    }
                    hollandishSpinsInBlock = 0
                    hollandishBlockWins = 0
                    hollandishBlockLosses = 0
                }

                // Modern progressions
                bestOfAllUnit = min(3, bestOfAllUnit + 1)
                blueRibbonIdx = 0
                spinOffPhase = (spinOffPhase + 1) % 3
                egdIdx = max(0, egdIdx - 2)

                if (type == ProgressionType.LABOUCHERE) {
                    if (labSeq.size >= 2) {
                        labSeq.removeAt(0)
                        labSeq.removeAt(labSeq.size - 1)
                    } else if (labSeq.size == 1) {
                        labSeq.removeAt(0)
                    }
                    if (labSeq.isEmpty()) labSeq.addAll(listOf(1, 2, 3, 4))
                }
                if (type == ProgressionType.REVERSE_LABOUCHERE) {
                    val add = (labSeq.firstOrNull() ?: 1) + (labSeq.lastOrNull() ?: 1)
                    labSeq.add(add)
                }
                if (type == ProgressionType.OSCARS_GRIND) {
                    if (netUnits >= oscarGoal) {
                        oscarUnit = 1
                        netUnits = 0.0
                    } else {
                        oscarUnit++
                    }
                }
            } else {
                ls++
                ws = 0
                fibIdx = min(fibSeq.size - 1, fibIdx + 1)
                seqIdx1326 = 0
                guetIdx = max(0, guetIdx - 1)
                tierStep = (tierStep + 1) % 3

                // D'Alembert increases by 1 unit on loss (+2 for Double D'Alembert)
                if (type == ProgressionType.DOUBLE_DALEMBERT) {
                    dalembertUnit += 2
                } else {
                    dalembertUnit++
                }
                // Contra D'Alembert decreases by 1 unit on loss (min 1)
                contraDalembertUnit = max(1, contraDalembertUnit - 1)

                // Hollandish block evaluation
                hollandishBlockLosses++
                hollandishSpinsInBlock++
                if (hollandishSpinsInBlock >= 3) {
                    if (hollandishBlockWins > hollandishBlockLosses) {
                        hollandishStage = max(0, hollandishStage - 1)
                    } else {
                        hollandishStage++
                    }
                    hollandishSpinsInBlock = 0
                    hollandishBlockWins = 0
                    hollandishBlockLosses = 0
                }

                // Modern progressions
                bestOfAllUnit = max(1, bestOfAllUnit - 1)
                blueRibbonIdx = min(blueRibbonLevels.size - 1, blueRibbonIdx + 1)
                spinOffPhase = 0
                egdIdx = min(egdLevels.size - 1, egdIdx + 1)

                if (type == ProgressionType.LABOUCHERE) {
                    val add = (labSeq.firstOrNull() ?: 1) + (labSeq.lastOrNull() ?: 1)
                    labSeq.add(add)
                }
                if (type == ProgressionType.REVERSE_LABOUCHERE) {
                    if (labSeq.size >= 2) {
                        labSeq.removeAt(0)
                        labSeq.removeAt(labSeq.size - 1)
                    }
                    if (labSeq.isEmpty()) labSeq.addAll(listOf(1, 2, 3, 4))
                }
            }
        }

        fun isCycleComplete(): Boolean {
            return when (type) {
                ProgressionType.MARTINGALE, ProgressionType.GRAND_MARTINGALE -> ls == 0
                ProgressionType.DALEMBERT, ProgressionType.DOUBLE_DALEMBERT -> dalembertUnit == 1
                ProgressionType.CONTRA_DALEMBERT -> contraDalembertUnit == 1
                ProgressionType.FIBONACCI -> fibIdx == 0
                ProgressionType.LABOUCHERE -> labSeq.size == 4 && labSeq == listOf(1, 2, 3, 4)
                ProgressionType.TIER_ET_TOUT -> tierStep == 0
                ProgressionType.OSCARS_GRIND -> oscarUnit == 1
                ProgressionType.PAROLI, ProgressionType.PARLAY, ProgressionType.TRIPLE_LUX -> ws == 0
                ProgressionType.SYSTEM_1326 -> seqIdx1326 == 0
                ProgressionType.HOLLANDISH -> hollandishStage == 0 && hollandishSpinsInBlock == 0
                ProgressionType.GUETTING -> guetIdx == 0
                ProgressionType.ALL_IN -> ws == 0
                ProgressionType.BEST_OF_ALL -> bestOfAllUnit == 1
                ProgressionType.BLUE_RIBBON -> blueRibbonIdx == 0
                ProgressionType.SPIN_OFF_3 -> spinOffPhase == 0
                ProgressionType.EGD_SP_2 -> egdIdx == 0
                else -> true
            }
        }

        fun reset() {
            ls = 0
            ws = 0
            netUnits = 0.0
            fibIdx = 0
            labSeq.clear()
            labSeq.addAll(listOf(1, 2, 3, 4))
            oscarGoal = 1.0
            oscarUnit = 1
            seqIdx1326 = 0
            guetIdx = 0
            tierStep = 0
            dalembertUnit = 1
            contraDalembertUnit = 1
            hollandishStage = 0
            hollandishSpinsInBlock = 0
            hollandishBlockWins = 0
            hollandishBlockLosses = 0
            bestOfAllUnit = 1
            blueRibbonIdx = 0
            spinOffPhase = 0
            egdIdx = 0
        }
    }

    /**
     * Synthesizes the optimal bet pattern for the current window based on family filter.
     */
    fun selectOptimalBets(
        candidates: List<CandidateBet>,
        freqs: Map<Int, Double>,
        coldBanned: Set<Int>,
        family: BetFamily,
        maxUnits: Int = 6,
        coldRule: ColdRule = ColdRule.STRICT,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0,
        progression: ProgressionType = ProgressionType.AUTO,
        recentSpins: List<Int> = emptyList(),
        isUS: Boolean = false
    ): List<CandidateBet> {
        // --- 1. Fixed Layout Systems ---
        when (progression) {
            ProgressionType.JAMES_BOND -> {
                val unitScale = max(1, ceil(baseOutsideBet / (14.0 * max(0.1, baseInsideBet))).toInt())
                val high = candidates.find { it.betId == "high" }?.copy(baseUnits = 14 * unitScale, isInside = true)
                val line1318 = candidates.find { it.type == "six_line" && it.numbers.contains(13) && it.numbers.contains(18) }?.copy(baseUnits = 5 * unitScale, isInside = true)
                val zero = candidates.find { it.betId == "num-0" }?.copy(baseUnits = 1 * unitScale, isInside = true)
                return listOfNotNull(high, line1318, zero)
            }
            ProgressionType.ROMANOSKY -> {
                val unitScale = max(1, ceil(baseOutsideBet / (3.0 * max(0.1, baseInsideBet))).toInt())
                // 3 units each on 2 hottest dozens
                val dozens = candidates.filter { it.type == "dozen" }
                    .map { d -> Pair(d, d.numbers.sumOf { freqs[it] ?: 0.0 }) }
                    .sortedByDescending { it.second }
                if (dozens.size >= 2) {
                    val doz1 = dozens[0].first.copy(baseUnits = 3 * unitScale, isInside = true)
                    val doz2 = dozens[1].first.copy(baseUnits = 3 * unitScale, isInside = true)
                    val coveredDozensMask = doz1.mask or doz2.mask
                    // 2 corners from the remaining numbers
                    val corners = candidates.filter { it.type == "corner" && (it.mask and coveredDozensMask) == 0L }
                        .map { c -> Pair(c, c.numbers.sumOf { freqs[it] ?: 0.0 }) }
                        .sortedByDescending { it.second }
                    val selectedCorners = mutableListOf<CandidateBet>()
                    var cornerMask = 0L
                    for (cPair in corners) {
                        if (selectedCorners.size >= 2) break
                        if ((cornerMask and cPair.first.mask) == 0L) {
                            selectedCorners.add(cPair.first.copy(baseUnits = 1 * unitScale, isInside = true))
                            cornerMask = cornerMask or cPair.first.mask
                        }
                    }
                    return listOf(doz1, doz2) + selectedCorners
                }
            }
            ProgressionType.KAVOURAS -> {
                val corner = candidates.find { it.type == "corner" && (it.numbers.contains(0) || it.numbers.contains(35)) }
                    ?: candidates.firstOrNull { it.type == "corner" }
                val cMask = corner?.mask ?: 0L
                val line = candidates.filter { it.type == "six_line" && (it.mask and cMask) == 0L }
                    .maxByOrNull { l -> l.numbers.sumOf { freqs[it] ?: 0.0 } }
                val lMask = (line?.mask ?: 0L) or cMask
                val splits = mutableListOf<CandidateBet>()
                var splitMask = lMask
                val availableSplits = candidates.filter { it.type == "split" }
                    .sortedByDescending { sp -> sp.numbers.sumOf { freqs[it] ?: 0.0 } }
                for (sp in availableSplits) {
                    if (splits.size >= 5) break
                    if ((splitMask and sp.mask) == 0L) {
                        splits.add(sp.copy(baseUnits = 1))
                        splitMask = splitMask or sp.mask
                    }
                }
                return listOfNotNull(corner?.copy(baseUnits = 1), line?.copy(baseUnits = 2)) + splits
            }
            ProgressionType.SHOTWELL -> {
                val bestLine = candidates.filter { it.type == "six_line" }
                    .maxByOrNull { l -> l.numbers.sumOf { freqs[it] ?: 0.0 } }
                val lineMask = bestLine?.mask ?: 0L
                val rotor = if (candidates.any { it.numbers.contains(37) }) DOUBLE_ZERO_ROTOR else SINGLE_ZERO_ROTOR
                val step = rotor.size / 4
                val straightBets = mutableListOf<CandidateBet>()
                for (k in 0..3) {
                    val num = rotor[(k * step) % rotor.size]
                    if ((lineMask and (1L shl num)) == 0L) {
                        val straight = candidates.find { it.type == "straight" && it.numbers.contains(num) }
                        if (straight != null && straightBets.size < 4) {
                            straightBets.add(straight.copy(baseUnits = 1))
                        }
                    }
                }
                return listOfNotNull(bestLine?.copy(baseUnits = 1)) + straightBets
            }
            ProgressionType.FOUR_PILLARS -> {
                val selectedCorners = mutableListOf<CandidateBet>()
                var cMask = 0L
                val allCorners = candidates.filter { it.type == "corner" }
                    .sortedByDescending { c -> c.numbers.sumOf { freqs[it] ?: 0.0 } }
                for (c in allCorners) {
                    if (selectedCorners.size >= 4) break
                    if ((cMask and c.mask) == 0L) {
                        selectedCorners.add(c.copy(baseUnits = 1))
                        cMask = cMask or c.mask
                    }
                }
                return selectedCorners
            }
            ProgressionType.ANDRUCCI -> {
                val hotStraights = candidates.filter { it.type == "straight" && !coldBanned.contains(it.numbers.first()) }
                    .sortedByDescending { freqs[it.numbers.first()] ?: 0.0 }
                    .take(5)
                    .map { it.copy(baseUnits = 1) }
                return hotStraights
            }
            else -> {}
        }

        val familyCandidates = candidates.filter { c ->
            when (family) {
                BetFamily.INSIDE -> c.isInside
                BetFamily.OUTSIDE -> !c.isInside
                BetFamily.COMBINED -> true
            }
        }

        fun getEqualizedUnits(c: CandidateBet): Int {
            if (!equalizePayout || !c.isInside) return c.baseUnits
            return when (c.payoutRatio) {
                35 -> 1 // Straight (35:1) -> 1 * 36 = 36 gross
                17 -> 2 // Split (17:1) -> 2 * 18 = 36 gross
                11 -> 3 // Street (11:1) -> 3 * 12 = 36 gross
                8 -> 4  // Corner (8:1) -> 4 * 9 = 36 gross
                5 -> 6  // Line (5:1) -> 6 * 6 = 36 gross
                else -> max(1, 36 / (c.payoutRatio + 1))
            }
        }

        val effectiveIsUS = isUS || candidates.any { it.numbers.contains(37) }
        val macroWeights = if (family != BetFamily.INSIDE) {
            computeMacroRotorWeights(recentSpins, freqs, effectiveIsUS)
        } else null
        val totalRotorPockets = if (effectiveIsUS) 38.0 else 37.0

        fun scoreCandidate(c: CandidateBet): Double {
            val weightSum = if (!c.isInside && macroWeights != null) {
                var sum = 0.0
                for (num in c.numbers) {
                    if (num in macroWeights.indices) {
                        sum += macroWeights[num]
                    }
                }
                sum
            } else {
                c.numbers.sumOf { freqs[it] ?: 0.0 }
            }
            val coveragePower = when {
                !c.isInside -> 0.45
                family == BetFamily.COMBINED -> 0.80
                else -> 0.65
            }
            return (weightSum / c.baseUnits) * (c.numbers.size.toDouble().pow(coveragePower))
        }

        fun getPenaltyFactor(c: CandidateBet): Double {
            val coldCount = c.numbers.count { coldBanned.contains(it) }
            if (coldCount == 0 || coldBanned.isEmpty()) return 1.0
            if (!c.isInside) {
                val expectedCold = (c.numbers.size * coldBanned.size) / totalRotorPockets
                if (coldCount <= expectedCold) {
                    return max(0.25, 1.0 - (0.12 * coldCount))
                }
                val penaltyRatio = coldCount.toDouble() / max(1.0, c.numbers.size * 0.35)
                return max(0.08, 1.0 - penaltyRatio)
            }
            val penaltyRatio = coldCount.toDouble() / max(1.0, c.numbers.size * 0.5)
            return max(0.08, 1.0 - penaltyRatio)
        }

        fun buildSelection(scoredList: List<Pair<CandidateBet, Double>>): List<CandidateBet> {
            val selected = mutableListOf<CandidateBet>()
            var coveredMask = 0L

            if (family == BetFamily.OUTSIDE) {
                val dozens = scoredList.filter { it.first.type == "dozen" || it.first.type == "column" }
                val evens = scoredList.filter { it.first.type == "even_money" }

                if (pushOrProfit) {
                    // Clean 2 Dozens/Columns: any hit gives 2:1 on 1 unit vs 2 units total stake = +1 unit net profit.
                    // Zero cannibalizing 3rd bet -> Guaranteed profit on hit, zero partial losses, zero pushes.
                    dozens.take(2).forEach {
                        selected.add(it.first)
                    }
                } else {
                    dozens.take(2).forEach {
                        selected.add(it.first)
                    }
                    evens.take(1).forEach {
                        selected.add(it.first)
                    }
                }
                return selected
            }

            if (family == BetFamily.COMBINED) {
                val insideScored = scoredList.filter { it.first.isInside }
                val outsideScored = scoredList.filter { !it.first.isInside }

                var insideStakeDollars = 0.0
                for (pair in insideScored) {
                    val c = pair.first
                    if (selected.size >= 3) break
                    if ((coveredMask and c.mask) == 0L) {
                        val effUnits = getEqualizedUnits(c)
                        selected.add(c.copy(baseUnits = effUnits))
                        coveredMask = coveredMask or c.mask
                        insideStakeDollars += effUnits * baseInsideBet
                    }
                }

                outsideScored.firstOrNull()?.let { (outBet, _) ->
                    val effOutsideUnits = if (pushOrProfit) {
                        // Guarantee that if only the outside bet hits, grossPayout >= totalStake:
                        // (payoutRatio + 1) * (units * baseOutsideBet) >= insideStakeDollars + (units * baseOutsideBet)
                        // payoutRatio * units * baseOutsideBet >= insideStakeDollars
                        // units >= insideStakeDollars / (payoutRatio * baseOutsideBet)
                        val p = max(1, outBet.payoutRatio)
                        val minUnits = ceil(insideStakeDollars / (p * max(0.1, baseOutsideBet))).toInt()
                        max(1, minUnits)
                    } else {
                        outBet.baseUnits
                    }
                    selected.add(outBet.copy(baseUnits = effOutsideUnits))
                }
                return selected
            }

            // INSIDE
            val targetCount = if (equalizePayout) 5 else maxUnits
            for (pair in scoredList) {
                val c = pair.first
                if (selected.size >= targetCount) break
                if ((coveredMask and c.mask) == 0L) {
                    val effUnits = getEqualizedUnits(c)
                    val candidate = c.copy(baseUnits = effUnits)
                    val testSelected = selected + candidate
                    val totalStake = testSelected.sumOf { it.baseUnits * baseInsideBet }
                    val allProfitable = testSelected.all { b ->
                        val grossWin = (b.payoutRatio + 1) * (b.baseUnits * baseInsideBet)
                        grossWin > totalStake
                    }
                    if (allProfitable) {
                        selected.add(candidate)
                        coveredMask = coveredMask or c.mask
                    }
                }
            }

            return selected
        }

        return when (coldRule) {
            ColdRule.STRICT -> {
                val eligible = familyCandidates.filter { c ->
                    !c.numbers.any { coldBanned.contains(it) }
                }
                val scored = eligible.map { c -> Pair(c, scoreCandidate(c)) }
                    .sortedByDescending { it.second }
                buildSelection(scored)
            }
            ColdRule.PENALTY -> {
                val scored = familyCandidates.map { c ->
                    val base = scoreCandidate(c)
                    val factor = getPenaltyFactor(c)
                    Pair(c, base * factor)
                }.sortedByDescending { it.second }
                buildSelection(scored)
            }
            ColdRule.WATERFALL -> {
                val zeroColdEligible = familyCandidates.filter { c -> !c.numbers.any { coldBanned.contains(it) } }
                val zeroColdScored = zeroColdEligible.map { c -> Pair(c, scoreCandidate(c)) }.sortedByDescending { it.second }
                val initialSelection = buildSelection(zeroColdScored)

                val targetBets = when (family) {
                    BetFamily.OUTSIDE -> if (pushOrProfit) 2 else 3
                    BetFamily.COMBINED -> 4
                    BetFamily.INSIDE -> if (equalizePayout) 5 else maxUnits
                }
                if (initialSelection.isNotEmpty() && (initialSelection.size >= targetBets || initialSelection.sumOf { it.numbers.size } >= 24)) {
                    initialSelection
                } else {
                    val fallbackScored = familyCandidates.map { c ->
                        val coldCount = c.numbers.count { coldBanned.contains(it) }
                        val rankScore = (100.0 - coldCount * 20.0) + (scoreCandidate(c) * 0.01)
                        Pair(c, rankScore)
                    }.sortedByDescending { it.second }
                    val fullSelection = buildSelection(fallbackScored)
                    if (initialSelection.size > fullSelection.size) initialSelection else fullSelection
                }
            }
            ColdRule.HYBRID -> {
                val eligible = familyCandidates.filter { c ->
                    if (c.isInside) {
                        !c.numbers.any { coldBanned.contains(it) }
                    } else {
                        true
                    }
                }
                val scored = eligible.map { c ->
                    val base = scoreCandidate(c)
                    val factor = if (!c.isInside) getPenaltyFactor(c) else 1.0
                    Pair(c, base * factor)
                }.sortedByDescending { it.second }
                buildSelection(scored)
            }
        }
    }

    data class PrecomputedRound(
        val spinIndex: Int,
        val spinNumber: Int,
        val spinMask: Long,
        val bets: List<CandidateBet>,
        val coldSet: Set<Int>,
        val coldStr: String,
        val betsDesc: String,
        val baseStake: Double = 0.0,
        val minNetWinPerUnit: Double = 1.0
    )

    fun precomputeRounds(
        spins: List<Int>,
        isUS: Boolean,
        family: BetFamily,
        coldLimit: Int,
        lookbackWindow: Int,
        coldRule: ColdRule = ColdRule.STRICT,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0,
        progression: ProgressionType = ProgressionType.AUTO
    ): List<PrecomputedRound> {
        val candidates = getCandidates(isUS)
        val rounds = ArrayList<PrecomputedRound>(spins.size)
        for (i in spins.indices) {
            val historicalSub = spins.subList(0, i)
            val coldSet = getColdBannedNumbers(historicalSub, coldLimit, isUS)
            val lookbackSlice = historicalSub.takeLast(min(lookbackWindow, historicalSub.size))
            val freqs = computeFrequencies(lookbackSlice, isUS)
            val bets = selectOptimalBets(
                candidates = candidates,
                freqs = freqs,
                coldBanned = coldSet,
                family = family,
                coldRule = coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                progression = progression,
                recentSpins = lookbackSlice,
                isUS = isUS
            )
            val spinNum = spins[i]

            var baseStake = 0.0
            bets.forEach { b ->
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                baseStake += b.baseUnits * baseBet
            }

            var minNetWinPerUnit = Double.MAX_VALUE
            if (baseStake > 0.0) {
                val coveredNumbers = mutableSetOf<Int>()
                bets.forEach { coveredNumbers.addAll(it.numbers) }
                for (num in coveredNumbers) {
                    var unitGross = 0.0
                    bets.forEach { b ->
                        if (b.numbers.contains(num)) {
                            val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                            unitGross += (b.baseUnits * baseBet) * (b.payoutRatio + 1)
                        }
                    }
                    val unitNet = unitGross - baseStake
                    if (unitNet > 0 && unitNet < minNetWinPerUnit) {
                        minNetWinPerUnit = unitNet
                    }
                }
            }
            if (minNetWinPerUnit == Double.MAX_VALUE || minNetWinPerUnit <= 0.0) {
                minNetWinPerUnit = max(1.0, baseStake)
            }

            rounds.add(
                PrecomputedRound(
                    spinIndex = i + 1,
                    spinNumber = spinNum,
                    spinMask = 1L shl spinNum,
                    bets = bets,
                    coldSet = coldSet,
                    coldStr = if (coldSet.isEmpty()) "None" else coldSet.joinToString(", ") { "#$it" },
                    betsDesc = bets.joinToString(", ") { "${it.name}${if (it.baseUnits > 1) " (${it.baseUnits}u)" else ""}" },
                    baseStake = baseStake,
                    minNetWinPerUnit = minNetWinPerUnit
                )
            )
        }
        return rounds
    }

    /**
     * Ultra-fast effective multiplier evaluation using precomputed baseStake and minNetWinPerUnit.
     * Executes in nanoseconds without allocating sets or iterating bet numbers.
     */
    fun computeEffectiveMultiplierFast(
        rawMultiplier: Int,
        baseStake: Double,
        minNetWinPerUnit: Double,
        bankroll: Double,
        peak: Double,
        progressionType: ProgressionType,
        riskCapEnabled: Boolean,
        maxBankrollRiskFraction: Double = 0.20
    ): Int {
        if (!riskCapEnabled || rawMultiplier <= 1 || baseStake <= 0.0) {
            return max(1, rawMultiplier)
        }

        var mult = rawMultiplier

        val deficit = peak - bankroll
        if (deficit > 0) {
            val targetSurplus = minNetWinPerUnit
            val neededMult = kotlin.math.ceil((deficit + targetSurplus) / minNetWinPerUnit).toInt()
            mult = min(mult, max(1, neededMult))
        } else if (progressionType.category == "Negative") {
            mult = 1
        }

        val maxAllowedStake = max(baseStake, bankroll * maxBankrollRiskFraction)
        val ceilingMult = max(1, (maxAllowedStake / baseStake).toInt())
        mult = min(mult, ceilingMult)

        return max(1, mult)
    }

    /**
     * Option A (Deficit-Aware Cap) & Capital Risk Ceiling (20% Bankroll Cap).
     */
    fun computeEffectiveMultiplier(
        rawMultiplier: Int,
        bets: List<CandidateBet>,
        bankroll: Double,
        peak: Double,
        baseInsideBet: Double,
        baseOutsideBet: Double,
        progressionType: ProgressionType,
        riskCapEnabled: Boolean,
        maxBankrollRiskFraction: Double = 0.20
    ): Int {
        if (!riskCapEnabled || rawMultiplier <= 1) {
            return max(1, rawMultiplier)
        }

        var baseStake = 0.0
        bets.forEach { b ->
            val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
            baseStake += b.baseUnits * baseBet
        }
        if (baseStake <= 0.0) return max(1, rawMultiplier)

        val coveredNumbers = mutableSetOf<Int>()
        bets.forEach { coveredNumbers.addAll(it.numbers) }

        var minNetWinPerUnit = Double.MAX_VALUE
        for (num in coveredNumbers) {
            var unitGross = 0.0
            bets.forEach { b ->
                if (b.numbers.contains(num)) {
                    val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                    unitGross += (b.baseUnits * baseBet) * (b.payoutRatio + 1)
                }
            }
            val unitNet = unitGross - baseStake
            if (unitNet > 0 && unitNet < minNetWinPerUnit) {
                minNetWinPerUnit = unitNet
            }
        }
        if (minNetWinPerUnit == Double.MAX_VALUE || minNetWinPerUnit <= 0.0) {
            minNetWinPerUnit = max(1.0, baseStake)
        }

        return computeEffectiveMultiplierFast(
            rawMultiplier = rawMultiplier,
            baseStake = baseStake,
            minNetWinPerUnit = minNetWinPerUnit,
            bankroll = bankroll,
            peak = peak,
            progressionType = progressionType,
            riskCapEnabled = riskCapEnabled,
            maxBankrollRiskFraction = maxBankrollRiskFraction
        )
    }

    /**
     * Simulates a bet configuration and progression across the trailing window.
     * Evaluates round-by-round returns, drawdown, win-rate, and stability metric.
     */
    fun evaluateSimulation(
        spins: List<Int>,
        isUS: Boolean,
        progression: ProgressionType,
        family: BetFamily,
        coldLimit: Int,
        lookbackWindow: Int = 37,
        startBankroll: Double,
        baseInsideBet: Double,
        baseOutsideBet: Double,
        precomputedRounds: List<PrecomputedRound>? = null,
        coldRule: ColdRule = ColdRule.STRICT,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        riskCap: Boolean = true,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1
    ): TrendRecommendation {
        val tracker = ProgressionTracker(progression)

        var bankroll = startBankroll
        var peak = startBankroll
        var maxDD = 0.0
        var winCount = 0
        var maxStakeReached = 0.0

        val logs = mutableListOf<SpinLedgerRecord>()
        val equityPoints = mutableListOf<EquityPoint>()
        val roundReturns = mutableListOf<Double>()
        val isAfterLookback = startMode == BettingStartMode.AFTER_LOOKBACK

        val rounds = precomputedRounds ?: precomputeRounds(
            spins = spins,
            isUS = isUS,
            family = family,
            coldLimit = coldLimit,
            lookbackWindow = lookbackWindow,
            coldRule = coldRule,
            equalizePayout = equalizePayout,
            pushOrProfit = pushOrProfit,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progression = progression
        )

        for (round in rounds) {
            val isObservational = isAfterLookback && round.spinIndex <= lookbackWindow

            if (isObservational) {
                val record = SpinLedgerRecord(
                    spinIndex = round.spinIndex,
                    spunNumber = round.spinNumber,
                    activeBetsSummary = "Lookback Seed (Observational)",
                    coldAvoided = round.coldStr,
                    progressionUsed = progression.displayName,
                    stake = 0.0,
                    multiplier = 0,
                    netGain = 0.0,
                    outcome = "SEED",
                    bankroll = bankroll,
                    placedBets = emptyList(),
                    rawMultiplier = 0,
                    switchNote = "Lookback Observation (${round.spinIndex}/$lookbackWindow)"
                )
                logs.add(record)

                equityPoints.add(
                    EquityPoint(
                        spinIndex = round.spinIndex,
                        spunNumber = round.spinNumber,
                        bankroll = bankroll,
                        netGain = 0.0,
                        record = record
                    )
                )
                continue
            }

            val rawMult = tracker.getMultiplier()
            val mult = computeEffectiveMultiplierFast(
                rawMultiplier = rawMult,
                baseStake = round.baseStake,
                minNetWinPerUnit = round.minNetWinPerUnit,
                bankroll = bankroll,
                peak = peak,
                progressionType = progression,
                riskCapEnabled = riskCap
            )

            var totalStake = 0.0
            var grossWin = 0.0

            round.bets.forEach { b ->
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                val betCost = b.baseUnits * baseBet * mult
                totalStake += betCost

                if ((b.mask and round.spinMask) != 0L) {
                    grossWin += betCost * (b.payoutRatio + 1)
                }
            }

            if (totalStake > maxStakeReached) {
                maxStakeReached = totalStake
            }

            val netGain = grossWin - totalStake
            bankroll += netGain
            roundReturns.add(netGain)

            val isWin = netGain > 0
            val isPush = netGain == 0.0
            val outcome = if (isWin) "WIN" else if (isPush) "PUSH" else "LOSS"

            if (isWin) winCount++
            if (bankroll > peak) peak = bankroll
            val dd = peak - bankroll
            if (dd > maxDD) maxDD = dd

            tracker.record(won = isWin, netGainedUnits = netGain / max(1.0, totalStake), isPush = isPush)

            val record = SpinLedgerRecord(
                spinIndex = round.spinIndex,
                spunNumber = round.spinNumber,
                activeBetsSummary = round.betsDesc,
                coldAvoided = round.coldStr,
                progressionUsed = progression.displayName,
                stake = totalStake,
                multiplier = mult,
                netGain = netGain,
                outcome = outcome,
                bankroll = bankroll,
                placedBets = round.bets,
                rawMultiplier = rawMult
            )
            logs.add(record)

            equityPoints.add(
                EquityPoint(
                    spinIndex = round.spinIndex,
                    spunNumber = round.spinNumber,
                    bankroll = bankroll,
                    netGain = netGain,
                    record = record
                )
            )
        }

        // Specifically evaluate recommendations for the upcoming NEXT spin (spin N+1),
        // using the full current history (up to lookbackWindow trailing spins) and current multiplier
        val candidates = getCandidates(isUS)
        val nextColdSet = getColdBannedNumbers(spins, coldLimit, isUS)
        val nextLookbackSlice = if (spins.isNotEmpty()) {
            spins.takeLast(min(lookbackWindow, spins.size))
        } else {
            emptyList()
        }
        val nextFreqs = computeFrequencies(nextLookbackSlice, isUS)
        val nextSelectedBets = selectOptimalBets(
            candidates = candidates,
            freqs = nextFreqs,
            coldBanned = nextColdSet,
            family = family,
            coldRule = coldRule,
            equalizePayout = equalizePayout,
            pushOrProfit = pushOrProfit,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progression = progression,
            recentSpins = nextLookbackSlice,
            isUS = isUS
        )

        val isWaitingForLookback = isAfterLookback && spins.size < lookbackWindow
        val nextRawMultiplier = if (isWaitingForLookback) 0 else tracker.getMultiplier()
        val nextMultiplier = if (isWaitingForLookback) 0 else computeEffectiveMultiplier(
            rawMultiplier = nextRawMultiplier,
            bets = nextSelectedBets,
            bankroll = bankroll,
            peak = peak,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progressionType = progression,
            riskCapEnabled = riskCap
        )

        // Compute Downside-Penalized Sortino Stability Score
        val n = roundReturns.size
        val avgReturn = if (n > 0) roundReturns.sum() / n else 0.0
        val downsideSemiVar = if (n > 0) {
            sqrt(roundReturns.sumOf { r -> if (r < 0) r.pow(2) else 0.0 } / n)
        } else 1.0

        val netProfit = bankroll - startBankroll
        val ddPenalty = 1.0 - (maxDD / (peak + 10.0)).coerceIn(0.0, 0.95)
        val stabilityScore = if (n > 0) ((avgReturn / (downsideSemiVar + 1.0)) * ddPenalty) else 0.0

        val activeRoundsCount = if (isAfterLookback) max(0, rounds.size - lookbackWindow) else rounds.size
        val winRate = if (activeRoundsCount > 0) (winCount.toDouble() / activeRoundsCount) * 100.0 else 0.0

        val recommendedBets = if (isWaitingForLookback) {
            emptyList()
        } else {
            nextSelectedBets.map { b ->
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                RecommendedBet(
                    bet = b,
                    units = b.baseUnits * nextMultiplier,
                    dollarAmount = b.baseUnits * baseBet * nextMultiplier
                )
            }
        }

        val totalUnits = recommendedBets.sumOf { it.units }
        val nextStake = recommendedBets.sumOf { it.dollarAmount }

        val quality = computeTrendQuality(equityPoints, startBankroll)

        return TrendRecommendation(
            bestProgression = progression,
            recommendedBets = recommendedBets,
            totalUnits = totalUnits,
            currentMultiplier = nextMultiplier,
            nextTotalStake = nextStake,
            coldBannedNumbers = nextColdSet.toList().sorted(),
            netProfit = netProfit,
            winRatePercent = winRate,
            maxDrawdown = maxDD,
            endingBankroll = bankroll,
            stabilityScore = stabilityScore,
            equityPoints = equityPoints,
            spinLogs = logs.reversed(), // newest first for display
            numberWeights = nextFreqs,
            rawMultiplier = nextRawMultiplier,
            maxStake = if (maxStakeReached > 0.0) maxStakeReached else nextStake,
            quality = quality,
            startMode = startMode
        )
    }

    /**
     * Evaluates equity trajectory quality:
     * 1. Linearity (R^2 & positive slope): Smoothness of upward climb
     * 2. Underwater Durations: Overall & Recent 50% spins spent below starting capital
     * 3. Ulcer Index: Quadratic penalty for drawdown depth x duration
     */
    fun computeTrendQuality(
        equityPoints: List<EquityPoint>,
        startBankroll: Double
    ): TrendQualityMetrics {
        val n = equityPoints.size
        if (n < 2) {
            return TrendQualityMetrics(
                score = 50,
                grade = "C",
                summary = "Insufficient Spins"
            )
        }

        val y = equityPoints.map { it.bankroll }

        // 1. Linear Regression (Slope m and R^2)
        val xMean = (n + 1) / 2.0
        val yMean = y.sum() / n

        var sxx = 0.0
        var sxy = 0.0
        var ssTot = 0.0

        for (i in 0 until n) {
            val xi = (i + 1).toDouble()
            val yi = y[i]
            val xDiff = xi - xMean
            val yDiff = yi - yMean
            sxx += xDiff * xDiff
            sxy += xDiff * yDiff
            ssTot += yDiff * yDiff
        }

        val slope = if (sxx > 0.0) sxy / sxx else 0.0
        var ssRes = 0.0
        for (i in 0 until n) {
            val xi = (i + 1).toDouble()
            val yPred = yMean + slope * (xi - xMean)
            val diff = y[i] - yPred
            ssRes += diff * diff
        }

        val rSquared = if (ssTot > 1e-6) {
            max(0.0, 1.0 - (ssRes / ssTot))
        } else 0.0

        // 2. Underwater Durations
        var totalUnderwaterCount = 0
        for (b in y) {
            if (b < startBankroll) totalUnderwaterCount++
        }
        val totalUnderwaterPct = (totalUnderwaterCount.toDouble() / n) * 100.0

        val recentN = max(1, n / 2)
        val recentSlice = y.takeLast(recentN)
        var recentUnderwaterCount = 0
        for (b in recentSlice) {
            if (b < startBankroll) recentUnderwaterCount++
        }
        val recentUnderwaterPct = (recentUnderwaterCount.toDouble() / recentN) * 100.0

        // 3. Ulcer Index (Quadratic Mean of Percentage Drawdowns)
        var runningPeak = startBankroll
        var sumSquaredPctDD = 0.0
        for (b in y) {
            if (b > runningPeak) runningPeak = b
            val pctDD = if (runningPeak > 0.0) {
                max(0.0, (runningPeak - b) / runningPeak) * 100.0
            } else 0.0
            sumSquaredPctDD += pctDD * pctDD
        }
        val ulcerIndex = sqrt(sumSquaredPctDD / n)

        // 4. Composite Quality Score (0 to 100)
        // A. Upward Linearity (0 - 40 pts): High R^2 only rewarded if trend slope is positive
        val slopeFactor = if (slope > 0.0) min(1.0, max(0.15, slope / 1.0)) else 0.0
        val linearityPts = 40.0 * rSquared * slopeFactor

        // B. Above-Water Stability (0 - 35 pts): Heavy penalty for recent underwater drift (20 pts), secondary penalty for overall (15 pts)
        val recentAboveWaterFactor = max(0.0, 1.0 - (recentUnderwaterPct / 100.0))
        val totalAboveWaterFactor = max(0.0, 1.0 - (totalUnderwaterPct / 100.0))
        val aboveWaterPts = (20.0 * recentAboveWaterFactor) + (15.0 * totalAboveWaterFactor)

        // C. Drawdown Severity & Duration (0 - 25 pts)
        val ulcerFactor = max(0.0, 1.0 - (ulcerIndex / 12.0))
        val drawdownPts = 25.0 * ulcerFactor

        val rawScore = (linearityPts + aboveWaterPts + drawdownPts).roundToInt().coerceIn(0, 100)

        // 5. Grade & Narrative Summary
        val grade = when {
            rawScore >= 85 -> "A+"
            rawScore >= 75 -> "A"
            rawScore >= 60 -> "B"
            rawScore >= 45 -> "C"
            rawScore >= 30 -> "D"
            else -> "F"
        }

        val summary = when {
            rawScore >= 80 -> "Smooth Uptrend"
            rawScore >= 65 -> "Steady Growth"
            recentUnderwaterPct > 50.0 -> "Underwater Drift"
            ulcerIndex > 8.0 -> "High Volatility"
            rawScore >= 45 -> "Moderate Volatility"
            else -> "Deep Drawdown"
        }

        return TrendQualityMetrics(
            score = rawScore,
            rSquared = rSquared,
            slope = slope,
            totalUnderwaterPercent = totalUnderwaterPct,
            recentUnderwaterPercent = recentUnderwaterPct,
            ulcerIndex = ulcerIndex,
            grade = grade,
            summary = summary
        )
    }

    data class FastSimMetrics(
        val netProfit: Double,
        val winRatePercent: Double,
        val maxDrawdown: Double,
        val stabilityScore: Double,
        val recentUnderwaterPercent: Double,
        val betsPlacedCount: Int = 0
    )

    fun fastEvaluateRounds(
        prog: ProgressionType,
        rounds: List<PrecomputedRound>,
        startBankroll: Double,
        baseInsideBet: Double,
        baseOutsideBet: Double,
        riskCap: Boolean,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1,
        lookbackWindow: Int = 37,
        recencyDecay: Boolean = false
    ): FastSimMetrics {
        val isAfterLookback = startMode == BettingStartMode.AFTER_LOOKBACK
        val tracker = ProgressionTracker(prog)
        var bankroll = startBankroll
        var peak = startBankroll
        var maxDD = 0.0
        var winCount = 0
        var sumReturn = 0.0
        var sumDownsideSq = 0.0
        var weightSum = 0.0
        var weightedProfit = 0.0
        val n = rounds.size
        var underwaterCount = 0
        var activeRoundCount = 0
        var betsPlacedCount = 0
        val recentWindowStart = max(0, n - 20)

        for (idx in 0 until n) {
            val round = rounds[idx]
            if (isAfterLookback && round.spinIndex <= lookbackWindow) {
                continue
            }
            activeRoundCount++
            if (round.bets.isNotEmpty()) {
                betsPlacedCount++
            }
            val rawMult = tracker.getMultiplier()
            val mult = computeEffectiveMultiplierFast(
                rawMultiplier = rawMult,
                baseStake = round.baseStake,
                minNetWinPerUnit = round.minNetWinPerUnit,
                bankroll = bankroll,
                peak = peak,
                progressionType = prog,
                riskCapEnabled = riskCap
            )

            var totalStake = 0.0
            var grossWin = 0.0

            val roundSpinMask = round.spinMask
            for (b in round.bets) {
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                val betCost = b.baseUnits * baseBet * mult
                totalStake += betCost
                if ((roundSpinMask and b.mask) != 0L) {
                    grossWin += betCost * (b.payoutRatio + 1)
                }
            }

            val netGain = grossWin - totalStake
            bankroll += netGain
            if (bankroll > peak) peak = bankroll
            val dd = peak - bankroll
            if (dd > maxDD) maxDD = dd

            val won = netGain > 0.0
            val isPush = netGain == 0.0
            if (won) winCount++

            tracker.record(won = won, netGainedUnits = netGain / max(1.0, totalStake), isPush = isPush)

            val decayW = if (recencyDecay) exp(-((n - 1 - idx).toDouble() / 16.0)) else 1.0
            weightSum += decayW
            weightedProfit += netGain * decayW

            val r = if (totalStake > 0.0) netGain / totalStake else 0.0
            sumReturn += r * decayW
            if (r < 0.0) sumDownsideSq += (r * r) * decayW

            if (idx >= recentWindowStart && bankroll < startBankroll) {
                underwaterCount++
            }
        }

        val normDenom = if (recencyDecay && weightSum > 0.0) weightSum else activeRoundCount.toDouble()
        val avgReturn = if (normDenom > 0.0) sumReturn / normDenom else 0.0
        val downsideSemiVar = if (normDenom > 0.0) sqrt(sumDownsideSq / normDenom) else 1.0
        val ddPenalty = 1.0 - (maxDD / (peak + 10.0)).coerceIn(0.0, 0.95)
        val stabilityScore = if (activeRoundCount > 0) (avgReturn / (downsideSemiVar + 1.0)) * ddPenalty else 0.0
        val winRate = if (activeRoundCount > 0) (winCount.toDouble() / activeRoundCount) * 100.0 else 0.0
        val recentWindowSize = max(1, n - recentWindowStart)
        val recentUnderwaterPercent = (underwaterCount.toDouble() / recentWindowSize) * 100.0
        val effectiveProfit = if (recencyDecay && activeRoundCount > 0 && weightSum > 0.0) {
            (weightedProfit / weightSum) * activeRoundCount
        } else {
            bankroll - startBankroll
        }

        return FastSimMetrics(
            netProfit = effectiveProfit,
            winRatePercent = winRate,
            maxDrawdown = maxDD,
            stabilityScore = stabilityScore,
            recentUnderwaterPercent = recentUnderwaterPercent,
            betsPlacedCount = betsPlacedCount
        )
    }

    /**
     * Scores all candidate progressions on a given list of precomputed rounds using ultra-fast metrics.
     */
    fun scoreProgressionsOnRounds(
        candidateProgressions: List<ProgressionType>,
        rounds: List<PrecomputedRound>,
        spins: List<Int>,
        isUS: Boolean,
        family: BetFamily,
        coldLimit: Int,
        lookbackWindow: Int,
        startBankroll: Double,
        baseInsideBet: Double,
        baseOutsideBet: Double,
        coldRule: ColdRule,
        equalizePayout: Boolean,
        pushOrProfit: Boolean,
        riskCap: Boolean,
        primaryCriterion: OptimizationCriterion,
        secondaryCriterion: OptimizationCriterion,
        underwaterFilter: Boolean,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1,
        recencyDecay: Boolean = false
    ): Map<ProgressionType, Double> {
        if (rounds.isEmpty()) return emptyMap()

        val evaluatedSims = candidateProgressions.map { prog ->
            val metrics = fastEvaluateRounds(
                prog = prog,
                rounds = rounds,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                riskCap = riskCap,
                startMode = startMode,
                lookbackWindow = lookbackWindow,
                recencyDecay = recencyDecay
            )
            Pair(prog, metrics)
        }

        val minPnl = evaluatedSims.minOfOrNull { it.second.netProfit } ?: 0.0
        val maxPnl = evaluatedSims.maxOfOrNull { it.second.netProfit } ?: 0.0
        val minHit = evaluatedSims.minOfOrNull { it.second.winRatePercent } ?: 0.0
        val maxHit = evaluatedSims.maxOfOrNull { it.second.winRatePercent } ?: 0.0
        val minDD = evaluatedSims.minOfOrNull { it.second.maxDrawdown } ?: 0.0
        val maxDD = evaluatedSims.maxOfOrNull { it.second.maxDrawdown } ?: 0.0

        fun scoreCriterion(crit: OptimizationCriterion, m: FastSimMetrics): Double {
            return when (crit) {
                OptimizationCriterion.PNL -> {
                    if (maxPnl > minPnl) (m.netProfit - minPnl) / (maxPnl - minPnl) else 1.0
                }
                OptimizationCriterion.HIT_RATE -> {
                    if (maxHit > minHit) (m.winRatePercent - minHit) / (maxHit - minHit) else 1.0
                }
                OptimizationCriterion.MAX_DD -> {
                    if (maxDD > minDD) 1.0 - ((m.maxDrawdown - minDD) / (maxDD - minDD)) else 1.0
                }
                OptimizationCriterion.QUALITY -> {
                    val pnlScore = if (maxPnl > minPnl) (m.netProfit - minPnl) / (maxPnl - minPnl) else 1.0
                    pnlScore * (1.0 - (m.recentUnderwaterPercent / 100.0).coerceIn(0.0, 0.7))
                }
            }
        }

        return evaluatedSims.associate { (prog, m) ->
            val scorePrimary = scoreCriterion(primaryCriterion, m)
            val scoreSecondary = scoreCriterion(secondaryCriterion, m)
            val underwaterPenalty = if (underwaterFilter && m.recentUnderwaterPercent > 30.0) {
                (1.0 - (m.recentUnderwaterPercent - 30.0) / 70.0).coerceIn(0.05, 1.0)
            } else 1.0
            val combined = ((0.85 * scorePrimary) + (0.15 * scoreSecondary) + (0.0001 * m.stabilityScore)) * underwaterPenalty
            prog to combined
        }
    }

    /**
     * Auto-optimizes progression system across the full progression suite using realistic
     * Walk-Forward adaptive simulation, while benchmarking against the Ideal Single Strategy.
     */
    fun optimize(
        spins: List<Int>,
        isUS: Boolean,
        targetProgression: ProgressionType = ProgressionType.AUTO,
        family: BetFamily = BetFamily.COMBINED,
        coldLimit: Int = 5,
        lookbackWindow: Int = 37,
        startBankroll: Double = 1000.0,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0,
        primaryCriterion: OptimizationCriterion = OptimizationCriterion.PNL,
        secondaryCriterion: OptimizationCriterion = OptimizationCriterion.MAX_DD,
        coldRule: ColdRule = ColdRule.STRICT,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        riskCap: Boolean = true,
        underwaterFilter: Boolean = true,
        transitionRule: SwitchTransitionRule = SwitchTransitionRule.COMPLETE_CYCLE,
        switchSensitivity: SwitchSensitivity = SwitchSensitivity.MODERATE,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1,
        precomputedRounds: List<PrecomputedRound>? = null,
        skipIdealBenchmark: Boolean = false
    ): TrendRecommendation {
        val effectiveSpins = spins

        // Precompute rounds once across all progression tests (50x performance speedup)
        val precomputed = precomputedRounds ?: precomputeRounds(
            spins = effectiveSpins,
            isUS = isUS,
            family = family,
            coldLimit = coldLimit,
            lookbackWindow = lookbackWindow,
            coldRule = coldRule,
            equalizePayout = equalizePayout,
            pushOrProfit = pushOrProfit,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progression = if (targetProgression.isFixedLayout) targetProgression else ProgressionType.AUTO
        )

        val candidateProgressions = ProgressionType.values().filter { it != ProgressionType.AUTO }

        // 1. Calculate the IDEAL Benchmark (Best single progression run from Spin 1 to all spins regardless of start mode)
        val (idealBestProgression, idealEquityPoints, idealSim) = if (skipIdealBenchmark) {
            Triple(ProgressionType.FLAT, emptyList<EquityPoint>(), null)
        } else {
            val globalScores = scoreProgressionsOnRounds(
                candidateProgressions = candidateProgressions,
                rounds = precomputed,
                spins = effectiveSpins,
                isUS = isUS,
                family = family,
                coldLimit = coldLimit,
                lookbackWindow = lookbackWindow,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                coldRule = coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                riskCap = riskCap,
                primaryCriterion = primaryCriterion,
                secondaryCriterion = secondaryCriterion,
                underwaterFilter = underwaterFilter,
                startMode = BettingStartMode.FROM_SPIN_1
            )
            val bestProg = globalScores.maxByOrNull { it.value }?.key ?: ProgressionType.FLAT
            val sim = evaluateSimulation(
                spins = effectiveSpins,
                isUS = isUS,
                progression = bestProg,
                family = family,
                coldLimit = coldLimit,
                lookbackWindow = lookbackWindow,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                precomputedRounds = precomputed,
                coldRule = coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                riskCap = riskCap,
                startMode = BettingStartMode.FROM_SPIN_1
            ).copy(bestProgression = bestProg)
            Triple(bestProg, sim.equityPoints, sim)
        }

        if (targetProgression != ProgressionType.AUTO) {
            val sim = evaluateSimulation(
                spins = effectiveSpins,
                isUS = isUS,
                progression = targetProgression,
                family = family,
                coldLimit = coldLimit,
                lookbackWindow = lookbackWindow,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                precomputedRounds = precomputed,
                coldRule = coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                riskCap = riskCap,
                startMode = startMode
            )
            return sim.copy(idealEquityPoints = idealEquityPoints, idealProgression = idealBestProgression)
        }

        if (precomputed.isEmpty()) {
            val fallbackSim = idealSim ?: evaluateSimulation(
                spins = effectiveSpins,
                isUS = isUS,
                progression = targetProgression,
                family = family,
                coldLimit = coldLimit,
                lookbackWindow = lookbackWindow,
                startBankroll = startBankroll,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet,
                coldRule = coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                riskCap = riskCap,
                startMode = startMode
            )
            return fallbackSim.copy(idealEquityPoints = idealEquityPoints, idealProgression = idealBestProgression)
        }

        // 2. Real-Time Walk-Forward Adaptive Simulation
        var currentProgression = ProgressionType.FLAT
        var currentTracker = ProgressionTracker(currentProgression)
        var pendingSwitchTo: ProgressionType? = null

        var bankroll = startBankroll
        var peak = startBankroll
        var maxDD = 0.0
        var winCount = 0
        var maxStakeReached = 0.0

        val logs = mutableListOf<SpinLedgerRecord>()
        val equityPoints = mutableListOf<EquityPoint>()
        val roundReturns = mutableListOf<Double>()
        val isAfterLookback = startMode == BettingStartMode.AFTER_LOOKBACK

        for (i in precomputed.indices) {
            val round = precomputed[i]
            val isObservational = isAfterLookback && round.spinIndex <= lookbackWindow

            if (isObservational) {
                val record = SpinLedgerRecord(
                    spinIndex = round.spinIndex,
                    spunNumber = round.spinNumber,
                    activeBetsSummary = "Lookback Seed (Observational)",
                    coldAvoided = round.coldStr,
                    progressionUsed = currentProgression.displayName,
                    stake = 0.0,
                    multiplier = 0,
                    netGain = 0.0,
                    outcome = "SEED",
                    bankroll = bankroll,
                    placedBets = emptyList(),
                    rawMultiplier = 0,
                    switchOccurred = false,
                    switchNote = "Lookback Observation (${round.spinIndex}/$lookbackWindow)",
                    isCyclePending = false
                )
                logs.add(record)
                equityPoints.add(
                    EquityPoint(
                        spinIndex = round.spinIndex,
                        spunNumber = round.spinNumber,
                        bankroll = bankroll,
                        netGain = 0.0,
                        record = record
                    )
                )
                continue
            }

            var switchOccurredThisSpin = false
            var switchNoteThisSpin = ""
            var isCyclePendingThisSpin = false

            if (i > 0) {
                val windowStart = max(0, i - lookbackWindow)
                val windowRounds = precomputed.subList(windowStart, i)

                val scores = scoreProgressionsOnRounds(
                    candidateProgressions = candidateProgressions,
                    rounds = windowRounds,
                    spins = effectiveSpins,
                    isUS = isUS,
                    family = family,
                    coldLimit = coldLimit,
                    lookbackWindow = lookbackWindow,
                    startBankroll = startBankroll,
                    baseInsideBet = baseInsideBet,
                    baseOutsideBet = baseOutsideBet,
                    coldRule = coldRule,
                    equalizePayout = equalizePayout,
                    pushOrProfit = pushOrProfit,
                    riskCap = riskCap,
                    primaryCriterion = primaryCriterion,
                    secondaryCriterion = secondaryCriterion,
                    underwaterFilter = underwaterFilter,
                    startMode = BettingStartMode.FROM_SPIN_1,
                    recencyDecay = true
                )

                val bestCandidate = scores.maxByOrNull { it.value }?.key ?: currentProgression
                val bestCandidateScore = scores[bestCandidate] ?: 0.0
                val currentScore = scores[currentProgression] ?: 0.0

                val isFirstActiveSpinAfterLookback = isAfterLookback && round.spinIndex == lookbackWindow + 1

                if (isFirstActiveSpinAfterLookback) {
                    currentProgression = bestCandidate
                    currentTracker = ProgressionTracker(currentProgression)
                    pendingSwitchTo = null
                    switchOccurredThisSpin = true
                    switchNoteThisSpin = "Optimal strategy based on lookback: ${bestCandidate.displayName}"
                } else {
                    val margin = switchSensitivity.leadMargin
                    val qualifies = bestCandidate != currentProgression && (bestCandidateScore > currentScore * (1.0 + margin))

                    if (transitionRule == SwitchTransitionRule.IMMEDIATE_RESET) {
                        if (qualifies) {
                            val oldProg = currentProgression
                            currentProgression = bestCandidate
                            currentTracker = ProgressionTracker(currentProgression)
                            pendingSwitchTo = null
                            switchOccurredThisSpin = true
                            switchNoteThisSpin = "Switched to ${bestCandidate.displayName}"
                        }
                    } else {
                        // SwitchTransitionRule.COMPLETE_CYCLE
                        val cycleComplete = currentTracker.isCycleComplete()
                        if (cycleComplete) {
                            if (pendingSwitchTo != null) {
                                // Previous pending switch: cycle has completed, re-verify!
                                if (qualifies) {
                                    val oldProg = currentProgression
                                    currentProgression = bestCandidate
                                    currentTracker = ProgressionTracker(currentProgression)
                                    pendingSwitchTo = null
                                    switchOccurredThisSpin = true
                                    switchNoteThisSpin = "Cycle completed. Switched to ${bestCandidate.displayName}"
                                } else {
                                    // Switch canceled, lead recovered!
                                    val kept = currentProgression
                                    pendingSwitchTo = null
                                    switchNoteThisSpin = "Cycle completed. Retained ${kept.displayName}"
                                }
                            } else if (qualifies) {
                                val oldProg = currentProgression
                                currentProgression = bestCandidate
                                currentTracker = ProgressionTracker(currentProgression)
                                pendingSwitchTo = null
                                switchOccurredThisSpin = true
                                switchNoteThisSpin = "Switched to ${bestCandidate.displayName}"
                            }
                        } else {
                            // Cycle still active
                            if (qualifies) {
                                pendingSwitchTo = bestCandidate
                                isCyclePendingThisSpin = true
                                switchNoteThisSpin = "Holding ${currentProgression.displayName} (Finishing cycle)"
                            } else if (pendingSwitchTo != null) {
                                isCyclePendingThisSpin = true
                                switchNoteThisSpin = "Holding ${currentProgression.displayName} (Finishing cycle)"
                            }
                        }
                    }
                }
            }

            val rawMult = currentTracker.getMultiplier()
            val mult = computeEffectiveMultiplierFast(
                rawMultiplier = rawMult,
                baseStake = round.baseStake,
                minNetWinPerUnit = round.minNetWinPerUnit,
                bankroll = bankroll,
                peak = peak,
                progressionType = currentProgression,
                riskCapEnabled = riskCap
            )

            var totalStake = 0.0
            var grossWin = 0.0
            round.bets.forEach { b ->
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                val betCost = b.baseUnits * baseBet * mult
                totalStake += betCost
                if ((b.mask and round.spinMask) != 0L) {
                    grossWin += betCost * (b.payoutRatio + 1)
                }
            }

            if (totalStake > maxStakeReached) {
                maxStakeReached = totalStake
            }

            val netGain = grossWin - totalStake
            bankroll += netGain
            roundReturns.add(netGain)

            val isWin = netGain > 0
            val isPush = netGain == 0.0
            val outcome = if (isWin) "WIN" else if (isPush) "PUSH" else "LOSS"

            if (isWin) winCount++
            if (bankroll > peak) peak = bankroll
            val dd = peak - bankroll
            if (dd > maxDD) maxDD = dd

            currentTracker.record(won = isWin, netGainedUnits = netGain / max(1.0, totalStake), isPush = isPush)

            val record = SpinLedgerRecord(
                spinIndex = round.spinIndex,
                spunNumber = round.spinNumber,
                activeBetsSummary = round.betsDesc,
                coldAvoided = round.coldStr,
                progressionUsed = currentProgression.displayName,
                stake = totalStake,
                multiplier = mult,
                netGain = netGain,
                outcome = outcome,
                bankroll = bankroll,
                placedBets = round.bets,
                rawMultiplier = rawMult,
                switchOccurred = switchOccurredThisSpin,
                switchNote = switchNoteThisSpin,
                isCyclePending = isCyclePendingThisSpin
            )
            logs.add(record)
            equityPoints.add(
                EquityPoint(
                    spinIndex = round.spinIndex,
                    spunNumber = round.spinNumber,
                    bankroll = bankroll,
                    netGain = netGain,
                    record = record
                )
            )
        }

        // Recommend next round bets using the current walk-forward state
        val candidates = getCandidates(isUS)
        val nextColdSet = getColdBannedNumbers(effectiveSpins, coldLimit, isUS)
        val nextLookbackSlice = if (effectiveSpins.isNotEmpty()) {
            effectiveSpins.takeLast(min(lookbackWindow, effectiveSpins.size))
        } else {
            emptyList()
        }
        val nextFreqs = computeFrequencies(nextLookbackSlice, isUS)
        val nextSelectedBets = selectOptimalBets(
            candidates = candidates,
            freqs = nextFreqs,
            coldBanned = nextColdSet,
            family = family,
            coldRule = coldRule,
            equalizePayout = equalizePayout,
            pushOrProfit = pushOrProfit,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progression = currentProgression,
            recentSpins = nextLookbackSlice,
            isUS = isUS
        )

        val isWaitingForLookback = isAfterLookback && effectiveSpins.size < lookbackWindow

        val nextRawMult = if (isWaitingForLookback) 0 else currentTracker.getMultiplier()
        val nextMult = if (isWaitingForLookback) 0 else computeEffectiveMultiplier(
            rawMultiplier = nextRawMult,
            bets = nextSelectedBets,
            bankroll = bankroll,
            peak = peak,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            progressionType = currentProgression,
            riskCapEnabled = riskCap
        )

        val recommendedBetsWithStake = if (isWaitingForLookback) {
            emptyList()
        } else {
            nextSelectedBets.map { b ->
                val baseBet = if (b.isInside) baseInsideBet else baseOutsideBet
                RecommendedBet(
                    bet = b,
                    units = b.baseUnits * nextMult,
                    dollarAmount = b.baseUnits * baseBet * nextMult
                )
            }
        }

        val totalUnits = recommendedBetsWithStake.sumOf { it.units }
        val nextTotalStake = recommendedBetsWithStake.sumOf { it.dollarAmount }

        val n = roundReturns.size
        val avgReturn = if (n > 0) roundReturns.sum() / n else 0.0
        val downsideSemiVar = if (n > 0) {
            sqrt(roundReturns.sumOf { r -> if (r < 0) r.pow(2) else 0.0 } / n)
        } else 1.0

        val netProfit = bankroll - startBankroll
        val ddPenalty = 1.0 - (maxDD / (peak + 10.0)).coerceIn(0.0, 0.95)
        val stabilityScore = if (n > 0) ((avgReturn / (downsideSemiVar + 1.0)) * ddPenalty) else 0.0

        val activeRoundsCount = if (isAfterLookback) max(0, precomputed.size - lookbackWindow) else precomputed.size
        val winRate = if (activeRoundsCount > 0) (winCount.toDouble() / activeRoundsCount) * 100.0 else 0.0
        val trendQuality = computeTrendQuality(equityPoints, startBankroll)

        return TrendRecommendation(
            bestProgression = idealBestProgression,
            recommendedBets = recommendedBetsWithStake,
            totalUnits = totalUnits,
            currentMultiplier = nextMult,
            nextTotalStake = nextTotalStake,
            coldBannedNumbers = nextColdSet.toList().sorted(),
            netProfit = netProfit,
            winRatePercent = winRate,
            maxDrawdown = maxDD,
            endingBankroll = bankroll,
            stabilityScore = stabilityScore,
            equityPoints = equityPoints,
            spinLogs = logs.reversed(),
            numberWeights = nextFreqs,
            rawMultiplier = nextRawMult,
            maxStake = if (maxStakeReached > 0.0) maxStakeReached else nextTotalStake,
            quality = trendQuality,
            idealEquityPoints = idealEquityPoints,
            idealProgression = idealBestProgression,
            startMode = startMode,
            activeProgression = currentProgression
        )
    }

    /**
     * Determines the optimal bet family and cold limit combination
     * for the current spin history, evaluated according to the user's prioritized criteria.
     */
    fun findOptimalConfig(
        spins: List<Int>,
        isUS: Boolean,
        progression: ProgressionType = ProgressionType.AUTO,
        lookbackWindow: Int = 37,
        startBankroll: Double = 1000.0,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0,
        primaryCriterion: OptimizationCriterion = OptimizationCriterion.PNL,
        secondaryCriterion: OptimizationCriterion = OptimizationCriterion.MAX_DD,
        coldRule: ColdRule = ColdRule.STRICT,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        riskCap: Boolean = true,
        underwaterFilter: Boolean = true,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1,
        targetColdLimit: Int? = null
    ): Pair<BetFamily, Int> {
        if (spins.isEmpty()) return Pair(BetFamily.COMBINED, targetColdLimit ?: 5)

        val families = listOf(BetFamily.COMBINED, BetFamily.INSIDE, BetFamily.OUTSIDE)
        val coldLimits = if (targetColdLimit != null) listOf(targetColdLimit) else listOf(0, 3, 5, 7)

        val candidateResults = mutableListOf<Triple<BetFamily, Int, TrendRecommendation>>()

        val evalProgression = if (progression == ProgressionType.AUTO) ProgressionType.FLAT else progression
        for (fam in families) {
            for (cold in coldLimits) {
                val res = evaluateSimulation(
                    spins = spins,
                    isUS = isUS,
                    progression = evalProgression,
                    family = fam,
                    coldLimit = cold,
                    lookbackWindow = lookbackWindow,
                    startBankroll = startBankroll,
                    baseInsideBet = baseInsideBet,
                    baseOutsideBet = baseOutsideBet,
                    coldRule = coldRule,
                    equalizePayout = equalizePayout,
                    pushOrProfit = pushOrProfit,
                    riskCap = riskCap,
                    startMode = startMode
                )
                candidateResults.add(Triple(fam, cold, res))
            }
        }

        val minPnl = candidateResults.minOfOrNull { it.third.netProfit } ?: 0.0
        val maxPnl = candidateResults.maxOfOrNull { it.third.netProfit } ?: 0.0
        val minHit = candidateResults.minOfOrNull { it.third.winRatePercent } ?: 0.0
        val maxHit = candidateResults.maxOfOrNull { it.third.winRatePercent } ?: 0.0
        val minDD = candidateResults.minOfOrNull { it.third.maxDrawdown } ?: 0.0
        val maxDD = candidateResults.maxOfOrNull { it.third.maxDrawdown } ?: 0.0

        fun scoreCriterion(crit: OptimizationCriterion, sim: TrendRecommendation): Double {
            return when (crit) {
                OptimizationCriterion.PNL -> {
                    if (maxPnl > minPnl) (sim.netProfit - minPnl) / (maxPnl - minPnl) else 1.0
                }
                OptimizationCriterion.HIT_RATE -> {
                    if (maxHit > minHit) (sim.winRatePercent - minHit) / (maxHit - minHit) else 1.0
                }
                OptimizationCriterion.MAX_DD -> {
                    if (maxDD > minDD) 1.0 - ((sim.maxDrawdown - minDD) / (maxDD - minDD)) else 1.0
                }
                OptimizationCriterion.QUALITY -> {
                    (sim.quality.score / 100.0).coerceIn(0.0, 1.0)
                }
            }
        }

        var bestFam = BetFamily.COMBINED
        var bestCold = 5
        var bestScore = -Double.MAX_VALUE

        for ((fam, cold, sim) in candidateResults) {
            val scorePrimary = scoreCriterion(primaryCriterion, sim)
            val scoreSecondary = scoreCriterion(secondaryCriterion, sim)

            val underwaterPenalty = if (underwaterFilter && sim.quality.recentUnderwaterPercent > 30.0) {
                (1.0 - (sim.quality.recentUnderwaterPercent - 30.0) / 70.0).coerceIn(0.05, 1.0)
            } else 1.0

            val combined = ((0.85 * scorePrimary) + (0.15 * scoreSecondary) + (0.0001 * sim.stabilityScore)) * underwaterPenalty

            if (combined > bestScore) {
                bestScore = combined
                bestFam = fam
                bestCold = cold
            }
        }

        return Pair(bestFam, bestCold)
    }

    data class FamilyOptimalColdConfig(
        val family: BetFamily,
        val optimalColdLimit: Int,
        val optimalColdRule: ColdRule,
        val netProfit: Double,
        val maxDrawdown: Double,
        val winRatePercent: Double,
        val qualityScore: Int,
        val qualityGrade: String,
        val stabilityScore: Double,
        val isGlobalBest: Boolean = false
    )

    data class GlobalOptimalConfig(
        val progression: ProgressionType,
        val family: BetFamily,
        val coldLimit: Int,
        val coldRule: ColdRule,
        val netProfit: Double,
        val maxDrawdown: Double,
        val winRatePercent: Double,
        val qualityScore: Int,
        val qualityGrade: String,
        val endingBankroll: Double,
        val idealEquityPoints: List<EquityPoint>,
        val familyOptimalColdConfigs: List<FamilyOptimalColdConfig> = emptyList()
    ) {
        val summaryText: String
            get() {
                val famText = when (family) {
                    BetFamily.INSIDE -> "Inside"
                    BetFamily.OUTSIDE -> "Outside"
                    BetFamily.COMBINED -> "Combined"
                }
                val coldText = if (coldLimit == 0) "Cold: Off" else "Cold: $coldLimit (${coldRule.shortName})"
                return if (progression.isFixedLayout) {
                    "${progression.displayName} • Fixed Layout"
                } else {
                    "${progression.displayName} • $famText • $coldText"
                }
            }

        val conditionRowText: String
            get() {
                val famText = when (family) {
                    BetFamily.INSIDE -> "Inside"
                    BetFamily.OUTSIDE -> "Outside"
                    BetFamily.COMBINED -> "Combined"
                }
                val coldText = if (coldLimit == 0) "Cold: Off" else "Cold: $coldLimit (${coldRule.shortName})"
                return if (progression.isFixedLayout) "Fixed Layout" else "$famText • $coldText"
            }
    }

    /**
     * Finds the absolute "Best of the Best" configuration across all dimensions:
     * - All candidate progressions
     * - All bet families (Inside, Outside, Combined)
     * - All cold limits (0, 3, 5, 7)
     * - All cold avoidance rules (Strict, Soft Penalty, Fallback, Split)
     */
    fun findGlobalOptimalConfig(
        spins: List<Int>,
        isUS: Boolean,
        lookbackWindow: Int = 37,
        startBankroll: Double = 1000.0,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0,
        primaryCriterion: OptimizationCriterion = OptimizationCriterion.PNL,
        secondaryCriterion: OptimizationCriterion = OptimizationCriterion.MAX_DD,
        equalizePayout: Boolean = false,
        pushOrProfit: Boolean = false,
        riskCap: Boolean = true,
        underwaterFilter: Boolean = true,
        transitionRule: SwitchTransitionRule = SwitchTransitionRule.COMPLETE_CYCLE,
        switchSensitivity: SwitchSensitivity = SwitchSensitivity.MODERATE,
        startMode: BettingStartMode = BettingStartMode.FROM_SPIN_1
    ): GlobalOptimalConfig? {
        if (spins.isEmpty()) return null

        val families = listOf(BetFamily.COMBINED, BetFamily.INSIDE, BetFamily.OUTSIDE)
        val coldLimits = listOf(0, 3, 5, 7)
        val coldRules = listOf(ColdRule.STRICT, ColdRule.PENALTY, ColdRule.WATERFALL, ColdRule.HYBRID)
        val candidateProgressions = ProgressionType.values().filter { it != ProgressionType.AUTO }

        data class ConfigKey(val family: BetFamily, val coldLimit: Int, val coldRule: ColdRule)

        val distinctConfigs = mutableListOf<ConfigKey>()
        for (fam in families) {
            for (cold in coldLimits) {
                if (cold == 0) {
                    distinctConfigs.add(ConfigKey(fam, 0, ColdRule.STRICT))
                } else {
                    for (rule in coldRules) {
                        distinctConfigs.add(ConfigKey(fam, cold, rule))
                    }
                }
            }
        }

        val precomputedMap = distinctConfigs.associateWith { cfg ->
            precomputeRounds(
                spins = spins,
                isUS = isUS,
                family = cfg.family,
                coldLimit = cfg.coldLimit,
                lookbackWindow = lookbackWindow,
                coldRule = cfg.coldRule,
                equalizePayout = equalizePayout,
                pushOrProfit = pushOrProfit,
                baseInsideBet = baseInsideBet,
                baseOutsideBet = baseOutsideBet
            )
        }

        data class EvaluatedCandidate(
            val prog: ProgressionType,
            val family: BetFamily,
            val coldLimit: Int,
            val coldRule: ColdRule,
            val metrics: FastSimMetrics
        )

        val allEvaluated = mutableListOf<EvaluatedCandidate>()

        for ((cfg, rounds) in precomputedMap) {
            for (prog in candidateProgressions) {
                if (prog.isFixedLayout && (cfg.family != BetFamily.COMBINED || cfg.coldLimit != 0)) {
                    continue
                }

                val m = fastEvaluateRounds(
                    prog = prog,
                    rounds = rounds,
                    startBankroll = startBankroll,
                    baseInsideBet = baseInsideBet,
                    baseOutsideBet = baseOutsideBet,
                    riskCap = riskCap,
                    startMode = startMode,
                    lookbackWindow = lookbackWindow
                )

                if (m.betsPlacedCount == 0 && spins.size > lookbackWindow) {
                    continue
                }
                allEvaluated.add(EvaluatedCandidate(prog, cfg.family, cfg.coldLimit, cfg.coldRule, m))
            }
        }

        if (allEvaluated.isEmpty()) return null

        val minPnl = allEvaluated.minOf { it.metrics.netProfit }
        val maxPnl = allEvaluated.maxOf { it.metrics.netProfit }
        val minHit = allEvaluated.minOf { it.metrics.winRatePercent }
        val maxHit = allEvaluated.maxOf { it.metrics.winRatePercent }
        val minDD = allEvaluated.minOf { it.metrics.maxDrawdown }
        val maxDD = allEvaluated.maxOf { it.metrics.maxDrawdown }

        fun scoreCriterion(crit: OptimizationCriterion, m: FastSimMetrics): Double {
            return when (crit) {
                OptimizationCriterion.PNL -> {
                    if (maxPnl > minPnl) (m.netProfit - minPnl) / (maxPnl - minPnl) else 1.0
                }
                OptimizationCriterion.HIT_RATE -> {
                    if (maxHit > minHit) (m.winRatePercent - minHit) / (maxHit - minHit) else 1.0
                }
                OptimizationCriterion.MAX_DD -> {
                    if (maxDD > minDD) 1.0 - ((m.maxDrawdown - minDD) / (maxDD - minDD)) else 1.0
                }
                OptimizationCriterion.QUALITY -> {
                    val pnlScore = if (maxPnl > minPnl) (m.netProfit - minPnl) / (maxPnl - minPnl) else 1.0
                    pnlScore * (1.0 - (m.recentUnderwaterPercent / 100.0).coerceIn(0.0, 0.7))
                }
            }
        }

        var bestCandidate: EvaluatedCandidate? = null
        var bestScore = -Double.MAX_VALUE

        for (cand in allEvaluated) {
            val scorePrimary = scoreCriterion(primaryCriterion, cand.metrics)
            val scoreSecondary = scoreCriterion(secondaryCriterion, cand.metrics)

            val underwaterPenalty = if (underwaterFilter && cand.metrics.recentUnderwaterPercent > 30.0) {
                (1.0 - (cand.metrics.recentUnderwaterPercent - 30.0) / 70.0).coerceIn(0.05, 1.0)
            } else 1.0

            val activityPenalty = if (spins.size > 15 && cand.metrics.betsPlacedCount < (spins.size * 0.10)) 0.4 else 1.0

            val combined = ((0.85 * scorePrimary) + (0.15 * scoreSecondary) + (0.0001 * cand.metrics.stabilityScore)) * underwaterPenalty * activityPenalty

            if (combined > bestScore) {
                bestScore = combined
                bestCandidate = cand
            }
        }

        val best = bestCandidate ?: return null

        val fullBestSim = evaluateSimulation(
            spins = spins,
            isUS = isUS,
            progression = best.prog,
            family = best.family,
            coldLimit = best.coldLimit,
            lookbackWindow = lookbackWindow,
            startBankroll = startBankroll,
            baseInsideBet = baseInsideBet,
            baseOutsideBet = baseOutsideBet,
            coldRule = best.coldRule,
            equalizePayout = equalizePayout,
            pushOrProfit = pushOrProfit,
            riskCap = riskCap,
            startMode = BettingStartMode.FROM_SPIN_1
        )

        // Evaluate the optimal cold ban configuration (limit + rule) for each family under Walk-Forward Adaptive Simulation
        val familyOptimalColdConfigs = families.mapNotNull { fam ->
            val famConfigs = distinctConfigs.filter { it.family == fam }
            var bestFamSim: TrendRecommendation? = null
            var bestFamCfg: ConfigKey? = null
            var bestFamScore = -Double.MAX_VALUE

            for (cfg in famConfigs) {
                val rounds = precomputedMap[cfg] ?: continue
                val sim = optimize(
                    spins = spins,
                    isUS = isUS,
                    targetProgression = ProgressionType.AUTO,
                    family = fam,
                    coldLimit = cfg.coldLimit,
                    lookbackWindow = lookbackWindow,
                    startBankroll = startBankroll,
                    baseInsideBet = baseInsideBet,
                    baseOutsideBet = baseOutsideBet,
                    primaryCriterion = primaryCriterion,
                    secondaryCriterion = secondaryCriterion,
                    coldRule = cfg.coldRule,
                    equalizePayout = equalizePayout,
                    pushOrProfit = pushOrProfit,
                    riskCap = riskCap,
                    underwaterFilter = underwaterFilter,
                    transitionRule = transitionRule,
                    switchSensitivity = switchSensitivity,
                    startMode = startMode,
                    precomputedRounds = rounds,
                    skipIdealBenchmark = true
                )

                // Prioritize optimal cold count & rule yielding the highest rated graph per family
                val famScore = (sim.quality.score * 10000.0) + (sim.stabilityScore * 100.0) + (sim.netProfit * 10.0) - sim.maxDrawdown

                if (famScore > bestFamScore) {
                    bestFamScore = famScore
                    bestFamSim = sim
                    bestFamCfg = cfg
                }
            }

            if (bestFamSim != null && bestFamCfg != null) {
                FamilyOptimalColdConfig(
                    family = fam,
                    optimalColdLimit = bestFamCfg.coldLimit,
                    optimalColdRule = bestFamCfg.coldRule,
                    netProfit = bestFamSim.netProfit,
                    maxDrawdown = bestFamSim.maxDrawdown,
                    winRatePercent = bestFamSim.winRatePercent,
                    qualityScore = bestFamSim.quality.score,
                    qualityGrade = bestFamSim.quality.grade,
                    stabilityScore = bestFamSim.stabilityScore,
                    isGlobalBest = (!best.prog.isFixedLayout && best.family == fam)
                )
            } else null
        }

        return GlobalOptimalConfig(
            progression = best.prog,
            family = best.family,
            coldLimit = best.coldLimit,
            coldRule = best.coldRule,
            netProfit = fullBestSim.netProfit,
            maxDrawdown = fullBestSim.maxDrawdown,
            winRatePercent = fullBestSim.winRatePercent,
            qualityScore = fullBestSim.quality.score,
            qualityGrade = fullBestSim.quality.grade,
            endingBankroll = fullBestSim.endingBankroll,
            idealEquityPoints = fullBestSim.equityPoints,
            familyOptimalColdConfigs = familyOptimalColdConfigs
        )
    }

    /**
     * Backward-compatible alias for legacy calls.
     */
    fun findMostProfitableConfig(
        spins: List<Int>,
        isUS: Boolean,
        progression: ProgressionType = ProgressionType.AUTO,
        lookbackWindow: Int = 37,
        startBankroll: Double = 1000.0,
        baseInsideBet: Double = 1.0,
        baseOutsideBet: Double = 1.0
    ): Pair<BetFamily, Int> = findOptimalConfig(
        spins = spins,
        isUS = isUS,
        progression = progression,
        lookbackWindow = lookbackWindow,
        startBankroll = startBankroll,
        baseInsideBet = baseInsideBet,
        baseOutsideBet = baseOutsideBet,
        primaryCriterion = OptimizationCriterion.PNL,
        secondaryCriterion = OptimizationCriterion.MAX_DD
    )
}
