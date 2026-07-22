package com.example.todotimer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ObjectiveVisibility
import com.example.todotimer.data.TaskItem

@Composable
internal fun ObjectiveCard(
    objective: Objective,
    tasks: List<TaskItem>,
    allItems: List<TaskItem>,
    nowMillis: Long,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onToggleTimer: (TaskItem) -> Unit,
    onAddTask: () -> Unit,
    onAddSubtask: (TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onEditObjective: () -> Unit,
    hasReusableTasks: Boolean,
    onOpenReusableTasks: () -> Unit,
    showClosed: Boolean,
) {
    val completedTasks = tasks.count { it.isDone }
    val visibleTasks = ObjectiveVisibility.visibleActions(tasks, showClosed)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "OBJECTIVE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                objective.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (objective.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    objective.tags.forEach { tag ->
                        ObjectiveTagChip(tag)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tasks.isEmpty()) {
                Text(
                    "No tasks yet. Add the first step toward this objective.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    progress = { completedTasks.toFloat() / tasks.size.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$completedTasks of ${tasks.size} tasks complete",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    TextButton(onClick = onAddTask) { Text("Add task") }
                    TextButton(onClick = onOpenReusableTasks) {
                        Text(if (hasReusableTasks) "Reusable" else "Templates")
                    }
                }
                TextButton(onClick = onEditObjective) { Text("Edit objective") }
            }

            visibleTasks.forEach { task ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                val subtasks = allItems.filter { it.parentTaskId == task.id }
                    .sortedWith(compareBy<TaskItem> { it.isDone }.thenBy { it.createdAt })
                TaskCard(
                    task = task,
                    subtasks = subtasks,
                    nowMillis = nowMillis,
                    onSetDone = onSetDone,
                    onToggleTimer = onToggleTimer,
                    onAddSubtask = { onAddSubtask(task) },
                    onEdit = onEditTask,
                    showClosed = showClosed,
                )
            }
        }
    }
}

@Composable
private fun ObjectiveTagChip(tag: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = tag,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
