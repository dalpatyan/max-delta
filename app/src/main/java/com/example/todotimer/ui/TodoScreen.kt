package com.example.todotimer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import com.example.todotimer.MainActivity
import com.example.todotimer.data.AnalyticsRange
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ObjectiveVisibility
import com.example.todotimer.data.ReusableTask
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.TimeEntry
import com.example.todotimer.util.TimeTracking
import kotlinx.coroutines.delay

private enum class AppSection(val label: String, val symbol: String) {
    OBJECTIVES("Objectives", "◎"),
    ONGOING("Ongoing", "▶"),
    ANALYTICS("Analytics", "Σ"),
}

private data class TaskEditorTarget(
    val task: TaskItem?,
    val objectiveId: Long,
    val parentTaskId: Long?,
)

private data class ObjectiveEditorTarget(val objective: Objective?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoScreen(
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    timeEntries: List<TimeEntry>,
    reusableTasks: List<ReusableTask>,
    launchAction: String?,
    onLaunchActionConsumed: () -> Unit,
    notificationAllowed: Boolean,
    exactAlarmAllowed: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onSaveObjective: (Long?, String, List<String>) -> Unit,
    onSaveTask: (Long?, Long, Long?, String, Long?) -> Unit,
    onSetDone: (TaskItem, Boolean) -> Unit,
    onToggleTimer: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onDeleteObjective: (Objective) -> Unit,
    onSaveReusableTask: (TaskItem) -> Unit,
    onUseReusableTask: (ReusableTask, Objective) -> Unit,
    onDeleteReusableTask: (ReusableTask) -> Unit,
    onDownloadAnalytics: (AnalyticsRange) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var sectionName by rememberSaveable { mutableStateOf(AppSection.OBJECTIVES.name) }
    val currentSection = AppSection.valueOf(sectionName)
    var taskEditorTarget by remember { mutableStateOf<TaskEditorTarget?>(null) }
    var objectiveEditorTarget by remember { mutableStateOf<ObjectiveEditorTarget?>(null) }
    var taskDeleteTarget by remember { mutableStateOf<TaskItem?>(null) }
    var objectiveDeleteTarget by remember { mutableStateOf<Objective?>(null) }
    var reusableObjective by remember { mutableStateOf<Objective?>(null) }
    var quickAddVisible by remember { mutableStateOf(false) }
    val nowMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val completedObjectives = remember(objectives, tasks) {
        objectives.count { ObjectiveVisibility.isClosed(it.id, tasks) }
    }
    val openActions = tasks.count { !it.isDone }
    val activeActions = tasks.count { it.isTimerRunning }
    val totalLogged = tasks.sumOf {
        TimeTracking.totalAt(it.trackedMillis, it.timerStartedAt, nowMillis)
    }
    val subtitle = when (currentSection) {
        AppSection.OBJECTIVES ->
            "${objectives.size - completedObjectives} open • $completedObjectives closed"
        AppSection.ONGOING -> "$activeActions active • $openActions unfinished"
        AppSection.ANALYTICS -> TimeTracking.format(totalLogged) + " logged"
    }

    androidx.compose.runtime.LaunchedEffect(launchAction) {
        when (launchAction) {
            MainActivity.ACTION_QUICK_ADD_TASK -> {
                sectionName = AppSection.OBJECTIVES.name
                quickAddVisible = true
                onLaunchActionConsumed()
            }
            MainActivity.ACTION_OPEN_ONGOING -> {
                sectionName = AppSection.ONGOING.name
                onLaunchActionConsumed()
            }
        }
    }

    Scaffold(
        modifier = Modifier.pointerInput(focusManager, keyboardController) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Max Δ", fontWeight = FontWeight.Bold)
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = section == currentSection,
                        onClick = { sectionName = section.name },
                        icon = { Text(section.symbol) },
                        label = { Text(section.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentSection == AppSection.OBJECTIVES) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.imePadding(),
                    onClick = { objectiveEditorTarget = ObjectiveEditorTarget(objective = null) },
                ) { Text("Add objective") }
            }
        },
    ) { innerPadding ->
        when (currentSection) {
            AppSection.OBJECTIVES -> ObjectiveListSection(
                modifier = Modifier.padding(innerPadding),
                objectives = objectives,
                tasks = tasks,
                reusableTasks = reusableTasks,
                nowMillis = nowMillis,
                notificationAllowed = notificationAllowed,
                exactAlarmAllowed = exactAlarmAllowed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onSetDone = onSetDone,
                onToggleTimer = onToggleTimer,
                onAddTask = { objective ->
                    taskEditorTarget = TaskEditorTarget(
                        task = null,
                        objectiveId = objective.id,
                        parentTaskId = null,
                    )
                },
                onAddSubtask = { objective, task ->
                    taskEditorTarget = TaskEditorTarget(
                        task = null,
                        objectiveId = objective.id,
                        parentTaskId = task.id,
                    )
                },
                onEditTask = { item ->
                    taskEditorTarget = TaskEditorTarget(
                        task = item,
                        objectiveId = item.objectiveId,
                        parentTaskId = item.parentTaskId,
                    )
                },
                onEditObjective = { objective ->
                    objectiveEditorTarget = ObjectiveEditorTarget(objective)
                },
                onOpenReusableTasks = { objective -> reusableObjective = objective },
            )

            AppSection.ONGOING -> OngoingSection(
                modifier = Modifier.padding(innerPadding),
                objectives = objectives,
                tasks = tasks,
                nowMillis = nowMillis,
                onToggleTimer = onToggleTimer,
                onSetDone = onSetDone,
                onEdit = { item ->
                    taskEditorTarget = TaskEditorTarget(
                        task = item,
                        objectiveId = item.objectiveId,
                        parentTaskId = item.parentTaskId,
                    )
                },
            )

            AppSection.ANALYTICS -> AnalyticsSection(
                modifier = Modifier.padding(innerPadding),
                objectives = objectives,
                tasks = tasks,
                timeEntries = timeEntries,
                nowMillis = nowMillis,
                onDownloadCsv = onDownloadAnalytics,
            )
        }
    }

    objectiveEditorTarget?.let { target ->
        ObjectiveEditorDialog(
            existing = target.objective,
            onDismiss = { objectiveEditorTarget = null },
            onSave = { title, tags ->
                onSaveObjective(target.objective?.id, title, tags)
                objectiveEditorTarget = null
            },
            onDelete = target.objective?.let { objective ->
                {
                    objectiveEditorTarget = null
                    objectiveDeleteTarget = objective
                }
            },
        )
    }

    taskEditorTarget?.let { target ->
        TaskEditorDialog(
            existing = target.task,
            isSubtask = target.parentTaskId != null,
            onDismiss = { taskEditorTarget = null },
            onSave = { title, reminderAt ->
                onSaveTask(
                    target.task?.id,
                    target.objectiveId,
                    target.parentTaskId,
                    title,
                    reminderAt,
                )
                taskEditorTarget = null
            },
            onDelete = target.task?.let { task ->
                {
                    taskEditorTarget = null
                    taskDeleteTarget = task
                }
            },
            onSaveReusable = target.task
                ?.takeIf { target.parentTaskId == null }
                ?.let { task ->
                    {
                        onSaveReusableTask(task)
                        taskEditorTarget = null
                    }
                },
        )
    }

    reusableObjective?.let { objective ->
        ReusableTaskDialog(
            objective = objective,
            reusableTasks = reusableTasks,
            onDismiss = { reusableObjective = null },
            onUse = { reusableTask ->
                onUseReusableTask(reusableTask, objective)
                reusableObjective = null
            },
            onDelete = onDeleteReusableTask,
        )
    }

    if (quickAddVisible) {
        QuickAddTaskDialog(
            objectives = objectives,
            onDismiss = { quickAddVisible = false },
            onSave = { objective, title ->
                onSaveTask(null, objective.id, null, title, null)
                quickAddVisible = false
            },
        )
    }

    taskDeleteTarget?.let { task ->
        AlertDialog(
            onDismissRequest = { taskDeleteTarget = null },
            title = { Text("Delete " + if (task.isSubtask) "subtask?" else "task?") },
            text = {
                Text(
                    if (task.isSubtask) "This cannot be undone."
                    else "This task and all its subtasks will be deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTask(task)
                        taskDeleteTarget = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { taskDeleteTarget = null }) { Text("Cancel") }
            },
        )
    }

    objectiveDeleteTarget?.let { objective ->
        AlertDialog(
            onDismissRequest = { objectiveDeleteTarget = null },
            title = { Text("Delete objective?") },
            text = {
                Text("All tasks, subtasks, reminders, and tracked time in this objective will be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteObjective(objective)
                        objectiveDeleteTarget = null
                    },
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { objectiveDeleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}
