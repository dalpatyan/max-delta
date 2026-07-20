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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TodoTimerTheme {
                TodoApp(viewModel)
            }
        }
    }
}

@Composable
private fun TodoApp(viewModel: TaskViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val objectives by viewModel.objectives.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
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
        onDownloadAnalytics = {
            pendingAnalyticsCsv.value = AnalyticsCsv.build(
                objectives = objectives,
                tasks = tasks,
                nowMillis = System.currentTimeMillis(),
            )
            analyticsExportLauncher.launch("max-delta-time-" + java.time.LocalDate.now() + ".csv")
        },
    )
}

private fun android.content.Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
