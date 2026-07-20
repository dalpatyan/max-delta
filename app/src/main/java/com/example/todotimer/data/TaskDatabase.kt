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
                $COL_TIMER_STARTED_AT INTEGER
            )
            """.trimIndent(),
        )
        createTaskIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) migrateTasksIntoObjective(db)
        if (oldVersion < 3) addObjectiveTagsColumnIfMissing(db)
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
        }
        return writableDatabase.insertOrThrow(TABLE_OBJECTIVES, null, values)
    }

    fun updateObjective(id: Long, title: String, tags: List<String>) {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_TAGS, ObjectiveTags.encode(tags))
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
    ): Long {
        val values = ContentValues().apply {
            put(COL_OBJECTIVE_ID, objectiveId)
            put(COL_TITLE, title)
            putNullableLong(COL_PARENT_ID, parentTaskId)
            putNullableLong(COL_REMINDER_AT, reminderAt)
            put(COL_CREATED_AT, nowMillis)
        }
        return writableDatabase.insertOrThrow(TABLE_TASKS, null, values)
    }

    fun update(id: Long, title: String, reminderAt: Long?) {
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            putNullableLong(COL_REMINDER_AT, reminderAt)
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
                        put(
                            COL_TRACKED_MILLIS,
                            TimeTracking.totalAt(
                                current.trackedMillis,
                                current.timerStartedAt,
                                nowMillis,
                            ),
                        )
                        putNull(COL_TIMER_STARTED_AT)
                        put(COL_COMPLETED_AT, nowMillis)
                    } else {
                        putNull(COL_COMPLETED_AT)
                    }
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
                    }
                    db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(runningTask.id.toString()))
                }
            }

            val target = findById(db, id)
            if (target != null && !target.isDone) {
                val values = ContentValues().apply { put(COL_TIMER_STARTED_AT, nowMillis) }
                db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun stopTimer(id: Long, nowMillis: Long) {
        val db = writableDatabase
        val current = findById(db, id) ?: return
        if (current.timerStartedAt == null) return
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
        }
        db.update(TABLE_TASKS, values, "$COL_ID = ?", arrayOf(id.toString()))
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
                $COL_CREATED_AT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    private fun createTaskIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX index_tasks_objective_id ON $TABLE_TASKS($COL_OBJECTIVE_ID)")
        db.execSQL("CREATE INDEX index_tasks_parent_id ON $TABLE_TASKS($COL_PARENT_ID)")
        db.execSQL("CREATE INDEX index_tasks_reminder_at ON $TABLE_TASKS($COL_REMINDER_AT)")
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
            }
            val objectiveId = db.insertOrThrow(TABLE_OBJECTIVES, null, values)
            val taskValues = ContentValues().apply { put(COL_OBJECTIVE_ID, objectiveId) }
            db.update(TABLE_TASKS, taskValues, null, null)
        }
        db.execSQL("CREATE INDEX index_tasks_objective_id ON $TABLE_TASKS($COL_OBJECTIVE_ID)")
    }

    private fun addObjectiveTagsColumnIfMissing(db: SQLiteDatabase) {
        val hasTagsColumn = db.rawQuery("PRAGMA table_info($TABLE_OBJECTIVES)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == COL_TAGS) {
                    found = true
                    break
                }
            }
            found
        }
        if (!hasTagsColumn) {
            db.execSQL(
                "ALTER TABLE $TABLE_OBJECTIVES ADD COLUMN $COL_TAGS TEXT NOT NULL DEFAULT ''",
            )
        }
    }

    private companion object {
        const val DATABASE_NAME = "todo_timer.db"
        const val DATABASE_VERSION = 3
        const val TABLE_OBJECTIVES = "objectives"
        const val TABLE_TASKS = "tasks"
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
        val OBJECTIVE_COLUMNS = arrayOf(COL_ID, COL_TITLE, COL_TAGS, COL_CREATED_AT)
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
        )
    }
}
