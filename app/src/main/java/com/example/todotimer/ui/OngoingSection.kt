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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.TaskItem
import com.example.todotimer.util.TimeTracking
import java.util.Locale

@Composable
internal fun OngoingSection(
    modifier: Modifier,
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    nowMillis: Long,
    onToggleTimer: (TaskItem) -> Unit,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onEdit: (TaskItem) -> Unit,
) {
    val objectivesById = remember(objectives) { objectives.associateBy { it.id } }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    val activeTask = tasks.firstOrNull { it.isTimerRunning }
    val listState = rememberLazyListState()
    val openTasks = remember(tasks, objectivesById, activeTask?.id) {
        tasks.filter { !it.isDone && it.id != activeTask?.id }
            .sortedWith(
                compareBy<TaskItem> {
                    objectivesById[it.objectiveId]?.title.orEmpty().lowercase(Locale.ROOT)
                }.thenBy { it.isSubtask }.thenByDescending { it.createdAt },
            )
    }

    LaunchedEffect(activeTask?.id) {
        if (activeTask != null) listState.animateScrollToItem(0)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "active_heading") {
            Text(
                "Currently working",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (activeTask == null) {
            item(key = "no_active") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No timer is running", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Start any open action below when you begin working.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item(key = "active_${activeTask.id}") {
                OngoingActionCard(
                    task = activeTask,
                    objective = objectivesById[activeTask.objectiveId],
                    parentTitle = activeTask.parentTaskId?.let { tasksById[it]?.title },
                    nowMillis = nowMillis,
                    isActive = true,
                    onToggleTimer = onToggleTimer,
                    onSetDone = onSetDone,
                    onEdit = onEdit,
                )
            }
        }

        item(key = "open_heading") {
            Text(
                "Open actions (${openTasks.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (openTasks.isEmpty()) {
            item(key = "no_open") {
                Text(
                    "No other unfinished tasks or subtasks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(openTasks, key = { "open_${it.id}" }) { task ->
                OngoingActionCard(
                    task = task,
                    objective = objectivesById[task.objectiveId],
                    parentTitle = task.parentTaskId?.let { tasksById[it]?.title },
                    nowMillis = nowMillis,
                    isActive = false,
                    onToggleTimer = onToggleTimer,
                    onSetDone = onSetDone,
                    onEdit = onEdit,
                )
            }
        }
    }
}

@Composable
private fun OngoingActionCard(
    task: TaskItem,
    objective: Objective?,
    parentTitle: String?,
    nowMillis: Long,
    isActive: Boolean,
    onToggleTimer: (TaskItem) -> Unit,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onEdit: (TaskItem) -> Unit,
) {
    val elapsed = TimeTracking.totalAt(task.trackedMillis, task.timerStartedAt, nowMillis)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (task.isSubtask) "SUBTASK" else "TASK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (task.note.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    task.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            objective?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Objective: ${it.title}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (it.tags.isNotEmpty()) {
                    Text(
                        it.tags.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            parentTitle?.let {
                Text(
                    "Parent task: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (isActive) "Working • ${TimeTracking.format(elapsed)}"
                else "Tracked • ${TimeTracking.format(elapsed)}",
                style = MaterialTheme.typography.labelLarge,
                color = if (isActive) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { onToggleTimer(task) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isActive) "Stop working" else "Start working")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { onSetDone(task, true) }) { Text("Mark done") }
                TextButton(onClick = { onEdit(task) }) { Text("Edit") }
            }
        }
    }
}
