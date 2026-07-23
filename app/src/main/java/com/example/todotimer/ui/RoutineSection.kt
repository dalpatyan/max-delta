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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Routine
import com.example.todotimer.data.RoutineFrequency
import com.example.todotimer.data.RoutineProgress
import com.example.todotimer.data.TaskItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun RoutineSection(
    modifier: Modifier,
    routines: List<Routine>,
    routineProgress: RoutineProgress?,
    tasks: List<TaskItem>,
    onEdit: (Routine) -> Unit,
    onSetEnabled: (Routine, Boolean) -> Unit,
    onStartRoutine: (RoutineProgress) -> Unit,
) {
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        routineProgress?.let { progress ->
            item(key = "routine_progress") {
                ActiveRoutineCard(
                    progress = progress,
                    currentTask = progress.currentTaskId?.let(tasksById::get),
                    onStart = { onStartRoutine(progress) },
                )
            }
        }
        item(key = "routine_heading") {
            Column {
                Text("Your routines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Selected tasks reopen automatically at the scheduled time and follow your order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (routines.isEmpty()) {
            item(key = "routine_empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        "No routines yet. Group tasks into a repeating sequence on the schedule you choose.",
                        modifier = Modifier.padding(20.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(routines, key = { "routine_${it.id}" }) { routine ->
                RoutineCard(
                    routine = routine,
                    tasksById = tasksById,
                    onEdit = { onEdit(routine) },
                    onSetEnabled = { enabled -> onSetEnabled(routine, enabled) },
                )
            }
        }
    }
}

@Composable
private fun ActiveRoutineCard(
    progress: RoutineProgress,
    currentTask: TaskItem?,
    onStart: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                if (progress.isStarted) "ROUTINE IN PROGRESS" else "ROUTINE READY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(progress.routineName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Step ${progress.currentPosition + 1} of ${progress.totalSteps}: " +
                    (currentTask?.title ?: "Task unavailable"),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!progress.isStarted || currentTask?.isTimerRunning != true) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStart,
                    enabled = currentTask != null && !currentTask.isDone,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (progress.isStarted) "Start tracking this task" else "Start routine")
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    tasksById: Map<Long, TaskItem>,
    onEdit: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "${routineScheduleLabel(routine)} • ${formatRoutineTime(routine.nextRunAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (routine.isEnabled) "Scheduled" else "Paused",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (routine.isEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            routine.taskIds.forEachIndexed { index, taskId ->
                Text(
                    "${index + 1}. ${tasksById[taskId]?.title ?: "Deleted task"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (tasksById.containsKey(taskId)) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onEdit) { Text("Edit routine") }
                OutlinedButton(onClick = { onSetEnabled(!routine.isEnabled) }) {
                    Text(if (routine.isEnabled) "Pause" else "Resume")
                }
            }
        }
    }
}

private fun formatRoutineTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))

private fun routineScheduleLabel(routine: Routine): String {
    if (routine.frequency != RoutineFrequency.CUSTOM) return routine.frequency.label
    return routine.customDays
        .sortedBy { day -> (day.value % 7) }
        .joinToString(", ") { day ->
            day.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
        }
        .ifBlank { "Custom" }
}
