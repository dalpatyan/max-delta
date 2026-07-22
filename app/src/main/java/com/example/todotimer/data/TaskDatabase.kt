package com.example.todotimer.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.todotimer.util.TimeTracking

class TaskDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        createObjectivesTable(db)
        db.execSQL(
            """
            CREATE TABLE $TABLE_TASKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_OBJECTIVE_ID INTEGER NOT NULL REFERENCES $TABLE_OBJECTIVES($COL_ID) ON DELETE CASCADE,
                $COL_PARENT_ID INTEGER REFERENCES $TABLE_TASKS($COL_ID) ON DELETE CASCADE,
                $COL_TITLE TEXT NOT NULL,
                $COL_REMINDER_AT INTEGER,
                $COL_IS_DONE INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_COMPLETED_AT INTEGER,
                $COL_TRACKED_MILLIS INTEGER NOT NULL DEFAULT 0,
                $COL_TIMER_STARTED_AT INTEGER,
                $COL_TIMER_ROUTINE_RUN_ID INTEGER,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        createTaskIndexes(db)
        createReusableTasksTable(db)
        createRoutineTables(db)
        createTimeEntriesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateTasksIntoObjective(db)
        if (oldVersion < 3) addObjectiveTagsColumnIfMissing(db)
        if (oldVersion < 4) addUpdateTrackingAndTimeEntries(db)
        if (oldVersion < 5) createReusableTasksTable(db)
        if (oldVersion < 6) addRoutinesAndAttribution(db)
    }

    fun getObjectives(): List<Objective> = readableDatabase.query(
        TABLE_OBJECTIVES,
        OBJECTIVE_COLUMNS,
        null,
        null,
        null,
        null,
        "$COL_CREATED_AT DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Objective(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        tags = ObjectiveTags.decode(
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_TAGS)),
                        ),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
                        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_UPDATED_AT)),
                    ),
                )
            }
        }
    }

    fun insertObjective(title: String, tags: List<String>, nowMillis: Long): Long {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TAGS, ObjectiveTags.encode(tags))
            put(COL_CREATED_AT, nowMillis)
            put(COL_UPDATED_AT, nowMillis)
        }
        return writableDatabase.insertOrThrow(TABLE_OBJECTIVES, null, values)
    }

    fun updateObjective(id: Long, title: String, tags: List<String>, nowMillis: Long) {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TAGS, ObjectiveTags.encode(tags))
            put(COL_UPDATED_AT, nowMillis)
        }
        writableDatabase.update(TABLE_OBJECTIVES, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getTaskIdsForObjective(objectiveId: Long): List<Long> = readableDatabase.query(
        TABLE_TASKS,
        arrayOf(COL_ID),
        "$COL_OBJECTIVE_ID = ?",
        arrayOf(objectiveId.toString()),
        null,
        null,
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    fun deleteObjective(id: Long) {
        writableDatabase.delete(TABLE_OBJECTIVES, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getAll(): List<TaskItem> = readableDatabase.query(
        TABLE_TASKS,
        TASK_COLUMNS,
        null,
        null,
        null,
        null,
        "$COL_IS_DONE ASC, $COL_CREATED_AT DESC",
    ).use { cursor -> cursor.readTasks() }

    fun getTimeEntries(): List<TimeEntry> = readableDatabase.query(
        TABLE_TIME_ENTRIES,
        TIME_ENTRY_COLUMNS,
        null,
        null,
        null,
        null,
        "$COL_ENTRY_STARTED_AT ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    TimeEntry(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        taskId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ENTRY_TASK_ID)),
                        startedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ENTRY_STARTED_AT)),
                        endedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ENTRY_ENDED_AT)),
                        routineId = cursor.getNullableLong(COL_ENTRY_ROUTINE_ID),
                        routineRunId = cursor.getNullableLong(COL_ENTRY_ROUTINE_RUN_ID),
                    ),
                )
            }
        }
    }

    fun getReusableTasks(): List<ReusableTask> = readableDatabase.query(
        TABLE_REUSABLE_TASKS,
        REUSABLE_TASK_COLUMNS,
        null,
        null,
        null,
        null,
        "$COL_CREATED_AT DESC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    ReusableTask(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        subtaskTitles = ReusableTaskCodec.decode(
                            cursor.getString(cursor.getColumnIndexOrThrow(COL_TEMPLATE_SUBTASKS)),
                        ),
                        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
                    ),
                )
            }
        }
    }

    fun saveReusableTask(taskId: Long, nowMillis: Long): Long {
        val task = findById(taskId) ?: error("Task not found")
        require(task.parentTaskId == null) { "Only top-level tasks can become reusable" }
        val subtaskTitles = readableDatabase.query(
            TABLE_TASKS,
            arrayOf(COL_TITLE),
            "$COL_PARENT_ID = ?",
            arrayOf(taskId.toString()),
            null,
            null,
            "$COL_CREATED_AT ASC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        val values = ContentValues().apply {
            put(COL_TITLE, task.title)
            put(COL_TEMPLATE_SUBTASKS, ReusableTaskCodec.encode(subtaskTitles))
            put(COL_CREATED_AT, nowMillis)
        }
        return writableDatabase.insertOrThrow(TABLE_REUSABLE_TASKS, null, values)
    }

    fun instantiateReusableTask(
        reusableTaskId: Long,
        objectiveId: Long,
        nowMillis: Long,
    ): List<Long> {
        val reusableTask = readableDatabase.query(
            TABLE_REUSABLE_TASKS,
            REUSABLE_TASK_COLUMNS,
            "$COL_ID = ?",
            arrayOf(reusableTaskId.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return emptyList()
            ReusableTask(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                subtaskTitles = ReusableTaskCodec.decode(
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_TEMPLATE_SUBTASKS)),
                ),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_CREATED_AT)),
            )
        }
        val db = writableDatabase
        val insertedIds = mutableListOf<Long>()
        db.beginTransaction()
        try {
            val parentId = insertTask(db, objectiveId, reusableTask.title, null, null, nowMillis)
            insertedIds += parentId
            reusableTask.subtaskTitles.forEachIndexed { index, title ->
                insertedIds += insertTask(
                    db = db,
                    objectiveId = objectiveId,
                    title = title,
                    parentTaskId = parentId,
                    reminderAt = null,
                    nowMillis = nowMillis + index + 1L,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return insertedIds
    }

    fun deleteReusableTask(id: Long) {
        writableDatabase.delete(TABLE_REUSABLE_TASKS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun getRoutines(): List<Routine> = readableDatabase.query(
        TABLE_ROUTINES,
        ROUTINE_COLUMNS,
        null,
        null,
        null,
        null,
        "$COL_ROUTINE_ENABLED DESC, $COL_ROUTINE_NEXT_RUN_AT ASC",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.readRoutine(readableDatabase))
            }
        }
    }

    fun saveRoutine(
        id: Long?,
        name: String,
        frequency: RoutineFrequency,
        nextRunAt: Long,
        taskIds: List<Long>,
        nowMillis: Long,
    ): Long {
        require(taskIds.isNotEmpty()) { "A routine needs at least one task" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_ROUTINE_NAME, name)
                put(COL_ROUTINE_FREQUENCY, frequency.name)
                put(COL_ROUTINE_NEXT_RUN_AT, nextRunAt)
                put(COL_ROUTINE_ENABLED, 1)
                put(COL_UPDATED_AT, nowMillis)
                if (id == null) put(COL_CREATED_AT, nowMillis)
            }
            val routineId = if (id == null) {
                db.insertOrThrow(TABLE_ROUTINES, null, values)
            } else {
                db.update(TABLE_ROUTINES, values, "$COL_ID = ?", arrayOf(id.toString()))
                id
            }
            db.delete(TABLE_ROUTINE_STEPS, "$COL_STEP_ROUTINE_ID = ?", arrayOf(routineId.toString()))
            taskIds.distinct().forEachIndexed { position, taskId ->
                db.insertOrThrow(
                    TABLE_ROUTINE_STEPS,
                    null,
                    ContentValues().apply {
                        put(COL_STEP_ROUTINE_ID, routineId)
                        put(COL_STEP_TASK_ID, taskId)
                        put(COL_STEP_POSITION, position)
                    },
                )
            }
            db.setTransactionSuccessful()
            return routineId
        } finally {
            db.endTransaction()
        }
    }

    fun setRoutineEnabled(id: Long, enabled: Boolean, nowMillis: Long) {
        writableDatabase.update(
            TABLE_ROUTINES,
            ContentValues().apply {
                put(COL_ROUTINE_ENABLED, if (enabled) 1 else 0)
                put(COL_UPDATED_AT, nowMillis)
            },
            "$COL_ID = ?",
            arrayOf(id.toString()),
        )
    }

    fun activateRoutine(
        routineId: Long,
        scheduledAt: Long,
        nowMillis: Long,
    ): RoutineProgress? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val routine = findRoutineById(db, routineId) ?: return null
            if (!routine.isEnabled || routine.taskIds.isEmpty()) return null

            val runValues = ContentValues().apply {
                put(COL_RUN_ROUTINE_ID, routineId)
                put(COL_RUN_SCHEDULED_AT, scheduledAt)
                put(COL_RUN_CURRENT_POSITION, 0)
            }
            val insertedRunId = db.insertWithOnConflict(
                TABLE_ROUTINE_RUNS,
                null,
                runValues,
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            val runId = if (insertedRunId >= 0L) insertedRunId else {
                DatabaseUtils.longForQuery(
                    db,
                    "SELECT $COL_ID FROM $TABLE_ROUTINE_RUNS " +
                        "WHERE $COL_RUN_ROUTINE_ID = ? AND $COL_RUN_SCHEDULED_AT = ?",
                    arrayOf(routineId.toString(), scheduledAt.toString()),
                )
            }
            if (insertedRunId >= 0L) {
                routine.taskIds.forEach { taskId ->
                    val task = findById(db, taskId) ?: return@forEach
                    recordRunningSession(db, task, nowMillis)
                    db.update(
                        TABLE_TASKS,
                        ContentValues().apply {
                            put(COL_IS_DONE, 0)
                            putNull(COL_COMPLETED_AT)
                            putNull(COL_TIMER_STARTED_AT)
                            putNull(COL_TIMER_ROUTINE_RUN_ID)
                            put(COL_UPDATED_AT, nowMillis)
                        },
                        "$COL_ID = ?",
                        arrayOf(taskId.toString()),
                    )
                }
                var nextRunAt = routine.frequency.nextOccurrence(scheduledAt)
                while (nextRunAt <= nowMillis) {
                    nextRunAt = routine.frequency.nextOccurrence(nextRunAt)
                }
                db.update(
                    TABLE_ROUTINES,
                    ContentValues().apply {
                        put(COL_ROUTINE_NEXT_RUN_AT, nextRunAt)
                        put(COL_UPDATED_AT, nowMillis)
                    },
                    "$COL_ID = ?",
                    arrayOf(routineId.toString()),
                )
            }
            db.setTransactionSuccessful()
            return routineProgress(db, runId)
        } finally {
            db.endTransaction()
        }
    }

    fun getActiveRoutineProgress(): RoutineProgress? = readableDatabase.rawQuery(
        """
        SELECT rr.$COL_ID
        FROM $TABLE_ROUTINE_RUNS rr
        WHERE rr.$COL_RUN_COMPLETED_AT IS NULL
        ORDER BY CASE WHEN rr.$COL_RUN_STARTED_AT IS NOT NULL THEN 0 ELSE 1 END,
                 rr.$COL_RUN_SCHEDULED_AT DESC
        LIMIT 1
        """.trimIndent(),
        null,
    ).use { cursor ->
        if (cursor.moveToFirst()) routineProgress(readableDatabase, cursor.getLong(0)) else null
    }

    fun getReadyRoutineProgress(): RoutineProgress? = readableDatabase.query(
        TABLE_ROUTINE_RUNS,
        arrayOf(COL_ID),
        "$COL_RUN_COMPLETED_AT IS NULL AND $COL_RUN_STARTED_AT IS NULL",
        null,
        null,
        null,
        "$COL_RUN_SCHEDULED_AT DESC",
        "1",
    ).use { cursor ->
        if (cursor.moveToFirst()) routineProgress(readableDatabase, cursor.getLong(0)) else null
    }

    fun startRoutineRun(runId: Long, nowMillis: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val progress = routineProgress(db, runId) ?: return
            val taskId = progress.currentTaskId ?: return
            db.execSQL(
                "UPDATE $TABLE_ROUTINE_RUNS SET $COL_RUN_STARTED_AT = " +
                    "COALESCE($COL_RUN_STARTED_AT, ?) WHERE $COL_ID = ?",
                arrayOf(nowMillis, runId),
            )
            startTimer(db, taskId, nowMillis, runId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun advanceActiveRoutinesForTasks(taskIds: Collection<Long>, nowMillis: Long) {
        if (taskIds.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val runIds = db.query(
                TABLE_ROUTINE_RUNS,
                arrayOf(COL_ID),
                "$COL_RUN_COMPLETED_AT IS NULL",
                null,
                null,
                null,
                null,
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
            runIds.forEach { runId ->
                val progress = routineProgress(db, runId) ?: return@forEach
                val currentTaskId = progress.currentTaskId ?: return@forEach
                if (currentTaskId !in taskIds) return@forEach
                val nextPosition = progress.taskIds.indices.firstOrNull { position ->
                    position > progress.currentPosition &&
                        findById(db, progress.taskIds[position])?.isDone == false
                }
                if (nextPosition == null) {
                    db.update(
                        TABLE_ROUTINE_RUNS,
                        ContentValues().apply { put(COL_RUN_COMPLETED_AT, nowMillis) },
                        "$COL_ID = ?",
                        arrayOf(runId.toString()),
                    )
                } else {
                    db.update(
                        TABLE_ROUTINE_RUNS,
                        ContentValues().apply { put(COL_RUN_CURRENT_POSITION, nextPosition) },
                        "$COL_ID = ?",
                        arrayOf(runId.toString()),
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getPendingTaskCount(): Int = DatabaseUtils.longForQuery(
        readableDatabase,
        "SELECT COUNT(*) FROM $TABLE_TASKS WHERE $COL_IS_DONE = 0",
        null,
    ).toInt()

    fun getPendingTaskSummaries(limit: Int): List<PendingTaskSummary> = readableDatabase.rawQuery(
        """
        SELECT t.$COL_ID, t.$COL_TITLE, o.$COL_TITLE AS objective_title,
               t.$COL_PARENT_ID, t.$COL_REMINDER_AT
        FROM $TABLE_TASKS t
        INNER JOIN $TABLE_OBJECTIVES o ON o.$COL_ID = t.$COL_OBJECTIVE_ID
        WHERE t.$COL_IS_DONE = 0
        ORDER BY CASE
                     WHEN t.$COL_TIMER_STARTED_AT IS NOT NULL THEN 0
                     WHEN t.$COL_REMINDER_AT IS NOT NULL THEN 1
                     ELSE 2
                 END,
                 t.$COL_REMINDER_AT ASC,
                 t.$COL_UPDATED_AT DESC
        LIMIT ?
        """.trimIndent(),
        arrayOf(limit.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    PendingTaskSummary(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        objectiveTitle = cursor.getString(2),
                        isSubtask = !cursor.isNull(3),
                        reminderAt = if (cursor.isNull(4)) null else cursor.getLong(4),
                    ),
                )
            }
        }
    }

    fun findById(id: Long): TaskItem? = findById(readableDatabase, id)

    fun getUpcomingReminders(nowMillis: Long): List<TaskItem> = readableDatabase.query(
        TABLE_TASKS,
        TASK_COLUMNS,
        "$COL_IS_DONE = 0 AND $COL_REMINDER_AT IS NOT NULL AND $COL_REMINDER_AT > ?",
        arrayOf(nowMillis.toString()),
        null,
        null,
        "$COL_REMINDER_AT ASC",
    ).use { cursor -> cursor.readTasks() }

    fun insert(
        objectiveId: Long,
        title: String,
        parentTaskId: Long?,
        reminderAt: Long?,
        nowMillis: Long,
    ): Long = insertTask(
        db = writableDatabase,
        objectiveId = objectiveId,
        title = title,
        parentTaskId = parentTaskId,
        reminderAt = reminderAt,
        nowMillis = nowMillis,
    )

    private fun insertTask(
        db: SQLiteDatabase,
        objectiveId: Long,
        title: String,
        parentTaskId: Long?,
        reminderAt: Long?,
        nowMillis: Long,
    ): Long {
        val values = ContentValues().apply {
            put(COL_OBJECTIVE_ID, objectiveId)
            put(COL_TITLE, title)
            putNullableLong(COL_PARENT_ID, parentTaskId)
            putNullableLong(COL_REMINDER_AT, reminderAt)
            put(COL_CREATED_AT, nowMillis)
            put(COL_UPDATED_AT, nowMillis)
        }
        return db.insertOrThrow(TABLE_TASKS, null, values)
    }

    fun update(id: Long, title: String, reminderAt: Long?, nowMillis: Long) {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            putNullableLong(COL_REMINDER_AT, reminderAt)
            put(COL_UPDATED_AT, nowMillis)
        }
        writableDatabase.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun setDone(id: Long, done: Boolean, nowMillis: Long): List<Long> {
        val db = writableDatabase
        val affectedIds = mutableListOf<Long>()
        db.beginTransaction()
        try {
            val target = findById(db, id) ?: return emptyList()
            affectedIds += id
            if (done && target.parentTaskId == null) {
                db.query(
                    TABLE_TASKS,
                    arrayOf(COL_ID),
                    "$COL_PARENT_ID = ?",
                    arrayOf(id.toString()),
                    null,
                    null,
                    null,
                ).use { cursor ->
                    while (cursor.moveToNext()) affectedIds += cursor.getLong(0)
                }
            }

            affectedIds.forEach { affectedId ->
                val current = findById(db, affectedId) ?: return@forEach
                val values = ContentValues().apply {
                    put(COL_IS_DONE, if (done) 1 else 0)
                    if (done) {
                        recordRunningSession(db, current, nowMillis)
                        put(
                            COL_TRACKED_MILLIS,
                            TimeTracking.totalAt(
                                current.trackedMillis,
                                current.timerStartedAt,
                                nowMillis,
                            ),
                        )
                        putNull(COL_TIMER_STARTED_AT)
                        putNull(COL_TIMER_ROUTINE_RUN_ID)
                        put(COL_COMPLETED_AT, nowMillis)
                    } else {
                        putNull(COL_COMPLETED_AT)
                    }
                    put(COL_UPDATED_AT, nowMillis)
                }
                db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(affectedId.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return affectedIds
    }

    fun startTimer(id: Long, nowMillis: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val routineRunId = resolveRoutineRunForTask(db, id, nowMillis)
            startTimer(db, id, nowMillis, routineRunId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun startTimer(
        db: SQLiteDatabase,
        id: Long,
        nowMillis: Long,
        routineRunId: Long?,
    ) {
        db.query(
            TABLE_TASKS,
            TASK_COLUMNS,
            "$COL_TIMER_STARTED_AT IS NOT NULL",
            null,
            null,
            null,
            null,
        ).use { cursor ->
            cursor.readTasks().forEach { runningTask ->
                recordRunningSession(db, runningTask, nowMillis)
                val values = ContentValues().apply {
                    put(
                        COL_TRACKED_MILLIS,
                        TimeTracking.totalAt(
                            runningTask.trackedMillis,
                            runningTask.timerStartedAt,
                            nowMillis,
                        ),
                    )
                    putNull(COL_TIMER_STARTED_AT)
                    putNull(COL_TIMER_ROUTINE_RUN_ID)
                    put(COL_UPDATED_AT, nowMillis)
                }
                db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(runningTask.id.toString()))
            }
        }

        val target = findById(db, id)
        if (target != null && !target.isDone) {
            val values = ContentValues().apply {
                put(COL_TIMER_STARTED_AT, nowMillis)
                putNullableLong(COL_TIMER_ROUTINE_RUN_ID, routineRunId)
                put(COL_UPDATED_AT, nowMillis)
            }
            db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(id.toString()))
        }
    }

    fun stopTimer(id: Long, nowMillis: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val current = findById(db, id) ?: return
            if (current.timerStartedAt == null) return
            recordRunningSession(db, current, nowMillis)
            val values = ContentValues().apply {
                put(
                    COL_TRACKED_MILLIS,
                    TimeTracking.totalAt(
                        current.trackedMillis,
                        current.timerStartedAt,
                        nowMillis,
                    ),
                )
                putNull(COL_TIMER_STARTED_AT)
                putNull(COL_TIMER_ROUTINE_RUN_ID)
                put(COL_UPDATED_AT, nowMillis)
            }
            db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getItemAndChildIds(id: Long): List<Long> {
        val ids = mutableListOf(id)
        readableDatabase.query(
            TABLE_TASKS,
            arrayOf(COL_ID),
            "$COL_PARENT_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
        ).use { cursor -> while (cursor.moveToNext()) ids += cursor.getLong(0) }
        return ids
    }

    fun delete(id: Long) {
        writableDatabase.delete(TABLE_TASKS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    private fun recordRunningSession(db: SQLiteDatabase, task: TaskItem, nowMillis: Long) {
        val startedAt = task.timerStartedAt ?: return
        val endedAt = nowMillis.coerceAtLeast(startedAt)
        if (endedAt <= startedAt) return
        val values = ContentValues().apply {
            put(COL_ENTRY_TASK_ID, task.id)
            put(COL_ENTRY_STARTED_AT, startedAt)
            put(COL_ENTRY_ENDED_AT, endedAt)
            task.timerRoutineRunId?.let { runId ->
                put(COL_ENTRY_ROUTINE_RUN_ID, runId)
                routineIdForRun(db, runId)?.let { put(COL_ENTRY_ROUTINE_ID, it) }
            }
        }
        db.insertOrThrow(TABLE_TIME_ENTRIES, null, values)
    }

    private fun findById(db: SQLiteDatabase, id: Long): TaskItem? = db.query(
        TABLE_TASKS,
        TASK_COLUMNS,
        "$COL_ID = ?",
        arrayOf(id.toString()),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.readTask() else null }

    private fun findRoutineById(db: SQLiteDatabase, id: Long): Routine? = db.query(
        TABLE_ROUTINES,
        ROUTINE_COLUMNS,
        "$COL_ID = ?",
        arrayOf(id.toString()),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.readRoutine(db) else null }

    private fun Cursor.readRoutine(db: SQLiteDatabase): Routine {
        val routineId = getLong(getColumnIndexOrThrow(COL_ID))
        return Routine(
            id = routineId,
            name = getString(getColumnIndexOrThrow(COL_ROUTINE_NAME)),
            frequency = RoutineFrequency.valueOf(
                getString(getColumnIndexOrThrow(COL_ROUTINE_FREQUENCY)),
            ),
            nextRunAt = getLong(getColumnIndexOrThrow(COL_ROUTINE_NEXT_RUN_AT)),
            isEnabled = getInt(getColumnIndexOrThrow(COL_ROUTINE_ENABLED)) == 1,
            taskIds = routineTaskIds(db, routineId),
            createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
        )
    }

    private fun routineTaskIds(db: SQLiteDatabase, routineId: Long): List<Long> = db.query(
        TABLE_ROUTINE_STEPS,
        arrayOf(COL_STEP_TASK_ID),
        "$COL_STEP_ROUTINE_ID = ?",
        arrayOf(routineId.toString()),
        null,
        null,
        "$COL_STEP_POSITION ASC",
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }

    private fun routineProgress(db: SQLiteDatabase, runId: Long): RoutineProgress? {
        return db.rawQuery(
            """
            SELECT rr.$COL_RUN_ROUTINE_ID,
                   r.$COL_ROUTINE_NAME,
                   rr.$COL_RUN_SCHEDULED_AT,
                   rr.$COL_RUN_STARTED_AT,
                   rr.$COL_RUN_CURRENT_POSITION
            FROM $TABLE_ROUTINE_RUNS rr
            INNER JOIN $TABLE_ROUTINES r ON r.$COL_ID = rr.$COL_RUN_ROUTINE_ID
            WHERE rr.$COL_ID = ? AND rr.$COL_RUN_COMPLETED_AT IS NULL
            LIMIT 1
            """.trimIndent(),
            arrayOf(runId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val routineId = cursor.getLong(0)
            RoutineProgress(
                runId = runId,
                routineId = routineId,
                routineName = cursor.getString(1),
                scheduledAt = cursor.getLong(2),
                startedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                currentPosition = cursor.getInt(4),
                taskIds = routineTaskIds(db, routineId),
            )
        }
    }

    private fun resolveRoutineRunForTask(
        db: SQLiteDatabase,
        taskId: Long,
        nowMillis: Long,
    ): Long? {
        return db.rawQuery(
            """
            SELECT rr.$COL_ID
            FROM $TABLE_ROUTINE_RUNS rr
            INNER JOIN $TABLE_ROUTINE_STEPS rs
                ON rs.$COL_STEP_ROUTINE_ID = rr.$COL_RUN_ROUTINE_ID
               AND rs.$COL_STEP_POSITION = rr.$COL_RUN_CURRENT_POSITION
            WHERE rr.$COL_RUN_COMPLETED_AT IS NULL
              AND rs.$COL_STEP_TASK_ID = ?
            ORDER BY CASE WHEN rr.$COL_RUN_STARTED_AT IS NOT NULL THEN 0 ELSE 1 END,
                     rr.$COL_RUN_SCHEDULED_AT DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(taskId.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            cursor.getLong(0).also { runId ->
                db.execSQL(
                    "UPDATE $TABLE_ROUTINE_RUNS SET $COL_RUN_STARTED_AT = " +
                        "COALESCE($COL_RUN_STARTED_AT, ?) WHERE $COL_ID = ?",
                    arrayOf(nowMillis, runId),
                )
            }
        }
    }

    private fun routineIdForRun(db: SQLiteDatabase, runId: Long): Long? = db.query(
        TABLE_ROUTINE_RUNS,
        arrayOf(COL_RUN_ROUTINE_ID),
        "$COL_ID = ?",
        arrayOf(runId.toString()),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun Cursor.readTasks(): List<TaskItem> = buildList {
        while (moveToNext()) add(readTask())
    }

    private fun Cursor.readTask(): TaskItem = TaskItem(
        id = getLong(getColumnIndexOrThrow(COL_ID)),
        objectiveId = getLong(getColumnIndexOrThrow(COL_OBJECTIVE_ID)),
        parentTaskId = getNullableLong(COL_PARENT_ID),
        title = getString(getColumnIndexOrThrow(COL_TITLE)),
        reminderAt = getNullableLong(COL_REMINDER_AT),
        isDone = getInt(getColumnIndexOrThrow(COL_IS_DONE)) == 1,
        createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
        completedAt = getNullableLong(COL_COMPLETED_AT),
        trackedMillis = getLong(getColumnIndexOrThrow(COL_TRACKED_MILLIS)),
        timerStartedAt = getNullableLong(COL_TIMER_STARTED_AT),
        timerRoutineRunId = getNullableLong(COL_TIMER_ROUTINE_RUN_ID),
        updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
    )

    private fun Cursor.getNullableLong(columnName: String): Long? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getLong(index)
    }

    private fun ContentValues.putNullableLong(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun createObjectivesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_OBJECTIVES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_TAGS TEXT NOT NULL DEFAULT '',
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createTaskIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX index_tasks_objective_id ON $TABLE_TASKS($COL_OBJECTIVE_ID)")
        db.execSQL("CREATE INDEX index_tasks_parent_id ON $TABLE_TASKS($COL_PARENT_ID)")
        db.execSQL("CREATE INDEX index_tasks_reminder_at ON $TABLE_TASKS($COL_REMINDER_AT)")
    }

    private fun createTimeEntriesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TIME_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ENTRY_TASK_ID INTEGER NOT NULL REFERENCES $TABLE_TASKS($COL_ID) ON DELETE CASCADE,
                $COL_ENTRY_STARTED_AT INTEGER NOT NULL,
                $COL_ENTRY_ENDED_AT INTEGER NOT NULL,
                $COL_ENTRY_ROUTINE_ID INTEGER REFERENCES $TABLE_ROUTINES($COL_ID),
                $COL_ENTRY_ROUTINE_RUN_ID INTEGER REFERENCES $TABLE_ROUTINE_RUNS($COL_ID)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_time_entries_task_id " +
                "ON $TABLE_TIME_ENTRIES($COL_ENTRY_TASK_ID)",
        )
    }

    private fun createReusableTasksTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_REUSABLE_TASKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_TEMPLATE_SUBTASKS TEXT NOT NULL DEFAULT '',
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createRoutineTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTINES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ROUTINE_NAME TEXT NOT NULL,
                $COL_ROUTINE_FREQUENCY TEXT NOT NULL,
                $COL_ROUTINE_NEXT_RUN_AT INTEGER NOT NULL,
                $COL_ROUTINE_ENABLED INTEGER NOT NULL DEFAULT 1,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTINE_STEPS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_STEP_ROUTINE_ID INTEGER NOT NULL
                    REFERENCES $TABLE_ROUTINES($COL_ID) ON DELETE CASCADE,
                $COL_STEP_TASK_ID INTEGER NOT NULL
                    REFERENCES $TABLE_TASKS($COL_ID) ON DELETE CASCADE,
                $COL_STEP_POSITION INTEGER NOT NULL,
                UNIQUE($COL_STEP_ROUTINE_ID, $COL_STEP_TASK_ID)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTINE_RUNS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RUN_ROUTINE_ID INTEGER NOT NULL
                    REFERENCES $TABLE_ROUTINES($COL_ID) ON DELETE CASCADE,
                $COL_RUN_SCHEDULED_AT INTEGER NOT NULL,
                $COL_RUN_STARTED_AT INTEGER,
                $COL_RUN_COMPLETED_AT INTEGER,
                $COL_RUN_CURRENT_POSITION INTEGER NOT NULL DEFAULT 0,
                UNIQUE($COL_RUN_ROUTINE_ID, $COL_RUN_SCHEDULED_AT)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_routine_steps_routine_id " +
                "ON $TABLE_ROUTINE_STEPS($COL_STEP_ROUTINE_ID)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_routine_runs_routine_id " +
                "ON $TABLE_ROUTINE_RUNS($COL_RUN_ROUTINE_ID)",
        )
    }

    private fun migrateTasksIntoObjective(db: SQLiteDatabase) {
        createObjectivesTable(db)
        db.execSQL(
            "ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_OBJECTIVE_ID " +
                "INTEGER REFERENCES $TABLE_OBJECTIVES($COL_ID) ON DELETE CASCADE",
        )
        if (DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM $TABLE_TASKS", null) > 0L) {
            val values = ContentValues().apply {
                put(COL_TITLE, "Imported tasks")
                put(COL_CREATED_AT, System.currentTimeMillis())
                put(COL_UPDATED_AT, System.currentTimeMillis())
            }
            val objectiveId = db.insertOrThrow(TABLE_OBJECTIVES, null, values)
            val taskValues = ContentValues().apply { put(COL_OBJECTIVE_ID, objectiveId) }
            db.update(TABLE_TASKS, taskValues, null, null)
        }
        db.execSQL("CREATE INDEX index_tasks_objective_id ON $TABLE_TASKS($COL_OBJECTIVE_ID)")
    }

    private fun addObjectiveTagsColumnIfMissing(db: SQLiteDatabase) {
        if (!hasColumn(db, TABLE_OBJECTIVES, COL_TAGS)) {
            db.execSQL(
                "ALTER TABLE $TABLE_OBJECTIVES ADD COLUMN $COL_TAGS TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    private fun addUpdateTrackingAndTimeEntries(db: SQLiteDatabase) {
        if (!hasColumn(db, TABLE_OBJECTIVES, COL_UPDATED_AT)) {
            db.execSQL(
                "ALTER TABLE $TABLE_OBJECTIVES ADD COLUMN $COL_UPDATED_AT " +
                    "INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE $TABLE_OBJECTIVES SET $COL_UPDATED_AT = $COL_CREATED_AT " +
                    "WHERE $COL_UPDATED_AT = 0",
            )
        }
        if (!hasColumn(db, TABLE_TASKS, COL_UPDATED_AT)) {
            db.execSQL(
                "ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_UPDATED_AT " +
                    "INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL(
                "UPDATE $TABLE_TASKS SET $COL_UPDATED_AT = " +
                    "COALESCE($COL_COMPLETED_AT, $COL_TIMER_STARTED_AT, $COL_CREATED_AT) " +
                    "WHERE $COL_UPDATED_AT = 0",
            )
        }
        createTimeEntriesTable(db)
        db.execSQL(
            """
            INSERT INTO $TABLE_TIME_ENTRIES (
                $COL_ENTRY_TASK_ID,
                $COL_ENTRY_STARTED_AT,
                $COL_ENTRY_ENDED_AT
            )
            SELECT $COL_ID,
                   MAX(0, $COL_UPDATED_AT - $COL_TRACKED_MILLIS),
                   $COL_UPDATED_AT
            FROM $TABLE_TASKS
            WHERE $COL_TRACKED_MILLIS > 0
            """.trimIndent(),
        )
    }

    private fun addRoutinesAndAttribution(db: SQLiteDatabase) {
        createRoutineTables(db)
        if (!hasColumn(db, TABLE_TASKS, COL_TIMER_ROUTINE_RUN_ID)) {
            db.execSQL("ALTER TABLE $TABLE_TASKS ADD COLUMN $COL_TIMER_ROUTINE_RUN_ID INTEGER")
        }
        if (!hasColumn(db, TABLE_TIME_ENTRIES, COL_ENTRY_ROUTINE_ID)) {
            db.execSQL("ALTER TABLE $TABLE_TIME_ENTRIES ADD COLUMN $COL_ENTRY_ROUTINE_ID INTEGER")
        }
        if (!hasColumn(db, TABLE_TIME_ENTRIES, COL_ENTRY_ROUTINE_RUN_ID)) {
            db.execSQL("ALTER TABLE $TABLE_TIME_ENTRIES ADD COLUMN $COL_ENTRY_ROUTINE_RUN_ID INTEGER")
        }
    }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }

    private companion object {
        const val DATABASE_NAME = "todo_timer.db"
        const val DATABASE_VERSION = 6
        const val TABLE_OBJECTIVES = "objectives"
        const val TABLE_TASKS = "tasks"
        const val TABLE_TIME_ENTRIES = "time_entries"
        const val TABLE_REUSABLE_TASKS = "reusable_tasks"
        const val TABLE_ROUTINES = "routines"
        const val TABLE_ROUTINE_STEPS = "routine_steps"
        const val TABLE_ROUTINE_RUNS = "routine_runs"
        const val COL_ID = "id"
        const val COL_OBJECTIVE_ID = "objective_id"
        const val COL_PARENT_ID = "parent_id"
        const val COL_TITLE = "title"
        const val COL_TAGS = "tags"
        const val COL_REMINDER_AT = "reminder_at"
        const val COL_IS_DONE = "is_done"
        const val COL_CREATED_AT = "created_at"
        const val COL_COMPLETED_AT = "completed_at"
        const val COL_TRACKED_MILLIS = "tracked_millis"
        const val COL_TIMER_STARTED_AT = "timer_started_at"
        const val COL_TIMER_ROUTINE_RUN_ID = "timer_routine_run_id"
        const val COL_UPDATED_AT = "updated_at"
        const val COL_ENTRY_TASK_ID = "task_id"
        const val COL_ENTRY_STARTED_AT = "started_at"
        const val COL_ENTRY_ENDED_AT = "ended_at"
        const val COL_ENTRY_ROUTINE_ID = "routine_id"
        const val COL_ENTRY_ROUTINE_RUN_ID = "routine_run_id"
        const val COL_TEMPLATE_SUBTASKS = "subtasks"
        const val COL_ROUTINE_NAME = "name"
        const val COL_ROUTINE_FREQUENCY = "frequency"
        const val COL_ROUTINE_NEXT_RUN_AT = "next_run_at"
        const val COL_ROUTINE_ENABLED = "is_enabled"
        const val COL_STEP_ROUTINE_ID = "routine_id"
        const val COL_STEP_TASK_ID = "task_id"
        const val COL_STEP_POSITION = "position"
        const val COL_RUN_ROUTINE_ID = "routine_id"
        const val COL_RUN_SCHEDULED_AT = "scheduled_at"
        const val COL_RUN_STARTED_AT = "started_at"
        const val COL_RUN_COMPLETED_AT = "completed_at"
        const val COL_RUN_CURRENT_POSITION = "current_position"
        val OBJECTIVE_COLUMNS = arrayOf(
            COL_ID,
            COL_TITLE,
            COL_TAGS,
            COL_CREATED_AT,
            COL_UPDATED_AT,
        )
        val TASK_COLUMNS = arrayOf(
            COL_ID,
            COL_OBJECTIVE_ID,
            COL_PARENT_ID,
            COL_TITLE,
            COL_REMINDER_AT,
            COL_IS_DONE,
            COL_CREATED_AT,
            COL_COMPLETED_AT,
            COL_TRACKED_MILLIS,
            COL_TIMER_STARTED_AT,
            COL_TIMER_ROUTINE_RUN_ID,
            COL_UPDATED_AT,
        )
        val TIME_ENTRY_COLUMNS = arrayOf(
            COL_ID,
            COL_ENTRY_TASK_ID,
            COL_ENTRY_STARTED_AT,
            COL_ENTRY_ENDED_AT,
            COL_ENTRY_ROUTINE_ID,
            COL_ENTRY_ROUTINE_RUN_ID,
        )
        val REUSABLE_TASK_COLUMNS = arrayOf(
            COL_ID,
            COL_TITLE,
            COL_TEMPLATE_SUBTASKS,
            COL_CREATED_AT,
        )
        val ROUTINE_COLUMNS = arrayOf(
            COL_ID,
            COL_ROUTINE_NAME,
            COL_ROUTINE_FREQUENCY,
            COL_ROUTINE_NEXT_RUN_AT,
            COL_ROUTINE_ENABLED,
            COL_CREATED_AT,
            COL_UPDATED_AT,
        )
    }
}
