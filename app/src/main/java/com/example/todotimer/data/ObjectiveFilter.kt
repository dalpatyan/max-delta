package com.example.todotimer.data

object ObjectiveFilter {
    fun filter(
        objectives: List<Objective>,
        query: String,
        tag: String?,
    ): List<Objective> {
        val cleanQuery = query.trim()
        return objectives.filter { objective ->
            val matchesText = cleanQuery.isEmpty() ||
                objective.title.contains(cleanQuery, ignoreCase = true)
            val matchesTag = tag == null || objective.tags.any { it.equals(tag, ignoreCase = true) }
            matchesText && matchesTag
        }
    }
}
