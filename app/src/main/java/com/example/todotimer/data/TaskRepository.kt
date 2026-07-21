package com.example.todotimer.data

import com.example.todotimer.reminder.ReminderScheduler
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

class TaskRepository(
    private val database: TaskDatabase,
    private val reminderScheduler: ReminderScheduler,
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

    init {
        repositoryScope.launch { refresh() }
    }

    suspend fun saveTask(
        id: Long?,
        objectiveId: Long,
        parentTaskId: Long?,
        title: String,
        reminderAt: Long?,
    ) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            val cleanTitle = title.trim()
            require(cleanTitle.isNotEmpty()) { "A title is required" }
            id?.let(reminderScheduler::cancel)
            val savedId = if (id == null) {
                database.insert(
                    objectiveId = objectiveId,
                    title = cleanTitle,
                    parentTaskId = parentTaskId,
                    reminderAt = reminderAt,
                    nowMillis = System.currentTimeMillis(),
                )
            } else {
                database.update(id, cleanTitle, reminderAt, System.currentTimeMillis())
                id
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
            val affectedIds = database.setDone(id, done, System.currentTimeMillis())
            affectedIds.forEach { affectedId ->
                reminderScheduler.cancel(affectedId)
                if (!done) database.findById(affectedId)?.let(reminderScheduler::schedule)
            }
            refresh()
        }
    }

    suspend fun startTimer(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.startTimer(id, System.currentTimeMillis())
            refresh()
        }
    }

    suspend fun stopTimer(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.stopTimer(id, System.currentTimeMillis())
            refresh()
        }
    }

    suspend fun delete(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getItemAndChildIds(id).forEach(reminderScheduler::cancel)
            database.delete(id)
            refresh()
        }
    }

    suspend fun deleteObjective(id: Long) = writeMutex.withLock {
        withContext(Dispatchers.IO) {
            database.getTaskIdsForObjective(id).forEach(reminderScheduler::cancel)
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

    suspend fun refresh() = withContext(Dispatchers.IO) {
        val currentObjectives = database.getObjectives()
        val currentTasks = database.getAll()
        _objectives.value = currentObjectives
        _tasks.value = currentTasks
        _timeEntries.value = database.getTimeEntries()
        _reusableTasks.value = database.getReusableTasks()
        taskStateNotifier.sync(currentTasks)
    }
}
