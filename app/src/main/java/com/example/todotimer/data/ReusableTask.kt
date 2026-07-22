package com.example.todotimer.data

data class ReusableTask(
    val id: Long,
    val title: String,
    val subtaskTitles: List<String>,
    val createdAt: Long,
)

object ReusableTaskCodec {
    private const val SEPARATOR = '\u001F'

    fun encode(titles: List<String>): String = normalize(titles).joinToString(SEPARATOR.toString())

    fun decode(value: String): List<String> = if (value.isBlank()) {
        emptyList()
    } else {
        normalize(value.split(SEPARATOR))
    }

    private fun normalize(titles: List<String>): List<String> = titles.mapNotNull { rawTitle ->
        rawTitle.replace(SEPARATOR, ' ').trim().takeIf { it.isNotEmpty() }
    }
}
