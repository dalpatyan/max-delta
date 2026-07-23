package com.example.todotimer.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.Routine
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun TaskEditorDialog(
    existing: TaskItem?,
    isSubtask: Boolean,
    routines: List<Routine>,
    onDismiss: () -> Unit,
    onSave: (String, String, Long?, Set<Long>) -> Unit,
    onDelete: (() -> Unit)?,
    onSaveReusable: (() -> Unit)? = null,
    onManageTime: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var title by remember(existing?.id, isSubtask) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(existing?.id, isSubtask) { mutableStateOf(existing?.note.orEmpty()) }
    var reminderAt by remember(existing?.id, isSubtask) { mutableStateOf(existing?.reminderAt) }
    var selectedRoutineIds by remember(existing?.id, isSubtask) {
        mutableStateOf(emptySet<Long>())
    }
    val reminderIsValid = reminderAt == null || reminderAt!! > System.currentTimeMillis()
    val dialogTitle = when {
        existing != null && isSubtask -> "Edit subtask"
        existing != null -> "Edit task"
        isSubtask -> "Add subtask"
        else -> "Add task"
    }

    fun openDateTimePicker() {
        val initial = Instant.ofEpochMilli(reminderAt ?: System.currentTimeMillis() + 3_600_000L)
            .atZone(ZoneId.systemDefault())
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        reminderAt = ZonedDateTime.of(
                            year,
                            month + 1,
                            day,
                            hour,
                            minute,
                            0,
                            0,
                            ZoneId.systemDefault(),
                        ).toInstant().toEpochMilli()
                    },
                    initial.hour,
                    initial.minute,
                    false,
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth,
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1_000L
        }.show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
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
                    label = { Text(if (isSubtask) "Subtask" else "Task") },
                    placeholder = { Text("What needs to be done?") },
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Short note (optional)") },
                    placeholder = { Text("Context, link, or next step") },
                    supportingText = { Text("${note.length}/$MAX_NOTE_LENGTH") },
                    minLines = 2,
                    maxLines = 4,
                )
                Spacer(Modifier.height(16.dp))
                Text("Reminder", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = ::openDateTimePicker,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        reminderAt?.let(::formatDateTime) ?: "Choose date and time",
                    )
                }
                if (reminderAt != null) {
                    TextButton(onClick = { reminderAt = null }) { Text("Clear reminder") }
                }
                if (!reminderIsValid) {
                    Text(
                        "Choose a future date and time.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (existing == null && routines.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Add to routine (optional)",
                        style = MaterialTheme.typography.labelLarge,
                    )
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
                if (onManageTime != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onManageTime) {
                        Text("Edit time log")
                    }
                }
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                if (onSaveReusable != null) {
                    TextButton(onClick = onSaveReusable) {
                        Text("Save task and subtasks as reusable")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim(), note.trim(), reminderAt, selectedRoutineIds)
                },
                enabled = title.isNotBlank() && reminderIsValid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatDateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy • h:mm a"))

private const val MAX_NOTE_LENGTH = 280
