package com.example.todotimer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ReusableTask
import com.example.todotimer.data.Routine
import com.example.todotimer.data.RoutineFrequency
import com.example.todotimer.data.RoutineProgress
import com.example.todotimer.data.TaskItem
import com.example.todotimer.data.TimeEntry
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppGraph.repository(application)
    val objectives: StateFlow<List<Objective>> = repository.objectives
    val tasks: StateFlow<List<TaskItem>> = repository.tasks
    val timeEntries: StateFlow<List<TimeEntry>> = repository.timeEntries
    val reusableTasks: StateFlow<List<ReusableTask>> = repository.reusableTasks
    val routines: StateFlow<List<Routine>> = repository.routines
    val routineProgress: StateFlow<RoutineProgress?> = repository.routineProgress

    fun saveTask(
        id: Long?,
        objectiveId: Long,
        parentTaskId: Long?,
        title: String,
        note: String,
        reminderAt: Long?,
        routineIds: Set<Long> = emptySet(),
    ) {
        viewModelScope.launch {
            repository.saveTask(
                id = id,
                objectiveId = objectiveId,
                parentTaskId = parentTaskId,
                title = title,
                note = note,
                reminderAt = reminderAt,
                routineIds = routineIds,
            )
        }
    }

    fun saveObjective(id: Long?, title: String, tags: List<String>) {
        viewModelScope.launch { repository.saveObjective(id, title, tags) }
    }

    fun setDone(item: TaskItem, done: Boolean) {
        viewModelScope.launch { repository.setDone(item.id, done) }
    }

    fun toggleTimer(item: TaskItem) {
        viewModelScope.launch {
            if (item.isTimerRunning) repository.stopTimer(item.id)
            else repository.startTimer(item.id)
        }
    }

    fun delete(item: TaskItem) {
        viewModelScope.launch { repository.delete(item.id) }
    }

    fun deleteObjective(objective: Objective) {
        viewModelScope.launch { repository.deleteObjective(objective.id) }
    }

    fun saveReusableTask(item: TaskItem) {
        viewModelScope.launch { repository.saveReusableTask(item.id) }
    }

    fun useReusableTask(reusableTask: ReusableTask, objective: Objective) {
        viewModelScope.launch { repository.useReusableTask(reusableTask.id, objective.id) }
    }

    fun deleteReusableTask(reusableTask: ReusableTask) {
        viewModelScope.launch { repository.deleteReusableTask(reusableTask.id) }
    }

    fun saveRoutine(
        id: Long?,
        name: String,
        frequency: RoutineFrequency,
        nextRunAt: Long,
        customDays: Set<DayOfWeek>,
        taskIds: List<Long>,
    ) {
        viewModelScope.launch {
            repository.saveRoutine(id, name, frequency, nextRunAt, customDays, taskIds)
        }
    }

    fun updateRunningTimerStart(item: TaskItem, startedAt: Long) {
        viewModelScope.launch { repository.updateRunningTimerStart(item.id, startedAt) }
    }

    fun saveTimeEntry(
        id: Long?,
        taskId: Long,
        startedAt: Long,
        endedAt: Long,
    ) {
        viewModelScope.launch {
            repository.saveTimeEntry(id, taskId, startedAt, endedAt)
        }
    }

    fun deleteTimeEntry(entry: TimeEntry) {
        viewModelScope.launch { repository.deleteTimeEntry(entry) }
    }

    fun setRoutineEnabled(routine: Routine, enabled: Boolean) {
        viewModelScope.launch { repository.setRoutineEnabled(routine.id, enabled) }
    }

    fun startRoutine(progress: RoutineProgress) {
        viewModelScope.launch { repository.startRoutine(progress.runId) }
    }

    suspend fun exportBackup(output: OutputStream, password: CharArray) {
        repository.exportBackup(output, password)
    }

    suspend fun restoreBackup(input: InputStream, password: CharArray) {
        repository.restoreBackup(input, password)
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }
}
