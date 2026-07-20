package com.example.todotimer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todotimer.data.Objective
import com.example.todotimer.data.ObjectiveTags

@Composable
internal fun ObjectiveEditorDialog(
    existing: Objective?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var tags by remember(existing?.id) {
        mutableStateOf(existing?.tags?.joinToString(", ").orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add objective" else "Edit objective") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Objective") },
                    placeholder = { Text("What outcome do you want to achieve?") },
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tags (optional)") },
                    placeholder = { Text("Personal, Professional") },
                    supportingText = { Text("Separate multiple custom tags with commas") },
                    maxLines = 2,
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onDelete) {
                        Text("Delete objective", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title.trim(), ObjectiveTags.fromInput(tags)) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
