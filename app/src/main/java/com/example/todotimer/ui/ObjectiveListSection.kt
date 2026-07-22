package com.example.todotimer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ObjectiveFilter
import com.example.todotimer.data.ObjectiveSortOrder
import com.example.todotimer.data.ObjectiveSorter
import com.example.todotimer.data.ObjectiveVisibility
import com.example.todotimer.data.ReusableTask
import com.example.todotimer.data.TaskItem
import java.util.Locale

@Composable
internal fun ObjectiveListSection(
    modifier: Modifier,
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    reusableTasks: List<ReusableTask>,
    nowMillis: Long,
    notificationAllowed: Boolean,
    exactAlarmAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onToggleTimer: (TaskItem) -> Unit,
    onAddTask: (Objective) -> Unit,
    onAddSubtask: (Objective, TaskItem) -> Unit,
    onEditTask: (TaskItem) -> Unit,
    onEditObjective: (Objective) -> Unit,
    onOpenReusableTasks: (Objective) -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    var showClosed by rememberSaveable { mutableStateOf(false) }
    var sortOrderName by rememberSaveable {
        mutableStateOf(ObjectiveSortOrder.RECENTLY_ADDED.name)
    }
    val sortOrder = ObjectiveSortOrder.valueOf(sortOrderName)
    val sortedObjectives = remember(objectives, tasks, sortOrder, nowMillis) {
        ObjectiveSorter.sort(objectives, tasks, sortOrder, nowMillis)
    }
    val closedObjectiveCount = remember(objectives, tasks) {
        objectives.count { ObjectiveVisibility.isClosed(it.id, tasks) }
    }
    val closedActionCount = remember(tasks) { tasks.count { it.isDone } }
    val visibleObjectives = remember(sortedObjectives, tasks, showClosed) {
        ObjectiveVisibility.visibleObjectives(sortedObjectives, tasks, showClosed)
    }
    val availableTags = remember(objectives) {
        objectives.flatMap { it.tags }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }
    val filteredObjectives = remember(visibleObjectives, searchQuery, selectedTag) {
        ObjectiveFilter.filter(visibleObjectives, searchQuery, selectedTag)
    }
    val isFiltering = searchQuery.isNotBlank() || selectedTag != null

    LaunchedEffect(availableTags, selectedTag) {
        if (selectedTag != null && availableTags.none { it.equals(selectedTag, ignoreCase = true) }) {
            selectedTag = null
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!notificationAllowed) {
            item(key = "notification_permission") {
                PermissionBanner(
                    title = "Notifications are off",
                    message = "Allow notifications so task reminders can alert you.",
                    actionLabel = "Allow",
                    onAction = onRequestNotificationPermission,
                )
            }
        }
        if (!exactAlarmAllowed) {
            item(key = "alarm_permission") {
                PermissionBanner(
                    title = "Precise reminders are off",
                    message = "Reminders can be delayed by Android until precise alarms are allowed.",
                    actionLabel = "Enable",
                    onAction = onRequestExactAlarmPermission,
                )
            }
        }
        if (sortedObjectives.isEmpty()) {
            item(key = "empty") { EmptyObjectiveState() }
        } else {
            item(key = "objective_filters") {
                ObjectiveFilters(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    tags = availableTags,
                    selectedTag = selectedTag,
                    onTagSelected = { selectedTag = it },
                    showClosed = showClosed,
                    closedObjectiveCount = closedObjectiveCount,
                    closedActionCount = closedActionCount,
                    onShowClosedChange = { showClosed = it },
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrderName = it.name },
                )
            }
            if (isFiltering) {
                item(key = "filter_count") {
                    Text(
                        text = "Showing ${filteredObjectives.size} of ${visibleObjectives.size} " +
                            if (showClosed) "objectives" else "open objectives",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (filteredObjectives.isEmpty()) {
                item(key = "no_matches") {
                    if (visibleObjectives.isEmpty() && closedObjectiveCount > 0 && !showClosed) {
                        NoObjectiveMatches(
                            title = "All objectives are closed",
                            message = "Turn on Show closed to view completed work.",
                        )
                    } else {
                        NoObjectiveMatches(
                            title = "No matching objectives",
                            message = "Change the search text or choose another tag.",
                        )
                    }
                }
            } else {
                items(filteredObjectives, key = { "objective_${it.id}" }) { objective ->
                    val objectiveTasks = tasks.filter {
                        it.objectiveId == objective.id && it.parentTaskId == null
                    }.sortedWith(
                        compareBy<TaskItem> { it.isDone }.thenByDescending { it.createdAt },
                    )
                    ObjectiveCard(
                        objective = objective,
                        tasks = objectiveTasks,
                        allItems = tasks,
                        nowMillis = nowMillis,
                        onSetDone = onSetDone,
                        onToggleTimer = onToggleTimer,
                        onAddTask = { onAddTask(objective) },
                        onAddSubtask = { task -> onAddSubtask(objective, task) },
                        onEditTask = onEditTask,
                        onEditObjective = { onEditObjective(objective) },
                        hasReusableTasks = reusableTasks.isNotEmpty(),
                        onOpenReusableTasks = { onOpenReusableTasks(objective) },
                        showClosed = showClosed,
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectiveFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    tags: List<String>,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    showClosed: Boolean,
    closedObjectiveCount: Int,
    closedActionCount: Int,
    onShowClosedChange: (Boolean) -> Unit,
    sortOrder: ObjectiveSortOrder,
    onSortOrderChange: (ObjectiveSortOrder) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search objectives") },
                placeholder = { Text("Type an objective name") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sort objectives",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ObjectiveSortOrder.entries.forEach { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSortOrderChange(order) },
                        label = { Text(order.label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Show closed", style = MaterialTheme.typography.labelLarge)
                    Text(
                        closedWorkSummary(closedObjectiveCount, closedActionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showClosed,
                    onCheckedChange = onShowClosedChange,
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Filter by tag",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { onTagSelected(null) },
                        label = { Text("All") },
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag.equals(tag, ignoreCase = true),
                            onClick = { onTagSelected(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
    }
}

private fun closedWorkSummary(closedObjectives: Int, closedActions: Int): String {
    if (closedObjectives == 0 && closedActions == 0) return "No closed work"
    val objectiveText = "$closedObjectives objective" + if (closedObjectives == 1) "" else "s"
    val actionText = "$closedActions task/subtask" + if (closedActions == 1) "" else "s"
    return "$objectiveText • $actionText"
}

@Composable
private fun PermissionBanner(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyObjectiveState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Define your first objective", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Break it into tasks, add subtasks when needed, and track the work.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoObjectiveMatches(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
