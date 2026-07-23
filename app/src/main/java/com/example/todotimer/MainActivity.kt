package com.example.todotimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.todotimer.data.AnalyticsCsv
import com.example.todotimer.reminder.ReminderScheduler
import com.example.todotimer.ui.TaskViewModel
import com.example.todotimer.ui.TodoScreen
import com.example.todotimer.ui.theme.TodoTimerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: TaskViewModel by viewModels()
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchAction = intent?.action
        enableEdgeToEdge()
        setContent {
            TodoTimerTheme {
                TodoApp(
                    viewModel = viewModel,
                    launchAction = launchAction,
                    onLaunchActionConsumed = { launchAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction = intent.action
    }

    companion object {
        const val ACTION_QUICK_ADD_TASK = "com.example.todotimer.action.QUICK_ADD_TASK"
        const val ACTION_OPEN_ONGOING = "com.example.todotimer.action.OPEN_ONGOING"
        const val ACTION_OPEN_ROUTINES = "com.example.todotimer.action.OPEN_ROUTINES"
    }
}

@Composable
private fun TodoApp(
    viewModel: TaskViewModel,
    launchAction: String?,
    onLaunchActionConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val objectives by viewModel.objectives.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val timeEntries by viewModel.timeEntries.collectAsStateWithLifecycle()
    val reusableTasks by viewModel.reusableTasks.collectAsStateWithLifecycle()
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val routineProgress by viewModel.routineProgress.collectAsStateWithLifecycle()
    var notificationAllowed by remember { mutableStateOf(context.hasNotificationPermission()) }
    var exactAlarmAllowed by remember {
        mutableStateOf(ReminderScheduler(context).hasExactAlarmAccess())
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationAllowed = granted }
    val pendingAnalyticsCsv = remember { mutableStateOf("") }
    val analyticsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)
                    ?.bufferedWriter(Charsets.UTF_8)
                    ?.use { writer -> writer.write(pendingAnalyticsCsv.value) }
                    ?: error("Unable to open the selected file")
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "Analytics CSV saved" else "Could not save analytics CSV",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAllowed = context.hasNotificationPermission()
                exactAlarmAllowed = ReminderScheduler(context).hasExactAlarmAccess()
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAllowed) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    TodoScreen(
        objectives = objectives,
        tasks = tasks,
        timeEntries = timeEntries,
        reusableTasks = reusableTasks,
        routines = routines,
        routineProgress = routineProgress,
        launchAction = launchAction,
        onLaunchActionConsumed = onLaunchActionConsumed,
        notificationAllowed = notificationAllowed,
        exactAlarmAllowed = exactAlarmAllowed,
        onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onRequestExactAlarmPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val exactAlarmIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(exactAlarmIntent) }.onFailure {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        },
                    )
                }
            }
        },
        onSaveObjective = viewModel::saveObjective,
        onSaveTask = viewModel::saveTask,
        onSetDone = viewModel::setDone,
        onToggleTimer = viewModel::toggleTimer,
        onDeleteTask = viewModel::delete,
        onDeleteObjective = viewModel::deleteObjective,
        onSaveReusableTask = viewModel::saveReusableTask,
        onUseReusableTask = viewModel::useReusableTask,
        onDeleteReusableTask = viewModel::deleteReusableTask,
        onSaveRoutine = viewModel::saveRoutine,
        onSetRoutineEnabled = viewModel::setRoutineEnabled,
        onStartRoutine = viewModel::startRoutine,
        onUpdateRunningTimerStart = viewModel::updateRunningTimerStart,
        onSaveTimeEntry = viewModel::saveTimeEntry,
        onDeleteTimeEntry = viewModel::deleteTimeEntry,
        onDownloadAnalytics = { range ->
            pendingAnalyticsCsv.value = AnalyticsCsv.build(
                objectives = objectives,
                tasks = tasks,
                timeEntries = timeEntries,
                routines = routines,
                routineProgress = routineProgress,
                range = range,
                nowMillis = System.currentTimeMillis(),
            )
            analyticsExportLauncher.launch(
                "max-delta-time-${range.label.lowercase().replace(' ', '-')}-" +
                    java.time.LocalDate.now() + ".csv",
            )
        },
    )
}

private fun android.content.Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
