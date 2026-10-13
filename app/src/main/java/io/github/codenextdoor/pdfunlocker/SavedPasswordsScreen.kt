package io.github.codenextdoor.pdfunlocker

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The saved passwords, shown only after the phone's lock was passed. */
@Composable
fun SavedPasswordsScreen(
    passwords: List<String>,
    cleared: Boolean,
    addState: TextFieldState,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keeps the passwords out of screenshots, screen sharing and the Recents thumbnail (taken before
    // onStop closes the list).
    val window = (LocalActivity.current)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.saved_passwords),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (cleared) ErrorText(R.string.saved_cleared)
        if (passwords.isEmpty()) Text(stringResource(R.string.saved_passwords_empty))
        passwords.forEach { password ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(password, Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = password }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete))
                }
            }
        }
        OutlinedSecureTextField(
            state = addState,
            label = { Text(stringResource(R.string.add_password_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Hidden rather than disabled until there's something to add.
        if (addState.text.isNotEmpty()) Button(onClick = onAdd) { Text(stringResource(R.string.add_button)) }
        Button(onClick = onDone, Modifier.align(Alignment.End)) { Text(stringResource(R.string.done)) }
    }
    confirmDelete?.let { password ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_confirm)) },
            text = { Text(password) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(password)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
