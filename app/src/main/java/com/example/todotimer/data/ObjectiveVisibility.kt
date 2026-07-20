package com.example.todotimer.data

object ObjectiveVisibility {
    fun isClosed(objectiveId: Long, tasks: List<TaskItem>): Boolean {
        val topLevelTasks = tasks.filter {
            it.objectiveId == objectiveId && it.parentTaskId == null
        }
        return topLevelTasks.isNotEmpty() && topLevelTasks.all { it.isDone }
    }

    fun visibleObjectives(
        objectives: List<Objective>,
        tasks: List<TaskItem>,
        showClosed: Boolean,
    ): List<Objective> = if (showClosed) {
        objectives
    } else {
        objectives.filterNot { isClosed(it.id, tasks) }
    }

    fun visibleActions(actions: List<TaskItem>, showClosed: Boolean): List<TaskItem> =
        if (showClosed) actions else actions.filterNot { it.isDone }
}
