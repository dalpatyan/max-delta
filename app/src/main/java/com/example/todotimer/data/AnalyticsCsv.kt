package com.example.todotimer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AnalyticsCsv {
    fun build(
        objectives: List<Objective>,
        tasks: List<TaskItem>,
        timeEntries: List<TimeEntry>,
        range: AnalyticsRange,
        nowMillis: Long,
    ): String {
        val objectivesById = objectives.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val sortedTasks = tasks.map { task ->
            task to AnalyticsTime.loggedMillis(task, timeEntries, range, nowMillis)
        }.filter { (_, millis) -> millis > 0L }.sortedWith(
            compareBy<Pair<TaskItem, Long>> {
                objectivesById[it.first.objectiveId]?.title.orEmpty().lowercase(Locale.ROOT)
            }.thenBy { it.first.parentTaskId != null }
                .thenBy { it.first.createdAt },
        )

        return buildString {
            append('\uFEFF')
            append("Date range,Range start,Range end,Objective,Objective tags,")
            append("Action type,Action,Parent task,Status,")
            append("Time logged (seconds),Time logged (HH:MM:SS)\r\n")
            sortedTasks.forEach { (task, elapsedMillis) ->
                val objective = objectivesById[task.objectiveId]
                val status = when {
                    task.isTimerRunning -> "Running"
                    task.isDone -> "Done"
                    else -> "Open"
                }
                append(csvCell(range.label))
                append(',')
                append(csvCell(formatRangeBoundary(range.startMillis)))
                append(',')
                append(csvCell(formatRangeBoundary(range.endExclusiveMillis - 1L)))
                append(',')
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

    private fun formatRangeBoundary(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
