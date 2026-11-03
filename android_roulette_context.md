App Context Extraction: Roulette Tactician

Target Platform: Android (Samsung Galaxy S26 Ultra)
Orientation: Landscape Mode (Exclusive/Primary)
Tech Stack: Kotlin, Jetpack Compose, Room Database, Hilt, Coroutines

1. Executive Summary

"Roulette Tactician" is a strategic companion app for Roulette players. It tracks real-world spins, calculates "Hot" and "Cold" numbers based on a specific algorithm (Frequency + Recency), and generates a heatmap on a betting table. Users drag and drop chips onto this table to plan bets.

2. Device Specification (S26 Ultra Context)

Form Factor: ~6.8+ inch AMOLED.

Resolution: QHD+ (High Density).

Aspect Ratio: Wide (approx 19.3:9 or 20:9).

Input: Touch (Multi-touch for chips) & S-Pen (Hover tooltips).

Layout Strategy:

Left Pane (20%): Spin History (Vertical List) & Stats.

Center Pane (65%): The Betting Table (Canvas). Maximize this space.

Right Pane (15%): Chip Rack (Vertical) & Action Buttons (Spin, Clear, Settings).

3. Core Algorithms (Crucial for Porting)

A. The Heatmap Logic (The "Brain")

The app colors numbers on the board based on a ranking system.

Input: spinHistory (List<Int>), hotNumbers (Manual Overrides), coldNumbers (Manual Overrides), window (Int, e.g., last 50 spins).

Logic:

Manual Check (Highest Priority):

If a number is in hotNumbers, Color = Bright Yellow (#facc15).

If a number is in coldNumbers, Color = Deep Navy (#172554).

Ranking (Non-Manual Numbers):

Calculate Frequency (Count in window).

Calculate Recency (Index of last occurrence normalized 0.0-1.0).

Score = Frequency + (Recency * 0.99).

Sort numbers by Score Descending.

Determine Median Rank.

Gradient Generation (The "Progressive" Nuance):

Top Half (Hot): Interpolate color from Slate Gray to Golden Orange. Intensity depends on distance from median.

Bottom Half (Cold): Interpolate color from Slate Gray to Royal Blue.

CRITICAL NUANCE (Progressive Cooling): The intensity of the Cold (Blue) color must be scaled by the total number of spins.

spinFactor = Math.min(totalSpins, 50) / 50

FinalIntensity = CalculatedIntensity * spinFactor

Why? This prevents the board from turning deep blue immediately when history is sparse. It requires ~50 spins to reach full "Cold" intensity.

B. The Geometric Snap System (The Board)

The board is not a grid of buttons. It is a coordinate system.

Table Toggle: The app must support switching between EU (Single Zero) and US (Double Zero) layouts. This changes the geometry of the "Zero" area on the left side of the canvas.

Touch Handling: When a user drags a chip to (x,y):

Convert screen coordinates to "Board Coordinates".

Hit Testing: Check distance to specific "Snap Points" (Threshold < 0.6 radius):

Straight: Center of a number cell.

Split: Edge between two numbers (Vertical or Horizontal).

Corner: Intersection of 4 numbers.

Street: Top edge of a row.

Line: Intersection of 2 rows at the top edge.

Outside Bets: Defined rectangles for Even/Odd, Red/Black, Dozens.

Android Implementation: Use Canvas within BoxWithConstraints. Use detectDragGestures.

4. Input & Validation Logic (Keypad)

The app requires a custom numerical input method, not the system keyboard.

Component: Custom Modal / Bottom Sheet Keypad.

Input Logic:

Accumulator: Users type digits (e.g., "3", then "6").

Max Length: 2 Digits maximum.

Validation Rule: The entered number MUST NOT exceed 36.

If user types "3" -> "3" (Valid).

If user types "6" (making "36") -> "36" (Valid).

If user types "9" (making "39") -> REJECT or reset to new digit "9".

Controls:

0-9 Grid.

DEL: Backspace (remove last digit).

OK: Submit number to spinHistory.

0 vs 00: The keypad allows 0. If US Mode is active, specific handling or a dedicated button for 00 may be required, or 00 can be treated as a distinct button on the board logic. (Note: Current React logic maps keypad 0-36, US/EU toggle affects board display).

5. Data Models (Kotlin)

// Represents a recorded spin
@Entity(tableName = "spins")
data class SpinResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val number: Int, // 0-36
    val timestamp: Long
)

// Represents a chip placed on the board
data class PlacedChip(
    val id: Long = System.currentTimeMillis(),
    val value: Int, // e.g., 1, 5, 25, 100, 500, 1000
    val xPercent: Float, // Relative X position (0.0 - 1.0) on Board Canvas
    val yPercent: Float, // Relative Y position (0.0 - 1.0) on Board Canvas
    val betId: String // e.g., "num-32", "split-17-20", "doz-1"
)

// Session Data for Save/Load
data class StrategySession(
    val name: String,
    val hotOverrides: List<Int?>,
    val coldOverrides: List<Int?>,
    val analysisWindow: Int,
    val tableType: String // "EU" or "US"
)


6. UI/UX Requirements

A. The Board (Center)

Must support Pinch-to-Zoom and Pan (use Modifier.transformable).

Rendering:

Draw the grid lines (White, low opacity).

Draw Number backgrounds (Gradient calculated from Heatmap logic).

Draw Numbers (Text).

Draw Chips (Z-Index highest). Chips should cast a shadow.

B. Controls (Sidebars)

Chip Selector: Vertical scroll list of chips (1, 5, 25, 100, 500, 1k).

Stats Panel:

Display "Last 20" spins.

Show current Total Bet value.

C. S-Pen Integration (S26 Ultra Specific)

Hover Events: Using Modifier.pointerInput, detect PointerEventType.Enter/Exit.

Action: When S-Pen hovers over a number:

Show tooltip: "Number 17: Hit 3 times in last 50 spins (Rank #4)".

7. Migration Checklist for AI Agent

Setup: Create Android project with Compose & Hilt.

Database: Setup Room for SpinHistory and Sessions.

Logic Port: Convert the JS useMemo stats logic to a Kotlin ViewModel function calculateHeatmap(spins: List<Int>) including the Progressive Cooling factor.

Canvas Engine: Build the RouletteBoard composable.

Crucial: Port the findSnapTarget function using normalized coordinates (0.0 - 1.0).

Keypad: Implement the custom 2-digit, max-36 validation logic.