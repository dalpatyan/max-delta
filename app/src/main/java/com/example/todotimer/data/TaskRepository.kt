package com.example.todotimer.data

import com.example.todotimer.reminder.ReminderScheduler
import com.example.todotimer.reminder.LongTimerReminderScheduler
import com.example.todotimer.reminder.RoutineScheduler
import com.example.todotimer.system.TaskStateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek

class TaskRepository(
    private val database: TaskDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val longTimerReminderScheduler: LongTimerReminderScheduler,
    private val routineScheduler: RoutineScheduler,
    private val taskStateNotifier: TaskStateNotifier,
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val _objectives = MutableStateFlow<List<Objective>>(emptyList())
    val objectives: StateFlow<List<Objective>> = _objectives.asStateFlow()
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()
    private val _timeEntries = MutableStateFlow<List<TimeEntry>>(emptyList())
    val timeEntries: StateFlow<List<TimeEntry>> = _timeEntries.asStateFlow()
    private val _reusableTasks = MutableStateFlow<List<ReusableTask>>(emptyList())
    val reusableTasks: StateFlow<List<ReusableTask>> = _reusableTasks.asStateFlow()
    private val _routines = MutableStateFlow<List<Routine>>(emptyList())
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()
    private val _routineProgress = MutableStateFlow<RoutineProgress?>(null)
    val routineProgress: StateFlow<RoutineProgress?> = _routineProgress.asStateFlow()

    init {
        repositoryScope.launch { refresh() }
    }

    suspend fun saveTask(
        id: Long?,
        objectiveId: Long,
        parentTaskId: Long?,
        title: String,
        note: String,
        reminderAt: Long?,
        routineIds: Set<Long> = emptySet(),
    ) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
            val cleanNote = note.trim()
            require(cleanTitle.isNotEmpty()) { "A title is required" }
            id?.let(reminderScheduler::cancel)
            val savedId = if (id == null) {
                database.insert(
                    objectiveId = objectiveId,
                    title = cleanTitle,
                    note = cleanNote,
                    parentTaskId = parentTaskId,
                    reminderAt = reminderAt,
                    nowMillis = System.currentTimeMillis(),
                )
            } else {
                database.update(id, cleanTitle, cleanNote, reminderAt, System.currentTimeMillis())
                id
            }
            if (id == null && routineIds.isNotEmpty()) {
                database.appendTaskToRoutines(
                    taskId = savedId,
                    routineIds = routineIds,
                    nowMillis = System.currentTimeMillis(),
                )
                database.getRoutines()
                    .filter { it.id in routineIds && it.isEnabled }
                    .forEach(routineScheduler::schedule)
            }
            database.findById(savedId)?.let(reminderScheduler::schedule)
            refresh()
        }
    }

    suspend fun saveObjective(id: Long?, title: String, tags: List<String>) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
            val cleanTags = ObjectiveTags.normalize(tags)
            require(cleanTitle.isNotEmpty()) { "An objective title is required" }
            if (id == null) {
                database.insertObjective(cleanTitle, cleanTags, System.currentTimeMillis())
            } else {
                database.updateObjective(id, cleanTitle, cleanTags, System.currentTimeMillis())
            }
            refresh()
        }
    }

    suspend fun setDone(id: Long, done: Boolean) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val nowMillis = System.currentTimeMillis()
            val affectedIds = database.setDone(id, done, nowMillis)
            if (done) {
                database.advanceActiveRoutinesForTasks(affectedIds, nowMillis)
            }
            affectedIds.forEach { affectedId ->
                reminderScheduler.cancel(affectedId)
                longTimerReminderScheduler.cancel(affectedId)
                if (!done) database.findById(affectedId)?.let(reminderScheduler::schedule)
            }
            refresh()
        }
    }

    suspend fun startTimer(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getAll()
                .filter { it.isTimerRunning }
                .forEach { longTimerReminderScheduler.cancel(it.id) }
            longTimerReminderScheduler.cancel(id)
            database.startTimer(id, System.currentTimeMillis())
            database.findById(id)?.let(longTimerReminderScheduler::schedule)
            refresh()
        }
    }

    suspend fun stopTimer(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            longTimerReminderScheduler.cancel(id)
            database.stopTimer(id, System.currentTimeMillis())
            refresh()
        }
    }

    suspend fun delete(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getItemAndChildIds(id).forEach {
                reminderScheduler.cancel(it)
                longTimerReminderScheduler.cancel(it)
            }
            database.delete(id)
            refresh()
        }
    }

    suspend fun deleteObjective(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getTaskIdsForObjective(id).forEach {
                reminderScheduler.cancel(it)
                longTimerReminderScheduler.cancel(it)
            }
            database.deleteObjective(id)
            refresh()
        }
    }

    suspend fun saveReusableTask(taskId: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.saveReusableTask(taskId, System.currentTimeMillis())
            refresh()
        }
    }

    suspend fun useReusableTask(reusableTaskId: Long, objectiveId: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.instantiateReusableTask(
                reusableTaskId = reusableTaskId,
                objectiveId = objectiveId,
                nowMillis = System.currentTimeMillis(),
            )
            refresh()
        }
    }

    suspend fun deleteReusableTask(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.deleteReusableTask(id)
            refresh()
        }
    }

    suspend fun saveRoutine(
        id: Long?,
        name: String,
        frequency: RoutineFrequency,
        nextRunAt: Long,
        customDays: Set<DayOfWeek>,
        taskIds: List<Long>,
    ) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty()) { "A routine name is required" }
            require(taskIds.isNotEmpty()) { "Choose at least one task" }
            require(frequency != RoutineFrequency.CUSTOM || customDays.isNotEmpty()) {
                "Choose at least one day"
            }
            id?.let(routineScheduler::cancel)
            val savedId = database.saveRoutine(
                id = id,
                name = cleanName,
                frequency = frequency,
                nextRunAt = frequency.alignFirstOccurrence(nextRunAt, customDays),
                customDays = customDays,
                taskIds = taskIds,
                nowMillis = System.currentTimeMillis(),
            )
            database.getRoutines().firstOrNull { it.id == savedId }?.let(routineScheduler::schedule)
            refresh()
        }
    }

    suspend fun setRoutineEnabled(routineId: Long, enabled: Boolean) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            routineScheduler.cancel(routineId)
            database.setRoutineEnabled(routineId, enabled, System.currentTimeMillis())
            if (enabled) {
                database.getRoutines().firstOrNull { it.id == routineId }
                    ?.let(routineScheduler::schedule)
            }
            refresh()
        }
    }

    suspend fun activateRoutine(routineId: Long, scheduledAt: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getAll()
                .filter { it.isTimerRunning }
                .forEach { longTimerReminderScheduler.cancel(it.id) }
            database.activateRoutine(routineId, scheduledAt, System.currentTimeMillis())
            database.getRoutines().firstOrNull { it.id == routineId }
                ?.let(routineScheduler::schedule)
            database.getAll()
                .filter { it.isTimerRunning }
                .forEach(longTimerReminderScheduler::schedule)
            refresh()
        }
    }

    suspend fun startRoutine(runId: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getAll()
                .filter { it.isTimerRunning }
                .forEach { longTimerReminderScheduler.cancel(it.id) }
            database.startRoutineRun(runId, System.currentTimeMillis())
            database.getAll()
                .firstOrNull { it.isTimerRunning }
                ?.let(longTimerReminderScheduler::schedule)
            refresh()
        }
    }

    suspend fun updateRunningTimerStart(taskId: Long, startedAt: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val nowMillis = System.currentTimeMillis()
            require(startedAt <= nowMillis) { "Start time cannot be in the future" }
            longTimerReminderScheduler.cancel(taskId)
            database.updateRunningTimerStart(taskId, startedAt, nowMillis)
                ?.let(longTimerReminderScheduler::schedule)
            refresh()
        }
    }

    suspend fun saveTimeEntry(
        id: Long?,
        taskId: Long,
        startedAt: Long,
        endedAt: Long,
    ) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val nowMillis = System.currentTimeMillis()
            require(startedAt < endedAt) { "End time must be after start time" }
            require(endedAt <= nowMillis) { "Logged time cannot end in the future" }
            database.saveTimeEntry(id, taskId, startedAt, endedAt, nowMillis)
            refresh()
        }
    }

    suspend fun deleteTimeEntry(entry: TimeEntry) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.deleteTimeEntry(entry.id, entry.taskId, System.currentTimeMillis())
            refresh()
        }
    }

    suspend fun claimLongTimerReminder(
        taskId: Long,
        expectedStartedAt: Long,
    ): TaskItem? = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val task = database.claimLongTimerReminder(
                taskId = taskId,
                expectedStartedAt = expectedStartedAt,
                nowMillis = System.currentTimeMillis(),
            )
            if (task != null) refresh()
            task
        }
    }

    suspend fun exportBackup(output: OutputStream, password: CharArray) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            require(password.size >= MIN_BACKUP_PASSWORD_LENGTH) {
                "Use a password with at least $MIN_BACKUP_PASSWORD_LENGTH characters."
            }
            database.exportEncryptedBackup(output, password)
        }
    }

    suspend fun restoreBackup(input: InputStream, password: CharArray) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            require(password.size >= MIN_BACKUP_PASSWORD_LENGTH) {
                "Enter the backup password."
            }
            val previousTasks = database.getAll()
            val previousRoutines = database.getRoutines()
            cancelSchedules(previousTasks, previousRoutines)

            try {
                database.restoreEncryptedBackup(input, password)
            } catch (error: Throwable) {
                scheduleDatabaseState()
                refresh()
                throw error
            }

            scheduleDatabaseState()
            refresh()
        }
    }

    private fun cancelSchedules(tasks: List<TaskItem>, routines: List<Routine>) {
        tasks.forEach { task ->
            reminderScheduler.cancel(task.id)
            longTimerReminderScheduler.cancel(task.id)
        }
        routines.forEach { routine -> routineScheduler.cancel(routine.id) }
    }

    private fun scheduleDatabaseState() {
        database.getAll().forEach { task ->
            reminderScheduler.schedule(task)
            if (task.isTimerRunning) longTimerReminderScheduler.schedule(task)
        }
        database.getRoutines()
            .filter { it.isEnabled }
            .forEach(routineScheduler::schedule)
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val currentObjectives = database.getObjectives()
        val currentTasks = database.getAll()
        _objectives.value = currentObjectives
        _tasks.value = currentTasks
        _timeEntries.value = database.getTimeEntries()
        _reusableTasks.value = database.getReusableTasks()
        _routines.value = database.getRoutines()
        val currentRoutineProgress = database.getActiveRoutineProgress()
        _routineProgress.value = currentRoutineProgress
        taskStateNotifier.sync(
            tasks = currentTasks,
            routineProgress = currentRoutineProgress,
            readyRoutineProgress = database.getReadyRoutineProgress(),
        )
    }

    private companion object {
        const val MIN_BACKUP_PASSWORD_LENGTH = 8
    }
}
