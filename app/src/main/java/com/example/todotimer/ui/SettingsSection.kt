package com.example.todotimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.todotimer.BuildConfig

private const val PRIVACY_POLICY_URL =
    "https://max-delta-privacy.gaurav-kumar-iimi.chatgpt.site"
private const val SUPPORT_EMAIL = "gaurav.kumar.iimi@gmail.com"
private const val MIN_PASSWORD_LENGTH = 8

private enum class BackupDialogMode {
    EXPORT,
    RESTORE,
}

@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    backupBusy: Boolean,
    onExportBackup: (String) -> Unit,
    onRestoreBackup: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var dialogMode by remember { mutableStateOf<BackupDialogMode?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Backup and transfer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (BuildConfig.FLAVOR == "legacy") {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Migration build", fontWeight = FontWeight.Bold)
                        Text(
                            "Export an encrypted backup here. Then install the Google Play " +
                                "version and restore that file before removing the old app.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Encrypted full backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Includes objectives, tasks, notes, routines, reminders, timer state, " +
                            "and time history. Your password cannot be recovered.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (backupBusy) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator()
                            Text("Working with backup…")
                        }
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { dialogMode = BackupDialogMode.EXPORT },
                        ) {
                            Text("Export encrypted backup")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { dialogMode = BackupDialogMode.RESTORE },
                        ) {
                            Text("Restore encrypted backup")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "About",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Max Δ ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                    Text(
                        "${BuildConfig.DISTRIBUTION_LABEL} • ${BuildConfig.APPLICATION_ID}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runCatching { uriHandler.openUri(PRIVACY_POLICY_URL) } },
                    ) {
                        Text("Privacy policy")
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching { uriHandler.openUri("mailto:$SUPPORT_EMAIL") }
                        },
                    ) {
                        Text("Contact support")
                    }
                }
            }
        }
    }

    dialogMode?.let { mode ->
        BackupPasswordDialog(
            mode = mode,
            onDismiss = { dialogMode = null },
            onConfirm = { password ->
                dialogMode = null
                if (mode == BackupDialogMode.EXPORT) {
                    onExportBackup(password)
                } else {
                    onRestoreBackup(password)
                }
            },
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    mode: BackupDialogMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val exporting = mode == BackupDialogMode.EXPORT
    val passwordValid = password.length >= MIN_PASSWORD_LENGTH
    val confirmationValid = !exporting || password == confirmation
    val error = when {
        password.isNotEmpty() && !passwordValid ->
            "Use at least $MIN_PASSWORD_LENGTH characters."
        exporting && confirmation.isNotEmpty() && !confirmationValid ->
            "Passwords do not match."
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (exporting) "Protect backup" else "Restore backup")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (exporting) {
                        "Choose a password you can retain. Max Delta cannot recover it."
                    } else {
                        "Restoring replaces all data currently in this app. Select the correct " +
                            "backup file on the next screen."
                    },
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Backup password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                )
                if (exporting) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = confirmation,
                        onValueChange = { confirmation = it.take(128) },
                        label = { Text("Confirm password") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    )
                }
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide password" else "Show password")
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = passwordValid && confirmationValid,
                onClick = { onConfirm(password) },
            ) {
                Text(if (exporting) "Choose save location" else "Choose backup file")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
