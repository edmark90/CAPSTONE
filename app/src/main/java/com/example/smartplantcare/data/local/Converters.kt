package com.example.smartplantcare.data.local

import androidx.room.TypeConverter

class Converters {

    private val LIST_SEPARATOR = "|||"

    /**
     * Regex that matches day labels at the start of a treatment item:
     *   - "Day 1:", "Day 2:", …, "Day 7:"
     *   - "Day 1 -", "Day 2 -", …
     *   - "Action 1:", "Action 2:", …
     *
     * No lookbehind is needed because:
     *   1. This pattern is only used for old comma-separated data.
     *   2. The instruction text never contains "Day X:" with a colon/hyphen
     *      inside it — only at the actual day boundaries.
     *   3. Lookbehind with variable-length alternation can cause
     *      PatternSyntaxException on older Android API levels.
     */
    private val DAY_BOUNDARY_REGEX = Regex(
        """(?i)Day\s+\d+\s*[-:]|Action\s+\d+\s*[-:]"""
    )

    @TypeConverter
    fun fromStringList(list: List<String>?): String? {
        return list?.joinToString(LIST_SEPARATOR)
    }

    @TypeConverter
    fun toStringList(data: String?): List<String>? {
        if (data.isNullOrBlank()) return null
        // New separator – fast path: split by "|||"
        if (data.contains(LIST_SEPARATOR)) {
            return data.split(LIST_SEPARATOR).map { it.trim() }.filter { it.isNotBlank() }
        }
        // Old or corrupted data – split on day boundaries instead of commas.
        // Comma-splitting is unreliable because treatment instructions CONTAIN commas.
        return splitOnDayBoundaries(data)
    }

    /**
     * Splits the stored string into a list of day entries by detecting
     * "Day X:", "Day X -", or "Action X:" boundaries.
     *
     * This correctly recovers old comma-separated data where commas inside
     * the instruction text caused the naive [String.split] to produce
     * fragmented/incomplete items.
     */
    private fun splitOnDayBoundaries(data: String): List<String> {
        val matches = DAY_BOUNDARY_REGEX.findAll(data).toList()
        if (matches.isEmpty()) {
            // No day boundaries found – return the entire text as a single item
            return listOf(data.trim())
        }

        val result = mutableListOf<String>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else data.length
            val entry = data.substring(start, end).trim()
            if (entry.isNotBlank()) {
                result.add(entry)
            }
        }
        return result
    }

    @TypeConverter
    fun fromIntList(list: List<Int>?): String? {
        return list?.joinToString(",")
    }

    @TypeConverter
    fun toIntList(data: String?): List<Int>? {
        return data?.split(",")?.mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun fromBooleanList(list: List<Boolean>?): String? {
        return list?.joinToString(",") { it.toString() }
    }

    @TypeConverter
    fun toBooleanList(data: String?): List<Boolean>? {
        return data?.split(",")?.map { it.toBoolean() }
    }
}
