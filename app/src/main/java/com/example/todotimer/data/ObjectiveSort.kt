package com.example.todotimer.data

enum class ObjectiveSortOrder(val label: String) {
    RECENTLY_ADDED("Recently added"),
    UPCOMING("Upcoming"),
    RECENTLY_WORKED("Recently worked"),
}

object ObjectiveSorter {
    fun sort(
        objectives: List<Objective>,
        tasks: List<TaskItem>,
        order: ObjectiveSortOrder,
        nowMillis: Long,
    ): List<Objective> {
        val tasksByObjective = tasks.groupBy { it.objectiveId }
        return when (order) {
            ObjectiveSortOrder.RECENTLY_ADDED -> objectives.sortedWith(
                compareByDescending<Objective> { it.createdAt }.thenByDescending { it.id },
            )

            ObjectiveSortOrder.UPCOMING -> objectives.sortedWith(
                compareBy<Objective> { objective ->
                    tasksByObjective[objective.id]
                        .orEmpty()
                        .asSequence()
                        .filter { !it.isDone }
                        .mapNotNull { it.reminderAt }
                        .filter { it >= nowMillis }
                        .minOrNull() ?: Long.MAX_VALUE
                }.thenByDescending { it.createdAt },
            )

            ObjectiveSortOrder.RECENTLY_WORKED -> objectives.sortedWith(
                compareByDescending<Objective> { objective ->
                    maxOf(
                        objective.updatedAt,
                        tasksByObjective[objective.id].orEmpty().maxOfOrNull { it.updatedAt } ?: 0L,
                    )
                }.thenByDescending { it.createdAt },
            )
        }
    }
}
