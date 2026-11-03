package com.roulettetactician.logic

import com.roulettetactician.ui.model.PlacedChip
import java.util.zip.Adler32

/**
 * High-performance, memory-efficient utility for validating, serializing,
 * and deserializing session data (spin history, chips, and overrides).
 *
 * Optimized to handle tens of thousands of spins in single-digit milliseconds
 * with minimal heap allocation.
 */
object SessionDataValidator {

    const val MAX_VALID_ROULETTE_NUMBER = 37 // 0-36, and 37 for 00 in US tables

    data class HistoryValidationResult(
        val numbers: List<Int>,
        val totalTokens: Int,
        val validCount: Int,
        val droppedCount: Int,
        val checksum: Long
    ) {
        val isValid: Boolean get() = droppedCount == 0
    }

    /**
     * Quickly validates if a number is a legitimate roulette outcome (0..37).
     */
    @JvmStatic
    fun isValidWheelNumber(number: Int): Boolean {
        return number in 0..MAX_VALID_ROULETTE_NUMBER
    }

    /**
     * Calculates an Adler-32 checksum for a sequence of spin numbers.
     * Extremely fast (O(N) CPU arithmetic, zero heap allocation).
     */
    @JvmStatic
    fun calculateChecksum(numbers: List<Int>): Long {
        if (numbers.isEmpty()) return 0L
        val adler = Adler32()
        val buffer = ByteArray(numbers.size)
        for (i in numbers.indices) {
            buffer[i] = numbers[i].toByte()
        }
        adler.update(buffer)
        return adler.value
    }

    /**
     * Fast single-pass parser and validator for spin history strings.
     * Avoids String.split() allocations and validates integer boundaries in-place.
     *
     * @param raw Raw string containing numbers (e.g. "0,32,15,19,4...")
     * @return HistoryValidationResult containing valid numbers, counts, and checksum.
     */
    @JvmStatic
    fun parseAndValidateHistory(raw: String?): HistoryValidationResult {
        if (raw.isNullOrBlank()) {
            return HistoryValidationResult(
                numbers = emptyList(),
                totalTokens = 0,
                validCount = 0,
                droppedCount = 0,
                checksum = 0L
            )
        }

        val len = raw.length
        val result = ArrayList<Int>(minOf(len / 2 + 1, 1024))
        var totalTokens = 0
        var droppedCount = 0

        var i = 0
        while (i < len) {
            // Skip separators (comma, whitespace, newline, tab, semicolon)
            while (i < len) {
                val c = raw[i]
                if (c != ',' && c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != ';') {
                    break
                }
                i++
            }
            if (i >= len) break

            // Parse token
            var isNegative = false
            if (raw[i] == '-') {
                isNegative = true
                i++
            }

            val tokenStart = i
            var currentVal = 0
            var hasDigits = false
            var isInvalidToken = false

            while (i < len) {
                val c = raw[i]
                if (c == ',' || c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == ';') {
                    break
                }
                if (c in '0'..'9') {
                    hasDigits = true
                    if (!isInvalidToken) {
                        currentVal = currentVal * 10 + (c - '0')
                        if (currentVal > MAX_VALID_ROULETTE_NUMBER) {
                            isInvalidToken = true
                        }
                    }
                } else {
                    isInvalidToken = true
                }
                i++
            }

            // Check if token was literally "00" (American Double-Zero)
            val tokenLen = i - tokenStart
            if (!isNegative && !isInvalidToken && tokenLen == 2 && raw[tokenStart] == '0' && raw[tokenStart + 1] == '0') {
                currentVal = 37 // Maps to 00
            }

            if (hasDigits) {
                totalTokens++
                if (!isNegative && !isInvalidToken && isValidWheelNumber(currentVal)) {
                    result.add(currentVal)
                } else {
                    droppedCount++
                }
            } else if (isNegative || isInvalidToken) {
                totalTokens++
                droppedCount++
            }
        }

        val checksum = calculateChecksum(result)
        return HistoryValidationResult(
            numbers = result,
            totalTokens = totalTokens,
            validCount = result.size,
            droppedCount = droppedCount,
            checksum = checksum
        )
    }

    /**
     * Fast serialization of spin numbers to a comma-separated string.
     * Pre-allocates StringBuilder capacity to minimize heap re-allocations.
     */
    @JvmStatic
    fun serializeHistory(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        // Estimate ~3 chars per number (e.g. "36," is 3 chars)
        val sb = StringBuilder(numbers.size * 3)
        for (i in numbers.indices) {
            if (i > 0) sb.append(',')
            sb.append(numbers[i])
        }
        return sb.toString()
    }

    /**
     * Formats spin numbers (1st to last) into clean file content.
     * One spin per line, converting 37 to "00" for standard roulette compatibility.
     */
    @JvmStatic
    fun serializeHistoryForFile(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        val sb = StringBuilder(numbers.size * 4)
        for (i in numbers.indices) {
            val num = numbers[i]
            if (num == 37) {
                sb.append("00")
            } else {
                sb.append(num)
            }
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }


    /**
     * Safely parses hot/cold overrides string into List<Int?> without throwing exceptions.
     */
    @JvmStatic
    fun parseOverrides(raw: String?): List<Int?> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = mutableListOf<Int?>()
        val tokens = raw.split(',')
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) {
                result.add(null)
            } else {
                val num = trimmed.toIntOrNull()
                if (num != null && isValidWheelNumber(num)) {
                    result.add(num)
                } else {
                    result.add(null)
                }
            }
        }
        return result
    }

    /**
     * Serializes hot/cold overrides list to a CSV string.
     */
    @JvmStatic
    fun serializeOverrides(overrides: List<Int?>): String {
        if (overrides.isEmpty()) return ""
        val sb = StringBuilder(overrides.size * 5)
        for (i in overrides.indices) {
            if (i > 0) sb.append(',')
            val item = overrides[i]
            if (item != null && isValidWheelNumber(item)) {
                sb.append(item)
            } else {
                sb.append("null")
            }
        }
        return sb.toString()
    }

    /**
     * Safely parses chips string representation without throwing exceptions.
     * Format: "val:x:y:betId|val:x:y:betId"
     */
    @JvmStatic
    fun parseChips(raw: String?): List<PlacedChip> {
        if (raw.isNullOrBlank()) return emptyList()
        val chips = mutableListOf<PlacedChip>()
        val entries = raw.split('|')
        for (entry in entries) {
            if (entry.isBlank()) continue
            val parts = entry.split(':')
            if (parts.size == 4) {
                val value = parts[0].toIntOrNull()
                val xPercent = parts[1].toFloatOrNull()
                val yPercent = parts[2].toFloatOrNull()
                val betId = parts[3].trim()
                if (value != null && value > 0 && xPercent != null && yPercent != null && betId.isNotEmpty()) {
                    chips.add(
                        PlacedChip(
                            id = System.nanoTime() + chips.size,
                            value = value,
                            xPercent = xPercent,
                            yPercent = yPercent,
                            betId = betId
                        )
                    )
                }
            }
        }
        return chips
    }

    /**
     * Serializes placed chips to string format.
     */
    @JvmStatic
    fun serializeChips(chips: List<PlacedChip>): String {
        if (chips.isEmpty()) return ""
        val sb = StringBuilder(chips.size * 24)
        for (i in chips.indices) {
            if (i > 0) sb.append('|')
            val c = chips[i]
            sb.append(c.value).append(':')
                .append(c.xPercent).append(':')
                .append(c.yPercent).append(':')
                .append(c.betId)
        }
        return sb.toString()
    }
}
