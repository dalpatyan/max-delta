# Max Δ — Android app

A local-first Android productivity app organized as Objective → Task → Subtask, with reminders and persistent time tracking.

## Included features

- Create, edit, and delete objectives.
- Add multiple custom tags to an objective, such as Personal or Professional.
- Search objectives by title and combine the search with a tag filter.
- Sort objectives by recently added, nearest upcoming reminder, or recently worked.
- Hide closed objectives and completed tasks/subtasks by default, with an explicit Show closed switch.
- Use the Ongoing section to see the active timer and all unfinished tasks/subtasks.
- Review total, objective-wise, and action-wise time for today, this week, this month, or a custom date range.
- Download the selected date range as an Excel-friendly CSV file.
- Save a task and its subtasks as a reusable template, then add a fresh copy to any objective.
- Create daily, weekly, monthly, or custom-weekday routines and choose their exact task order.
- Add a new task or subtask directly to one or more routines while creating it.
- Reopen routine tasks automatically at the scheduled time and start the first task from its notification.
- Advance a routine from the persistent notification with Mark done and Start tracking actions.
- Divide each objective into tasks and each task into optional subtasks.
- Track objective progress from completed tasks.
- Create, edit, complete, reopen, and delete tasks/subtasks.
- Add or clear a date-and-time reminder for every task or subtask.
- Receive reminder notifications, including after the phone restarts.
- Start and stop a persistent timer on each task or subtask.
- Keep the active timer visible in an ongoing notification with Stop working and Mark done actions.
- Receive a one-hour check-in notification when a timer keeps running.
- Add a short note to any task or subtask.
- Correct a running timer's start time, add time manually, and edit or delete completed time entries.
- Add home-screen widgets for pending tasks and quick task creation.
- Fit the quick-add widget into a compact 1×1 home-screen cell.
- See live elapsed time and cumulative tracked time.
- See subtask completion progress on each parent task.
- Light and dark themes, including Android dynamic color.

## Product rules used in this MVP

- Data is stored only on the device. There is no account or cloud sync.
- The hierarchy is Objective → Task → Subtask; subtasks are one level deep.
- Objectives are containers. Their progress is derived from completed tasks, while reminders and timers belong to tasks/subtasks.
- Objective tags are optional, comma-separated when editing, and displayed as chips.
- Search is case-insensitive; tag filtering uses one selected tag and can be combined with search.
- An objective is closed when all of its top-level tasks are done. Closed work is hidden from the Objectives section until Show closed is enabled; Analytics still includes its history.
- Each task and subtask is treated as an action in analytics. Timer sessions are retained so date-range totals include only overlapping work.
- Existing cumulative time is preserved during migration and backfilled as historical session data using the action's last known update.
- Reusable templates copy task/subtask titles only; reminders, completion state, and logged time start fresh.
- Routine schedules reuse existing tasks instead of creating duplicates. Each occurrence reopens the selected tasks and follows the saved order.
- Custom routines can run on any user-selected combination of Sunday through Saturday.
- Timer sessions started through a routine retain routine attribution; Analytics separates routine work from objective work without double counting.
- Manual time edits update both cumulative task time and date-range analytics; routine attribution is preserved when an attributed session is edited.
- CSV downloads use Android's document picker and do not require storage permission.
- Only one timer can run at once. Starting another timer first saves and stops the active one.
- Completing a task completes its subtasks and stops all related timers.
- Reopening a parent task does not automatically reopen its completed subtasks.
- On Android 12+, the app asks for precise-alarm access. Without it, Android may delay a reminder.
- On Android 13+, notification permission must be allowed for reminders, routine alerts, and running-timer notifications to appear.

## Run in Android Studio

1. Install a current Android Studio version with JDK 17.
2. Open the `MaxDelta` folder as a project.
3. Allow Android Studio to install Android SDK 35 if prompted and finish Gradle sync.
4. Select an Android 8.0 (API 26) or newer emulator/device.
5. Click **Run**.

The project uses Kotlin, Jetpack Compose, Material 3, `SQLiteOpenHelper`, `AlarmManager`, and Android home-screen widgets. It does not require a backend or API key. Upgrading preserves existing data; version 1 tasks are grouped under an “Imported tasks” objective, pre-tag objectives start with no tags, and the v1.7 database migration adds notes, custom routine days, and long-timer reminder state in place.

## Build commands

macOS/Linux:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Windows:

```bat
gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK will be created at `app/build/outputs/apk/debug/app-debug.apk`.

Local builds use `app/maxdelta-debug.keystore` when that private file is present, allowing future debug APKs signed with the same key to update an existing installation. The keystore is intentionally excluded from version control: keep a secure private backup and never use it for a production or Play Store release. A fresh clone without that file falls back to the machine's standard Android debug key and cannot update an installation signed with a different key.

Because versions through 1.2 used a transient key that is no longer available, version 1.2 must be uninstalled once before installing version 1.3; uninstalling clears that debug installation's local data, so export or back up the database first.

## Project structure

- `data/`: SQLite persistence and task/timer business operations.
- `reminder/`: alarm scheduling, reboot restoration, and notifications.
- `system/`: synchronization of timer notifications and widgets.
- `ui/`: Compose screen, editor, task cards, and view model.
- `util/`: timer arithmetic and duration formatting.
- `widget/`: pending-task and quick-add home-screen widgets.

## Verification

Pure unit tests cover cumulative time calculation, date-range overlap, routine recurrence and ordering, objective sorting, reusable templates, CSV output, clock rollback safety, and duration formatting. For reminder and routine QA, test once with precise alarms allowed and once denied to verify the fallback banner and behavior.
