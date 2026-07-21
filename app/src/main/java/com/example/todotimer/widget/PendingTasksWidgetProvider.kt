package com.example.todotimer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.todotimer.MainActivity
import com.example.todotimer.R
import com.example.todotimer.di.AppGraph
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PendingTasksWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        update(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private val rowIds = intArrayOf(
            R.id.pending_task_1,
            R.id.pending_task_2,
            R.id.pending_task_3,
            R.id.pending_task_4,
            R.id.pending_task_5,
        )

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PendingTasksWidgetProvider::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val database = AppGraph.database(context)
            val pending = database.getPendingTaskSummaries(rowIds.size)
            val total = database.getPendingTaskCount()
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_ONGOING
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                20_101,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ids.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_pending_tasks)
                views.setTextViewText(
                    R.id.pending_task_count,
                    if (total == 1) "1 pending action" else "$total pending actions",
                )
                rowIds.forEachIndexed { index, rowId ->
                    val item = pending.getOrNull(index)
                    views.setViewVisibility(rowId, if (item == null) View.GONE else View.VISIBLE)
                    if (item != null) {
                        views.setTextViewText(
                            rowId,
                            buildString {
                                if (item.isSubtask) append("↳ ")
                                append(item.title)
                                append(" • ")
                                append(item.objectiveTitle)
                                item.reminderAt?.let { append(" • ").append(formatReminder(it)) }
                            },
                        )
                        views.setOnClickPendingIntent(rowId, openPendingIntent)
                    }
                }
                views.setViewVisibility(
                    R.id.pending_empty,
                    if (pending.isEmpty()) View.VISIBLE else View.GONE,
                )
                views.setOnClickPendingIntent(R.id.pending_widget_root, openPendingIntent)
                manager.updateAppWidget(widgetId, views)
            }
        }

        private fun formatReminder(epochMillis: Long): String {
            val dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
            val day = when (dateTime.toLocalDate()) {
                LocalDate.now() -> "Today"
                LocalDate.now().plusDays(1) -> "Tomorrow"
                else -> dateTime.format(DateTimeFormatter.ofPattern("d MMM"))
            }
            return "$day ${dateTime.format(DateTimeFormatter.ofPattern("h:mm a"))}"
        }
    }
}
