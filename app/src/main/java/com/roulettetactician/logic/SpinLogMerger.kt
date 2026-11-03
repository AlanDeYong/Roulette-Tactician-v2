package com.roulettetactician.logic

/**
 * Pure logic for turning a raw scanned spin log into spins that can be safely
 * appended to the existing history.
 *
 * Conventions (match the rest of the app):
 *  - "00" is stored as 37.
 *  - "Oldest-first" lists are chronological (index 0 = oldest).
 */
object SpinLogMerger {

    /** Token the vision model emits for a cell it cannot read (glare / occlusion). */
    const val UNREADABLE = "?"

    /** Minimum matching spins required to trust an overlap (guards against 1/37 coincidences). */
    const val MIN_TRUSTED_OVERLAP = 3

    data class SanitizedLog(
        /** Chronological (oldest-first) readable spins. */
        val spinsOldestFirst: List<Int>,
        /** Number of older entries discarded because an unreadable cell broke the sequence. */
        val droppedOlder: Int
    )

    data class MergeResult(
        /** Number of scanned spins already present at the tail of the existing history. */
        val overlap: Int,
        /** Spins to append, oldest-first. */
        val newSpins: List<Int>,
        /** True when the history was non-empty but no trusted overlap was found (possible gap). */
        val possibleGap: Boolean
    )

    /**
     * Converts the model's newest-first tokens into a chronological integer list.
     *
     * If any cell is unreadable, only the contiguous run of readable *newest* spins is kept,
     * because positions after an unknown cell can't be placed in order with confidence.
     * Invalid tokens are treated as unreadable.
     */
    fun sanitize(tokensNewestFirst: List<String>, allowDoubleZero: Boolean): SanitizedLog {
        val parsed = tokensNewestFirst.map { parseToken(it.trim(), allowDoubleZero) }
        val firstBad = parsed.indexOfFirst { it == null }
        val readableNewestFirst = if (firstBad == -1) parsed else parsed.subList(0, firstBad)
        return SanitizedLog(
            spinsOldestFirst = readableNewestFirst.filterNotNull().reversed(),
            droppedOlder = parsed.size - readableNewestFirst.size
        )
    }

    /**
     * Finds the largest k where the last k existing spins equal the first k scanned spins,
     * and returns only the scanned spins after that overlap.
     */
    fun merge(existingOldestFirst: List<Int>, scannedOldestFirst: List<Int>): MergeResult {
        if (scannedOldestFirst.isEmpty()) return MergeResult(0, emptyList(), false)
        if (existingOldestFirst.isEmpty()) return MergeResult(0, scannedOldestFirst, false)

        val maxK = minOf(existingOldestFirst.size, scannedOldestFirst.size)
        val existingTailStart = existingOldestFirst.size
        for (k in maxK downTo 1) {
            var match = true
            for (i in 0 until k) {
                if (existingOldestFirst[existingTailStart - k + i] != scannedOldestFirst[i]) {
                    match = false
                    break
                }
            }
            if (match) {
                // Short overlaps are only trusted if they cover the entire scan or entire history.
                val trusted = k >= MIN_TRUSTED_OVERLAP || k == scannedOldestFirst.size || k == existingOldestFirst.size
                if (trusted) {
                    return MergeResult(k, scannedOldestFirst.drop(k), false)
                }
                break // A shorter k would be even less trustworthy.
            }
        }
        return MergeResult(0, scannedOldestFirst, possibleGap = true)
    }

    private fun parseToken(token: String, allowDoubleZero: Boolean): Int? {
        if (token == "00") return if (allowDoubleZero) 37 else null
        val n = token.toIntOrNull() ?: return null
        return if (n in 0..36) n else null
    }
}
