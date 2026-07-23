package com.example.todotimer.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.TimeEntry
import com.example.todotimer.util.TimeTracking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class TimeEditTarget(
    val entry: TimeEntry?,
    val runningTimer: Boolean,
    val startedAt: Long,
    val endedAt: Long?,
)

@Composable
internal fun TimeLogDialog(
    task: TaskItem,
    timeEntries: List<TimeEntry>,
    onDismiss: () -> Unit,
    onUpdateRunningStart: (Long) -> Unit,
    onSaveEntry: (Long?, Long, Long) -> Unit,
    onDeleteEntry: (TimeEntry) -> Unit,
) {
    var editorTarget by remember(task.id) { mutableStateOf<TimeEditTarget?>(null) }
    var deleteTarget by remember(task.id) { mutableStateOf<TimeEntry?>(null) }
    val entries = remember(timeEntries, task.id) {
        timeEntries.filter { it.taskId == task.id }.sortedByDescending { it.startedAt }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time log • ${task.title}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 560.dp)) {
                task.timerStartedAt?.let { startedAt ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "TIMER RUNNING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Started ${formatTimeLogDateTime(startedAt)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(
                                onClick = {
                                    editorTarget = TimeEditTarget(
                                        entry = null,
                                        runningTimer = true,
                                        startedAt = startedAt,
                                        endedAt = null,
                                    )
                                },
                            ) { Text("Edit start time") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                OutlinedButton(
                    onClick = {
                        val now = System.currentTimeMillis()
                        editorTarget = TimeEditTarget(
                            entry = null,
                            runningTimer = false,
                            startedAt = now - DEFAULT_MANUAL_ENTRY_MILLIS,
                            endedAt = now,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add time manually") }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Completed sessions",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (entries.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No completed sessions yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(entries, key = { "time_entry_${it.id}" }) { entry ->
                            TimeEntryRow(
                                entry = entry,
                                onEdit = {
                                    editorTarget = TimeEditTarget(
                                        entry = entry,
                                        runningTimer = false,
                                        startedAt = entry.startedAt,
                                        endedAt = entry.endedAt,
                                    )
                                },
                                onDelete = { deleteTarget = entry },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )

    editorTarget?.let { target ->
        TimeEntryEditorDialog(
            target = target,
            onDismiss = { editorTarget = null },
            onSave = { startedAt, endedAt ->
                if (target.runningTimer) {
                    onUpdateRunningStart(startedAt)
                } else {
                    onSaveEntry(
                        target.entry?.id,
                        startedAt,
                        checkNotNull(endedAt),
                    )
                }
                editorTarget = null
            },
        )
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete time entry?") },
            text = {
                Text(
                    "${formatTimeLogDateTime(entry.startedAt)} • " +
                        TimeTracking.format(entry.endedAt - entry.startedAt),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(entry)
                        deleteTarget = null
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TimeEntryRow(
    entry: TimeEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            formatTimeLogDateTime(entry.startedAt),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${formatClockTime(entry.startedAt)}–${formatClockTime(entry.endedAt)} • " +
                TimeTracking.format(entry.endedAt - entry.startedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun TimeEntryEditorDialog(
    target: TimeEditTarget,
    onDismiss: () -> Unit,
    onSave: (Long, Long?) -> Unit,
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val initialStart = remember(target) { Instant.ofEpochMilli(target.startedAt).atZone(zoneId) }
    val initialEnd = remember(target) {
        Instant.ofEpochMilli(target.endedAt ?: System.currentTimeMillis()).atZone(zoneId)
    }
    var startDate by remember(target) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(target) {
        mutableStateOf(initialStart.toLocalTime().withSecond(0).withNano(0))
    }
    var endDate by remember(target) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(target) {
        mutableStateOf(initialEnd.toLocalTime().withSecond(0).withNano(0))
    }
    val startedAt = startDate.atTime(startTime).atZone(zoneId).toInstant().toEpochMilli()
    val endedAt = if (target.runningTimer) {
        null
    } else {
        endDate.atTime(endTime).atZone(zoneId).toInstant().toEpochMilli()
    }
    val nowMillis = System.currentTimeMillis()
    val valid = if (target.runningTimer) {
        startedAt <= nowMillis
    } else {
        endedAt != null && endedAt > startedAt && endedAt <= nowMillis
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (target.runningTimer) "Edit timer start" else "Edit time entry") },
        text = {
            Column {
                DateTimePickerRow(
                    label = "Start",
                    date = startDate,
                    time = startTime,
                    onPickDate = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                startDate = LocalDate.of(year, month + 1, day)
                            },
                            startDate.year,
                            startDate.monthValue - 1,
                            startDate.dayOfMonth,
                        ).show()
                    },
                    onPickTime = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> startTime = LocalTime.of(hour, minute) },
                            startTime.hour,
                            startTime.minute,
                            false,
                        ).show()
                    },
                )
                if (!target.runningTimer) {
                    Spacer(Modifier.height(12.dp))
                    DateTimePickerRow(
                        label = "End",
                        date = endDate,
                        time = endTime,
                        onPickDate = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    endDate = LocalDate.of(year, month + 1, day)
                                },
                                endDate.year,
                                endDate.monthValue - 1,
                                endDate.dayOfMonth,
                            ).show()
                        },
                        onPickTime = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> endTime = LocalTime.of(hour, minute) },
                                endTime.hour,
                                endTime.minute,
                                false,
                            ).show()
                        },
                    )
                }
                if (!valid) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (target.runningTimer) {
                            "Start time cannot be in the future."
                        } else {
                            "End time must be after start time and cannot be in the future."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(startedAt, endedAt) },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DateTimePickerRow(
    label: String,
    date: LocalDate,
    time: LocalTime,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1f)) {
                Text(date.format(DATE_FORMATTER))
            }
            OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                Text(time.format(TIME_FORMATTER))
            }
        }
    }
}

private fun formatTimeLogDateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy • h:mm a"))

private fun formatClockTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
    .atZone(ZoneId.systemDefault())
    .format(TIME_FORMATTER)

private val DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMM yyyy")
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a")
private const val DEFAULT_MANUAL_ENTRY_MILLIS = 30L * 60L * 1_000L
