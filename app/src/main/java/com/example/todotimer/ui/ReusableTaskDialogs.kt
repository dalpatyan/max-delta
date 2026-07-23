package com.example.todotimer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ReusableTask
import com.example.todotimer.data.Routine

@Composable
internal fun ReusableTaskDialog(
    objective: Objective,
    reusableTasks: List<ReusableTask>,
    onDismiss: () -> Unit,
    onUse: (ReusableTask) -> Unit,
    onDelete: (ReusableTask) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reusable tasks") },
        text = {
            Column {
                Text(
                    "Add a saved task template to ${objective.title}. Reminders and logged time start fresh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (reusableTasks.isEmpty()) {
                    Text("No reusable tasks yet. Edit a task and choose Save as reusable.")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(reusableTasks, key = { "reusable_${it.id}" }) { reusableTask ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(reusableTask.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (reusableTask.subtaskTitles.isEmpty()) "No subtasks"
                                        else "${reusableTask.subtaskTitles.size} subtasks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onDelete(reusableTask) }) { Text("Delete") }
                                Button(onClick = { onUse(reusableTask) }) { Text("Use") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun QuickAddTaskDialog(
    objectives: List<Objective>,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onSave: (Objective, String, String, Set<Long>) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedObjectiveId by remember { mutableStateOf<Long?>(null) }
    var selectedRoutineIds by remember { mutableStateOf(emptySet<Long>()) }
    var objectiveMenuExpanded by remember { mutableStateOf(false) }
    val selectedObjective = objectives.firstOrNull { it.id == selectedObjectiveId }

    LaunchedEffect(objectives, selectedObjectiveId) {
        if (selectedObjectiveId == null || objectives.none { it.id == selectedObjectiveId }) {
            selectedObjectiveId = objectives.firstOrNull()?.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add task") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task") },
                    placeholder = { Text("What needs to be done?") },
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(280) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short note (optional)") },
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                if (objectives.isEmpty()) {
                    Text(
                        "Create an objective in the app before adding a task.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("Objective", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { objectiveMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(selectedObjective?.title ?: "Choose objective") }
                        DropdownMenu(
                            expanded = objectiveMenuExpanded,
                            onDismissRequest = { objectiveMenuExpanded = false },
                        ) {
                            objectives.forEach { objective ->
                                DropdownMenuItem(
                                    text = { Text(objective.title) },
                                    onClick = {
                                        selectedObjectiveId = objective.id
                                        objectiveMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (routines.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Add to routine (optional)", style = MaterialTheme.typography.labelLarge)
                    routines.forEach { routine ->
                        val selected = routine.id in selectedRoutineIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedRoutineIds = if (selected) {
                                        selectedRoutineIds - routine.id
                                    } else {
                                        selectedRoutineIds + routine.id
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    selectedRoutineIds = if (checked) {
                                        selectedRoutineIds + routine.id
                                    } else {
                                        selectedRoutineIds - routine.id
                                    }
                                },
                            )
                            Text(routine.name)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedObjective?.let {
                        onSave(it, title.trim(), note.trim(), selectedRoutineIds)
                    }
                },
                enabled = title.isNotBlank() && selectedObjective != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
