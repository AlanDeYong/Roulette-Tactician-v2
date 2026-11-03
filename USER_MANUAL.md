# Roulette Tactician v2 — Features Guide & User Manual

Welcome to **Roulette Tactician v2**, an advanced, professional-grade analytics workstation and betting decision support system designed specifically for European and American Roulette.

---

## Table of Contents
1. [Overview & Architecture](#1-overview--architecture)
2. [Wheel Formats & Table Felt Interface](#2-wheel-formats--table-felt-interface)
3. [Spin History & Data Input Modes](#3-spin-history--data-input-modes)
   - [On-Screen Draggable Keypads](#on-screen-draggable-keypads)
   - [Camera OCR & AI Spin Log Scanner](#camera-ocr--ai-spin-log-scanner)
   - [Clipboard & File Import / Export](#clipboard--file-import--export)
   - [Undo, Redo, Edit & Time Machine Historical Scrubber](#undo-redo-edit--time-machine-historical-scrubber)
4. [Heatmaps & Frequency Analytics](#4-heatmaps--frequency-analytics)
   - [Rolling Analysis Window](#rolling-analysis-window)
   - [Felt Heat Modes (Global Heat, Trend Weights, MA Trend)](#felt-heat-modes)
   - [Manual Hot & Cold Number Overrides](#manual-hot--cold-number-overrides)
5. [Moving Average (MA) Dashboard](#5-moving-average-ma-dashboard)
   - [Supported MA Indicators (SMA, EMA, WMA, ALMA, KAMA)](#supported-ma-indicators)
   - [Period (N) & Parameter Calibration](#period-n--parameter-calibration)
   - [Metric Groupings (All, Evens, Dozens & Columns)](#metric-groupings)
   - [Selector Persistence Across Sessions](#selector-persistence-across-sessions)
6. [Trigger Alerts Dashboard](#6-trigger-alerts-dashboard)
   - [Omission & Streak Tracking](#omission--streak-tracking)
   - [Customizable Alert Thresholds](#customizable-alert-thresholds)
7. [Bet Trend Optimization & Auto-Betting Engine](#7-bet-trend-optimization--auto-betting-engine)
   - [Bet Families & Candidate Generation](#bet-families--candidate-generation)
   - [Progression Types (Auto, Flat, Tiered, Martingale)](#progression-types)
   - [Risk Controls (Risk Cap / Deficit-Aware Cap, Push-or-Profit, Underwater Filter)](#risk-controls)
   - [Direct Felt Application](#direct-felt-application)
8. [Gameplay Recorder & Live Bankroll Equity Tracking](#8-gameplay-recorder--live-bankroll-equity-tracking)
   - [Live Sessions, Target Profits & Stop-Loss Limits](#live-sessions-target-profits--stop-loss-limits)
   - [Real-Time Equity Curve Chart](#real-time-equity-curve-chart)
   - [Spin-by-Spin Ledger Card](#spin-by-spin-ledger-card)
9. [Session Management, Cloud Backup & Disaster Recovery](#9-session-management-cloud-backup--disaster-recovery)
10. [Step-by-Step Practical Workflows](#10-step-by-step-practical-workflows)

---

## 1. Overview & Architecture

Roulette Tactician v2 combines casino game theory, statistical moving averages, and quantitative money management into a seamless, high-performance Android application:

- **Technology Stack**: Kotlin, Jetpack Compose, Kotlin Coroutines, StateFlow, Android Room SQLite Database (v9 Schema), and Hilt Dependency Injection.
- **Orientation**: Optimized for landscape tablets and phones, providing high data density with zero clutter.
- **Safety & Integrity**: Monotonically deterministic spin logging, isolated gameplay sessions, atomic database transactions, and automated background backup synchronization.

---

## 2. Wheel Formats & Table Felt Interface

### Table Formats
Toggle between wheel formats at any time using the header toggle:
- **European Table (Single Zero `0`)**: 37 numbered pockets (0–36) with standard 2.70% house edge.
- **American Table (Double Zero `0` & `00`)**: 38 numbered pockets (0, 00, 1–36) with standard 5.26% house edge.

### Interactive Betting Felt
The canvas-based felt supports touch chip placement, stack aggregation, and visual highlighting:
- **Inside Bets**:
  - *Straight Up* (Single numbers: 35:1)
  - *Split* (Two adjacent numbers: 17:1)
  - *Street* (Three horizontal numbers: 11:1)
  - *Corner / Square* (Four intersecting numbers: 8:1)
  - *Six-Line / Double Street* (Six numbers across two rows: 5:1)
  - *Basket / Top Line* (0, 1, 2, 3 on European [8:1]; 0, 00, 1, 2, 3 on American [6:1])
- **Outside Bets**:
  - *Even-Money Bets* (1:1): Red / Black, Even / Odd, Low (1–18) / High (19–36).
  - *Dozens* (2:1): 1st Dozen (1–12), 2nd Dozen (13–24), 3rd Dozen (25–36).
  - *Columns* (2:1): 1st Col (1, 4, 7...), 2nd Col (2, 5, 8...), 3rd Col (3, 6, 9...).

---

## 3. Spin History & Data Input Modes

### On-Screen Draggable Keypads
- **Floating Compact Keypad**: Tap numbers 0–36 (plus `00` on American tables) to log spins instantly.
- **Draggable & Dockable**: Move the keypad anywhere on the screen so it never covers your areas of analysis.

### Camera OCR & AI Spin Log Scanner
- **Gemini Multimodal Vision Scanner**: Capture a photo or select an image of physical casino display boards, scorecards, or paper logs.
- **Extraction Protocol**: Automatically transcribes numbers in chronological sequence, filters illegal values, and provides a preview dialog before appending to your session.
- **API Key Configuration**: Tap the Key icon to safely store your Google Gemini API Key locally in encrypted device preferences.

### Clipboard & File Import / Export
- **Clipboard Paste**: Open **Sessions -> Paste Spins**, enter a session name, paste numbers separated by commas, spaces, or line breaks (e.g. `17, 20, 00, 32, 15`), and tap **Import**.
- **File Import (`.txt` / `.csv`)**: Reads raw text files formatted from 1st spin to last.
- **File Export**: Exports session spins formatted chronologically for external spreadsheet or simulation software.

### Undo, Redo, Edit & Time Machine Historical Scrubber
- **Undo / Redo**: Immediately undo accidental spin entries with full state reversal.
- **Spin Editing & Deletion**: Tap any spin in the history rail to change its number or delete it; downstream records and metrics recalculate automatically.
- **Time Machine Historical Scrubber**:
  - Drag the bottom scrubber slider to rewind to any past spin.
  - The entire interface (Felt heatmaps, Outside metrics, Moving Averages, and Equity balances) dynamically rewinds to that exact historical moment, allowing post-game analysis of critical inflection points.

---

## 4. Heatmaps & Frequency Analytics

### Rolling Analysis Window
- Configure the lookback sample window in the header (e.g., last 37 spins, 50 spins, or 100 spins).
- All frequency bars and heat gradients update in real-time as new spins arrive.

### Felt Heat Modes
Toggle between three specialized visual heat modes on the Felt tab:
1. **Global Heat**: Visualizes raw cold-to-hot frequencies across numbers and outside positions using a rich color gradient (Deep Blue $\rightarrow$ Neutral Slate $\rightarrow$ Amber $\rightarrow$ Fiery Coral).
2. **Trend Weights**: Calculates Bayesian probability weights based on deviation from theoretical expected value ($1/37$ or $1/38$).
3. **MA Trend**: Colors outside bet positions based on Moving Average crossover momentum (Green for bullish trends above the moving average, Red for declining trends below).

### Manual Hot & Cold Number Overrides
- Pin up to 5 Hot numbers and 5 Cold numbers manually via the Hot/Cold editor dialog to emphasize wheel-specific biases or dealer signatures.

---

## 5. Moving Average (MA) Dashboard

The **MA Tab** transforms discrete roulette spins into continuous trend trajectories across all major bet families.

### Supported MA Indicators
- **SMA (Simple Moving Average)**: Standard arithmetic rolling average.
- **EMA (Exponential Moving Average)**: Gives greater weight to recent spins for rapid momentum detection.
- **ALMA (Arnaud Legoux Moving Average)**: Filters market noise and reduces lag using a Gaussian distribution filter with configurable `Offset` (responsiveness) and `Sigma` (smoothness).
- **HMA (Hull Moving Average)**: Fast and smooth moving average combining weighted moving averages to virtually eliminate lag.
- **KAMA (Kaufman's Adaptive Moving Average)**: Dynamically adjusts smoothing based on market volatility, automatically becoming fast in strong trends and slow during choppy cycles.
- **MD (McGinley Dynamic)**: Adaptive tracking filter invented by John R. McGinley that automatically speeds up in accelerating trends and slows down in choppy ranges by scaling its denominator by $(Source / MD)^4$, avoiding lag and whipsaws.
- **TEMA (Triple Exponential Moving Average)**: Employs a triple-smoothed EMA formula $3 \cdot EMA_1 - 3 \cdot EMA_2 + EMA_3$ to eliminate lag while remaining smooth.

### Period (N) & Parameter Calibration
- **Period (N)**: Quick presets for `20`, `50`, `100`, `150`, `200`, plus `-` / `+` step buttons and a direct entry dialog.
- **Advanced Constants**: Fine-tune ALMA Offset (default `0.85`), ALMA Sigma (default `6.0`), KAMA Fast (default `2`), and KAMA Slow (default `30`).

### Metric Groupings
- **All Metrics**: Overlays all 6 outside pairs simultaneously.
- **Evens**: Isolates Red/Black, High/Low, and Odd/Even.
- **Dozens & Columns**: Isolates 1st/2nd/3rd Dozens and Columns.

### Selector Persistence Across Sessions
All selections under the MA tab (MA Type, Period N, Active Category, and advanced constants) are **globally persistent**. Navigating between sessions, importing new files, or restarting the application preserves your chosen indicators without resetting to defaults.

---

## 6. Trigger Alerts Dashboard

The **Triggers Tab** continuously monitors streak deviations and sleeper positions:
- **Consecutive Streaks**: Alerts when a single color, even/odd, high/low, dozen, or column repeats beyond user-defined streak thresholds (e.g., 4 consecutive Reds).
- **Cold Omissions (Sleepers)**: Alerts when a position has not hit for $N$ consecutive spins (e.g., 2nd Dozen absent for 8 spins).
- **Configurable Thresholds**: Sliders allow independent calibration of trigger sensitivity for even-money bets and 2:1 bets.

---

## 7. Bet Trend Optimization & Auto-Betting Engine

The **Bet Trend Dashboard** provides quantitative strategy modeling and automated stake recommendations based on real-time historical data.

### Bet Families & Optimization
- **Families**:
  - *Evens*: Red/Black, High/Low, Odd/Even.
  - *Dozens & Columns*: 1st/2nd/3rd Dozen and Column combinations.
  - *Combined*: Cross-family optimization selecting the highest-performing opportunities.
- **Optimization Criteria**:
  - *Primary & Secondary Metrics*: Optimize for Net P&L, Maximum Drawdown (Max DD), Win Rate, or Profit Factor.

### Progression Models
- **Auto**: Automatically selects the mathematical progression best suited to the current distribution.
- **Flat Betting**: Constant 1 unit stake per bet.
- **Tiered / Martingale**: Controlled recovery progressions.

### Risk Controls
- **Risk Cap (Deficit-Aware Cap)**:
  - Intelligently caps progression bets so they **only wager what is mathematically required to reach a new session bankroll peak**.
  - Prevents severe over-betting and eliminates unnecessary exposure when recovering from a drawdown.
- **Push or Profit**:
  - Adjusts stake sizing on multi-unit bets so that any win yields at least a net profit or break-even (push) after factoring in zero losses.
- **Equalize Payout**:
  - Normalizes unit allocations across bets with differing payout ratios (e.g., 1:1 vs 2:1).
- **Cold Rule (Strict vs. Lenient) & Cold Limit**:
  - Temporarily suppresses betting on candidates that enter protracted cold streaks beyond the specified limit.
- **Underwater Filter**:
  - Suppresses aggressive betting when bankroll equity is below the starting capital baseline.

### Direct Felt Application
- Tap **Apply Recommended Bets** to instantly mirror the optimal trend bets as live chips on the interactive betting felt.

---

## 8. Gameplay Recorder & Live Bankroll Equity Tracking

The **Game Play Tab** allows tracking live or simulated execution against rigorous bankroll limits.

### Session Setup
- **Roulette Session Anchor**: Binds the gameplay records directly to the active roulette wheel session.
- **Parameters**:
  - Starting Bankroll (e.g., `$1,000`)
  - Base Chip Unit Size (e.g., `$5`)
  - Target Profit / Take-Profit (e.g., `+$300`)
  - Stop-Loss Floor (e.g., `-$200`)

### Real-Time Equity Curve Chart
- Plots dynamic bankroll trajectory with:
  - Green/Red profit/loss fills.
  - Starting Bankroll baseline marker.
  - All-Time Session High marker ($P_{\text{max}}$).
  - Maximum Drawdown indicator ($DD_{\text{max}}$).
  - Current Bankroll and Peak Stake reached tile.

### Spin-by-Spin Ledger Card
- Full ledger table displaying:
  - Spin Number & Resulting Number.
  - Bet Locations & Placed Chip Value.
  - Gross Payout & Net Round P&L.
  - Progressive Bankroll Total.

---

## 9. Session Management, Cloud Backup & Disaster Recovery

### Session Manager
- **Save Current Session**: Stores spin history, manual overrides, chip placements, and analytical configurations.
- **Load Past Sessions**: Switch seamlessly between different casino visits or historical testing archives.
- **Delete & Cleanup**: Safely removes sessions and cleans up associated gameplay records.

### Automated Cloud & Folder Backup
- **Scoped Storage Folder Binding**:
  - Tap **Backup Folder** in the Sessions dialog to bind an external directory (e.g., Google Drive sync folder or local `Documents/RouletteBackups`).
- **Auto-Sync Engine**:
  - Automatically writes `roulette_tactician_backup.json` to the selected folder on every recorded spin, chip modification, and session update.
- **Disaster Recovery**:
  - If the app is uninstalled or storage is cleared, simply re-link your backup folder or import the backup JSON to restore all roulette sessions and gameplay ledgers instantly.

---

## 10. Step-by-Step Practical Workflows

### Workflow A: Live Play Tracking with Deficit-Aware Risk Capping
1. Select your wheel type (**European** or **American**).
2. Open the **Sessions** menu, enter a session name (e.g., `Bellagio Table 4`), and select your backup folder.
3. In the **Game Play** tab, tap **New Gameplay Session**, set starting bankroll to `$500`, base unit to `$10`, and enable **Risk Cap**.
4. As spins occur, enter them using the floating keypad or camera scanner.
5. Review the **Bet Trend** tab recommendations; tap **Apply Bets** to load chips onto the Felt.
6. Verify payouts on the **Equity Chart** and **Ledger** after each spin.

### Workflow B: Quantitative Historical Trend Analysis
1. Tap **Sessions -> Paste Spins** and paste 200+ historical roulette numbers.
2. Switch to the **MA Tab**:
   - Set MA Type to **EMA** or **KAMA**.
   - Set Period $N$ to **50**.
   - Set Category to **Evens**.
3. Observe crossover zones where trend momentum moves above the zero line.
4. Drag the **Time Machine Scrubber** along the bottom to evaluate how betting signals behaved across specific drawdowns.
