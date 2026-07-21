package com.example.todotimer.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.AnalyticsRange
import com.example.todotimer.data.AnalyticsRanges
import com.example.todotimer.data.AnalyticsTime
import com.example.todotimer.data.Objective
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.TimeEntry
import com.example.todotimer.util.TimeTracking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class AnalyticsPreset(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    CUSTOM("Custom"),
}

private data class ActionTime(val task: TaskItem, val millis: Long)
private data class ObjectiveTime(val objective: Objective, val millis: Long)

@Composable
internal fun AnalyticsSection(
    modifier: Modifier,
    objectives: List<Objective>,
    tasks: List<TaskItem>,
    timeEntries: List<TimeEntry>,
    nowMillis: Long,
    onDownloadCsv: (AnalyticsRange) -> Unit,
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val today = remember(nowMillis) { Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate() }
    var presetName by rememberSaveable { mutableStateOf(AnalyticsPreset.TODAY.name) }
    var customStartEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var customEndEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val preset = AnalyticsPreset.valueOf(presetName)
    val customStart = LocalDate.ofEpochDay(customStartEpochDay)
    val customEnd = LocalDate.ofEpochDay(customEndEpochDay)
    val range = remember(preset, customStartEpochDay, customEndEpochDay, nowMillis) {
        when (preset) {
            AnalyticsPreset.TODAY -> AnalyticsRanges.today(nowMillis, zoneId)
            AnalyticsPreset.THIS_WEEK -> AnalyticsRanges.thisWeek(nowMillis, zoneId)
            AnalyticsPreset.THIS_MONTH -> AnalyticsRanges.thisMonth(nowMillis, zoneId)
            AnalyticsPreset.CUSTOM -> AnalyticsRanges.custom(customStart, customEnd, zoneId)
        }
    }
    val objectivesById = remember(objectives) { objectives.associateBy { it.id } }
    val actionTimes = remember(tasks, timeEntries, range, nowMillis) {
        tasks.map { task ->
            ActionTime(
                task = task,
                millis = AnalyticsTime.loggedMillis(task, timeEntries, range, nowMillis),
            )
        }.sortedByDescending { it.millis }
    }
    val loggedActions = actionTimes.filter { it.millis > 0L }
    val totalMillis = actionTimes.sumOf { it.millis }
    val objectiveTimes = objectives.map { objective ->
        ObjectiveTime(
            objective = objective,
            millis = actionTimes.filter { it.task.objectiveId == objective.id }.sumOf { it.millis },
        )
    }.filter { it.millis > 0L }.sortedByDescending { it.millis }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "analytics_range") {
            DateRangeSelector(
                preset = preset,
                range = range,
                customStart = customStart,
                customEnd = customEnd,
                onPresetChange = { presetName = it.name },
                onPickStart = {
                    openDatePicker(context, customStart) { selected ->
                        customStartEpochDay = selected.toEpochDay()
                        if (selected.isAfter(customEnd)) customEndEpochDay = selected.toEpochDay()
                    }
                },
                onPickEnd = {
                    openDatePicker(context, customEnd) { selected ->
                        customEndEpochDay = selected.toEpochDay()
                        if (selected.isBefore(customStart)) customStartEpochDay = selected.toEpochDay()
                    }
                },
            )
        }
        item(key = "analytics_summary") {
            AnalyticsSummary(
                range = range,
                totalMillis = totalMillis,
                loggedActions = loggedActions.size,
                totalActions = tasks.size,
                onDownloadCsv = { onDownloadCsv(range) },
            )
        }
        item(key = "objective_time_heading") {
            Text(
                "Time by objective",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (objectiveTimes.isEmpty()) {
            item(key = "no_objective_time") { NoTimeLogged(range.label) }
        } else {
            items(objectiveTimes, key = { "objective_time_${it.objective.id}" }) { record ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.objective.title, fontWeight = FontWeight.SemiBold)
                            if (record.objective.tags.isNotEmpty()) {
                                Text(
                                    record.objective.tags.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Text(
                            TimeTracking.format(record.millis),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        item(key = "action_time_heading") {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "Action-wise time",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Each task and subtask is reported for the selected dates.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (loggedActions.isEmpty()) {
            item(key = "no_action_time") { NoTimeLogged(range.label) }
        } else {
            items(loggedActions, key = { "action_time_${it.task.id}" }) { record ->
                AnalyticsActionRow(
                    record = record,
                    objective = objectivesById[record.task.objectiveId],
                )
            }
        }
    }
}

@Composable
private fun DateRangeSelector(
    preset: AnalyticsPreset,
    range: AnalyticsRange,
    customStart: LocalDate,
    customEnd: LocalDate,
    onPresetChange: (AnalyticsPreset) -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Date range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AnalyticsPreset.entries.forEach { option ->
                    FilterChip(
                        selected = preset == option,
                        onClick = { onPresetChange(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            if (preset == AnalyticsPreset.CUSTOM) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = onPickStart, modifier = Modifier.weight(1f)) {
                        Text(customStart.format(shortDateFormatter))
                    }
                    OutlinedButton(onClick = onPickEnd, modifier = Modifier.weight(1f)) {
                        Text(customEnd.format(shortDateFormatter))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatRange(range),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AnalyticsSummary(
    range: AnalyticsRange,
    totalMillis: Long,
    loggedActions: Int,
    totalActions: Int,
    onDownloadCsv: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("TIME LOGGED • ${range.label.uppercase()}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                TimeTracking.format(totalMillis),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$loggedActions of $totalActions actions logged time in this range",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDownloadCsv,
                enabled = loggedActions > 0,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download filtered CSV") }
            Spacer(Modifier.height(4.dp))
            Text(
                "Exports only time sessions that overlap the selected dates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun AnalyticsActionRow(record: ActionTime, objective: Objective?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(record.task.title, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(if (record.task.isSubtask) "Subtask" else "Task")
                        objective?.let { append(" • ${it.title}") }
                        append(if (record.task.isDone) " • Done" else " • Open")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                TimeTracking.format(record.millis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NoTimeLogged(rangeLabel: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            "No time was logged for $rangeLabel. Choose another range or start working on an action.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openDatePicker(
    context: android.content.Context,
    initial: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun formatRange(range: AnalyticsRange): String {
    val zoneId = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(range.startMillis).atZone(zoneId).toLocalDate()
    val end = Instant.ofEpochMilli(range.endExclusiveMillis - 1L).atZone(zoneId).toLocalDate()
    return if (start == end) start.format(shortDateFormatter)
    else "${start.format(shortDateFormatter)} – ${end.format(shortDateFormatter)}"
}
