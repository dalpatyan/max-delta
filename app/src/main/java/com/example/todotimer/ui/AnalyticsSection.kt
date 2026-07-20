package com.example.todotimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.TaskItem
import com.example.todotimer.util.TimeTracking

private data class ActionTime(val task: TaskItem, val millis: Long)
private data class ObjectiveTime(val objective: Objective, val millis: Long)

@Composable
internal fun AnalyticsSection(
    modifier: Modifier,
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    nowMillis: Long,
    onDownloadCsv: () -> Unit,
) {
    val objectivesById = remember(objectives) { objectives.associateBy { it.id } }
    val actionTimes = tasks.map { task ->
        ActionTime(
            task = task,
            millis = TimeTracking.totalAt(task.trackedMillis, task.timerStartedAt, nowMillis),
        )
    }.sortedByDescending { it.millis }
    val loggedActions = actionTimes.filter { it.millis > 0L }
    val totalMillis = actionTimes.sumOf { it.millis }
    val objectiveTimes = objectives.map { objective ->
        ObjectiveTime(
            objective = objective,
            millis = actionTimes.filter { it.task.objectiveId == objective.id }.sumOf { it.millis },
        )
    }.filter { it.millis > 0L }.sortedByDescending { it.millis }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "analytics_summary") {
            AnalyticsSummary(
                totalMillis = totalMillis,
                loggedActions = loggedActions.size,
                totalActions = tasks.size,
                onDownloadCsv = onDownloadCsv,
            )
        }
        item(key = "objective_time_heading") {
            Text(
                "Time by objective",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (objectiveTimes.isEmpty()) {
            item(key = "no_objective_time") { NoTimeLogged() }
        } else {
            items(objectiveTimes, key = { "objective_time_${it.objective.id}" }) { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.objective.title, fontWeight = FontWeight.SemiBold)
                            if (record.objective.tags.isNotEmpty()) {
                                Text(
                                    record.objective.tags.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Text(
                            TimeTracking.format(record.millis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item(key = "action_time_heading") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Action-wise time",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Each task and subtask is reported as an action.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (loggedActions.isEmpty()) {
            item(key = "no_action_time") { NoTimeLogged() }
        } else {
            items(loggedActions, key = { "action_time_${it.task.id}" }) { record ->
                AnalyticsActionRow(
                    record = record,
                    objective = objectivesById[record.task.objectiveId],
                )
            }
        }
    }
}

@Composable
private fun AnalyticsSummary(
    totalMillis: Long,
    loggedActions: Int,
    totalActions: Int,
    onDownloadCsv: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("TOTAL TIME LOGGED", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                TimeTracking.format(totalMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$loggedActions of $totalActions actions have logged time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDownloadCsv,
                enabled = totalActions > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download CSV") }
            Spacer(Modifier.height(4.dp))
            Text(
                "Includes objectives, tags, tasks, subtasks, status, and cumulative time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AnalyticsActionRow(record: ActionTime, objective: Objective?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.task.title, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(if (record.task.isSubtask) "Subtask" else "Task")
                        objective?.let { append(" • ${it.title}") }
                        append(if (record.task.isDone) " • Done" else " • Open")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                TimeTracking.format(record.millis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NoTimeLogged() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            "No time logged yet. Start working on a task or subtask to build analytics.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
