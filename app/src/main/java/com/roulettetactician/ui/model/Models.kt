package com.roulettetactician.ui.model

data class PlacedChip(
    val id: Long = System.currentTimeMillis(),
    val value: Int,
    val xPercent: Float, // Normalized X (0.0 - 1.0)
    val yPercent: Float, // Normalized Y (0.0 - 1.0)
    val betId: String,
    val stack: List<Int> = listOf(value)
) {
    fun safeStack(): List<Int> {
        return if (stack.isNullOrEmpty()) listOf(value) else stack
    }
}

data class TableState(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

data class GameState(
    val chips: List<PlacedChip> = emptyList(),
    val totalBet: Int = 0,
    val selectedChipValue: Int = 5,
    val wheelName: String = "",
    val analysisWindow: Int = 50,
    val hotOverrides: List<Int?> = List(5) { null },
    val coldOverrides: List<Int?> = List(5) { null },
    val tableType: TableType = TableType.EU
)

enum class TableType {
    EU, US
}
