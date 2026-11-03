# Task List - Roulette Tactician (Android Port)

## Project Status: 🚧 In Progress

### 1. Build & Configuration
- [x] Fix Java version mismatch (Set to Java 17)
- [x] Configure `gradle.properties` (Memory settings, AndroidX)
- [x] Stabilize Dependency Versions (Gradle 8.13, AGP 8.13.2, Compose 2024.04)
- [x] Recreate missing resources (`themes.xml`, `strings.xml`, `res` dir)
- [x] Fix Kotlin Compilation Errors (`MainScreen.kt`, `RouletteBoard.kt`)
- [x] Verify Build Success (`./gradlew assembleDebug`)

### 2. Core Logic Verification
- [x] Implement Unit Tests for Geometry (`LogicTests.kt`)
- [x] Implement Unit Tests for Heatmap Calculator (`LogicTests.kt`)
- [x] Verify Target Snapping (Zero, Numbers, Outside Bets)
- [x] Verify Heatmap Frequency & Recency Logic

### 3. Feature Implementation & Verification
- [ ] Verify Game State Management (ViewModel)
- [ ] Verify S-Pen Integration (Hover Tooltips)
- [ ] Verify Drag & Drop Interactions
- [ ] Verify Persistence (Room Database History)

### 4. Final Polish
- [ ] UI visual review (Dark mode, text sizes)
- [ ] Performance check (Canvas rendering)
