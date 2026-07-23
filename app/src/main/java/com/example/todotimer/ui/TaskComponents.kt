package com.example.todotimer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.ObjectiveVisibility
import com.example.todotimer.util.TimeTracking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun TaskCard(
    task: TaskItem,
    subtasks: List<TaskItem>,
    nowMillis: Long,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onToggleTimer: (TaskItem) -> Unit,
    onAddSubtask: () -> Unit,
    onEdit: (TaskItem) -> Unit,
    showClosed: Boolean,
) {
    val completedSubtasks = subtasks.count { it.isDone }
    val visibleSubtasks = ObjectiveVisibility.visibleActions(subtasks, showClosed)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .alpha(if (task.isDone) 0.72f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = { onSetDone(task, it) },
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    )
                    if (task.note.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = task.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    task.reminderAt?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Reminder: ${formatReminder(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TrackingSummary(task = task, nowMillis = nowMillis)

            if (subtasks.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { completedSubtasks.toFloat() / subtasks.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$completedSubtasks of ${subtasks.size} subtasks complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { onToggleTimer(task) },
                enabled = !task.isDone,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (task.isTimerRunning) "Stop working" else "Start working")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onAddSubtask, enabled = !task.isDone) { Text("Add subtask") }
                TextButton(onClick = { onEdit(task) }) { Text("Edit") }
            }

            visibleSubtasks.forEach { subtask ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SubtaskRow(
                    task = subtask,
                    nowMillis = nowMillis,
                    onSetDone = onSetDone,
                    onToggleTimer = onToggleTimer,
                    onEdit = onEdit,
                )
            }
        }
    }
}

@Composable
private fun SubtaskRow(
    task: TaskItem,
    nowMillis: Long,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onToggleTimer: (TaskItem) -> Unit,
    onEdit: (TaskItem) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (task.isDone) 0.68f else 1f),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = task.isDone,
                    onCheckedChange = { onSetDone(task, it) },
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    )
                    if (task.note.isNotBlank()) {
                        Text(
                            task.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    task.reminderAt?.let {
                        Text(
                            formatReminder(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TrackingSummary(task = task, nowMillis = nowMillis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = { onToggleTimer(task) },
                    enabled = !task.isDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (task.isTimerRunning) "Stop working" else "Start working")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onEdit(task) }) { Text("Edit") }
            }
        }
    }
}

@Composable
private fun TrackingSummary(task: TaskItem, nowMillis: Long) {
    val elapsed = TimeTracking.totalAt(task.trackedMillis, task.timerStartedAt, nowMillis)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (task.isTimerRunning) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.error,
            ) {}
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = if (task.isTimerRunning) "Working • ${TimeTracking.format(elapsed)}"
            else "Tracked • ${TimeTracking.format(elapsed)}",
            style = MaterialTheme.typography.labelLarge,
            color = if (task.isTimerRunning) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatReminder(epochMillis: Long): String {
    val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val prefix = when (dateTime.toLocalDate()) {
        LocalDate.now() -> "Today"
        LocalDate.now().plusDays(1) -> "Tomorrow"
        else -> dateTime.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
    return "$prefix • ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
}
