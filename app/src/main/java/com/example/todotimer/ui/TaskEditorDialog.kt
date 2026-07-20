package com.example.todotimer.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.TaskItem
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun TaskEditorDialog(
    existing: TaskItem?,
    isSubtask: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var title by remember(existing?.id, isSubtask) { mutableStateOf(existing?.title.orEmpty()) }
    var reminderAt by remember(existing?.id, isSubtask) { mutableStateOf(existing?.reminderAt) }
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
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isSubtask) "Subtask" else "Task") },
                    placeholder = { Text("What needs to be done?") },
                    maxLines = 3,
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
                if (onDelete != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), reminderAt) },
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
