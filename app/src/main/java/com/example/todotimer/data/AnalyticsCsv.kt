package com.example.todotimer.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object AnalyticsCsv {
    private data class CsvAction(
        val task: TaskItem,
        val totalMillis: Long,
        val objectiveMillis: Long,
        val routineMillis: Long,
        val routineNames: String,
    )

    fun build(
        objectives: List<Objective>,
        tasks: List<TaskItem>,
        timeEntries: List<TimeEntry>,
        routines: List<Routine>,
        routineProgress: RoutineProgress?,
        range: AnalyticsRange,
        nowMillis: Long,
    ): String {
        val objectivesById = objectives.associateBy { it.id }
        val tasksById = tasks.associateBy { it.id }
        val routinesById = routines.associateBy { it.id }
        val sortedTasks = tasks.map { task ->
            val runningMillis = AnalyticsTime.runningMillis(task, range, nowMillis)
            val objectiveMillis = AnalyticsTime.closedObjectiveMillis(task.id, timeEntries, range) +
                if (task.timerRoutineRunId == null) runningMillis else 0L
            val routineMillis = AnalyticsTime.closedRoutineMillis(task.id, timeEntries, range) +
                if (task.timerRoutineRunId != null) runningMillis else 0L
            val routineIds = timeEntries.asSequence()
                .filter {
                    it.taskId == task.id && it.routineId != null &&
                        AnalyticsTime.overlapMillis(it.startedAt, it.endedAt, range) > 0L
                }
                .mapNotNull { it.routineId }
                .toMutableSet()
            if (
                runningMillis > 0L && routineProgress != null &&
                task.timerRoutineRunId == routineProgress.runId
            ) {
                routineIds += routineProgress.routineId
            }
            CsvAction(
                task = task,
                totalMillis = objectiveMillis + routineMillis,
                objectiveMillis = objectiveMillis,
                routineMillis = routineMillis,
                routineNames = routineIds.mapNotNull { routinesById[it]?.name }
                    .sorted()
                    .joinToString(" | "),
            )
        }.filter { it.totalMillis > 0L }.sortedWith(
            compareBy<CsvAction> {
                objectivesById[it.task.objectiveId]?.title.orEmpty().lowercase(Locale.ROOT)
            }.thenBy { it.task.parentTaskId != null }
                .thenBy { it.task.createdAt },
        )

        return buildString {
            append('\uFEFF')
            append("Date range,Range start,Range end,Objective,Objective tags,")
            append("Action type,Action,Parent task,Status,Work source,Routines,")
            append("Objective time (seconds),Routine time (seconds),")
            append("Total time (seconds),Total time (HH:MM:SS)\r\n")
            sortedTasks.forEach { record ->
                val task = record.task
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
                append(csvCell(workSource(record.objectiveMillis, record.routineMillis)))
                append(',')
                append(csvCell(record.routineNames))
                append(',')
                append(record.objectiveMillis / 1_000L)
                append(',')
                append(record.routineMillis / 1_000L)
                append(',')
                append(record.totalMillis / 1_000L)
                append(',')
                append(csvCell(formatDuration(record.totalMillis)))
                append("\r\n")
            }
        }
    }

    private fun workSource(objectiveMillis: Long, routineMillis: Long): String = when {
        objectiveMillis > 0L && routineMillis > 0L -> "Objective + routine"
        routineMillis > 0L -> "Routine"
        else -> "Objective"
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
