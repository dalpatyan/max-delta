package com.example.todotimer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.todotimer.data.Objective
import com.example.todotimer.data.TaskItem
import com.example.todotimer.di.AppGraph
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppGraph.repository(application)
    val objectives: StateFlow<List<Objective>> = repository.objectives
    val tasks: StateFlow<List<TaskItem>> = repository.tasks

    fun saveTask(
        id: Long?,
        objectiveId: Long,
        parentTaskId: Long?,
        title: String,
        reminderAt: Long?,
    ) {
        viewModelScope.launch {
            repository.saveTask(id, objectiveId, parentTaskId, title, reminderAt)
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

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }
}
