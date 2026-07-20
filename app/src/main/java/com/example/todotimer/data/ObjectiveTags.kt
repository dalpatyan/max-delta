package com.example.todotimer.data

import java.util.Locale

object ObjectiveTags {
    private const val STORAGE_SEPARATOR = '\u001F'

    fun fromInput(input: String): List<String> = normalize(input.split(','))

    fun normalize(tags: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return tags.mapNotNull { rawTag ->
            val tag = rawTag.replace(STORAGE_SEPARATOR, ' ').trim()
            val key = tag.lowercase(Locale.ROOT)
            if (tag.isEmpty() || !seen.add(key)) null else tag
        }
    }

    fun encode(tags: List<String>): String = normalize(tags).joinToString(STORAGE_SEPARATOR.toString())

    fun decode(value: String): List<String> = if (value.isBlank()) {
        emptyList()
    } else {
        normalize(value.split(STORAGE_SEPARATOR))
    }
}
