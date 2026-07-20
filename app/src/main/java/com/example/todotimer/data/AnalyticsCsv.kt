package com.example.todotimer.data

import com.example.todotimer.util.TimeTracking
import java.util.Locale

object AnalyticsCsv {
    fun build(
        objectives: List<Objective>,
        tasks: List<TaskItem>,
        nowMillis: Long,
    ): String {
        val objectivesById = objectives.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val sortedTasks = tasks.sortedWith(
            compareBy<TaskItem> { objectivesById[it.objectiveId]?.title.orEmpty().lowercase(Locale.ROOT) }
                .thenBy { it.parentTaskId != null }
                .thenBy { it.createdAt },
        )

        return buildString {
            append('\uFEFF')
            append("Objective,Objective tags,Action type,Action,Parent task,Status,")
            append("Time logged (seconds),Time logged (HH:MM:SS)\r\n")
            sortedTasks.forEach { task ->
                val objective = objectivesById[task.objectiveId]
                val elapsedMillis = TimeTracking.totalAt(
                    task.trackedMillis,
                    task.timerStartedAt,
                    nowMillis,
                )
                val status = when {
                    task.isTimerRunning -> "Running"
                    task.isDone -> "Done"
                    else -> "Open"
                }
                append(csvCell(objective?.title.orEmpty()))
                append(',')
                append(csvCell(objective?.tags?.joinToString(" | ").orEmpty()))
                append(',')
                append(csvCell(if (task.isSubtask) "Subtask" else "Task"))
                append(',')
                append(csvCell(task.title))
                append(',')
                append(csvCell(task.parentTaskId?.let { tasksById[it]?.title }.orEmpty()))
                append(',')
                append(csvCell(status))
                append(',')
                append(elapsedMillis / 1_000L)
                append(',')
                append(csvCell(formatDuration(elapsedMillis)))
                append("\r\n")
            }
        }
    }

    private fun csvCell(value: String): String =
        "\"${value.replace("\"", "\"\"").replace('\r', ' ').replace('\n', ' ')}\""

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
