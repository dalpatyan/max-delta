package com.example.todotimer.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.Routine
import com.example.todotimer.data.RoutineFrequency
import com.example.todotimer.data.RoutineOrdering
import com.example.todotimer.data.TaskItem
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun RoutineEditorDialog(
    existing: Routine?,
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    onDismiss: () -> Unit,
    onSave: (String, RoutineFrequency, Long, Set<DayOfWeek>, List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val initialDateTime = remember(existing?.id) {
        Instant.ofEpochMilli(existing?.nextRunAt ?: System.currentTimeMillis() + 3_600_000L)
            .atZone(zoneId)
    }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var frequency by remember(existing?.id) {
        mutableStateOf(existing?.frequency ?: RoutineFrequency.DAILY)
    }
    var scheduledDate by remember(existing?.id) { mutableStateOf(initialDateTime.toLocalDate()) }
    var scheduledTime by remember(existing?.id) {
        mutableStateOf(initialDateTime.toLocalTime().withSecond(0).withNano(0))
    }
    var customDays by remember(existing?.id) {
        mutableStateOf(
            existing?.customDays
                ?.takeIf { it.isNotEmpty() }
                ?: setOf(initialDateTime.dayOfWeek),
        )
    }
    var selectedTaskIds by remember(existing?.id) {
        mutableStateOf(existing?.taskIds?.filter { id -> tasks.any { it.id == id } }.orEmpty())
    }
    val objectivesById = remember(objectives) { objectives.associateBy { it.id } }
    val tasksById = remember(tasks) { tasks.associateBy { it.id } }
    val availableTasks = remember(tasks, objectivesById) {
        tasks.sortedWith(
            compareBy<TaskItem> { objectivesById[it.objectiveId]?.title.orEmpty() }
                .thenBy { it.parentTaskId != null }
                .thenBy { it.createdAt },
        )
    }
    val scheduledAt = scheduledDate.atTime(scheduledTime).atZone(zoneId).toInstant().toEpochMilli()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create routine" else "Edit routine") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Routine name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text("Frequency", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RoutineFrequency.entries.forEach { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(option.label) },
                        )
                    }
                }
                if (frequency == RoutineFrequency.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Run on",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        customDayOrder.forEach { day ->
                            FilterChip(
                                selected = day in customDays,
                                onClick = {
                                    customDays = if (day in customDays) {
                                        customDays - day
                                    } else {
                                        customDays + day
                                    }
                                },
                                label = {
                                    Text(
                                        day.name.take(3).lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                    )
                                },
                            )
                        }
                    }
                    if (customDays.isEmpty()) {
                        Text(
                            "Choose at least one day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("First scheduled time", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, year, month, day -> scheduledDate = LocalDate.of(year, month + 1, day) },
                                scheduledDate.year,
                                scheduledDate.monthValue - 1,
                                scheduledDate.dayOfMonth,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(scheduledDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, minute -> scheduledTime = LocalTime.of(hour, minute) },
                                scheduledTime.hour,
                                scheduledTime.minute,
                                false,
                            ).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text(scheduledTime.format(DateTimeFormatter.ofPattern("h:mm a"))) }
                }
                Spacer(Modifier.height(16.dp))
                Text("Task order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Use the arrows to set the exact sequence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selectedTaskIds.forEachIndexed { index, taskId ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${index + 1}. ${tasksById[taskId]?.title.orEmpty()}", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                selectedTaskIds = RoutineOrdering.move(selectedTaskIds, index, index - 1)
                            },
                            enabled = index > 0,
                        ) { Text("↑") }
                        TextButton(
                            onClick = {
                                selectedTaskIds = RoutineOrdering.move(selectedTaskIds, index, index + 1)
                            },
                            enabled = index < selectedTaskIds.lastIndex,
                        ) { Text("↓") }
                        TextButton(
                            onClick = { selectedTaskIds = selectedTaskIds - taskId },
                        ) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Choose tasks", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                availableTasks.forEach { task ->
                    val selected = task.id in selectedTaskIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTaskIds = if (selected) selectedTaskIds - task.id
                                else selectedTaskIds + task.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                selectedTaskIds = if (checked) selectedTaskIds + task.id
                                else selectedTaskIds - task.id
                            },
                        )
                        Column {
                            Text(task.title)
                            Text(
                                buildString {
                                    append(objectivesById[task.objectiveId]?.title.orEmpty())
                                    if (task.isSubtask) append(" • Subtask")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name.trim(), frequency, scheduledAt, customDays, selectedTaskIds)
                },
                enabled = name.isNotBlank() &&
                    selectedTaskIds.isNotEmpty() &&
                    (frequency != RoutineFrequency.CUSTOM || customDays.isNotEmpty()),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val customDayOrder = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)
