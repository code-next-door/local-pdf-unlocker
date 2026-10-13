package io.github.codenextdoor.pdfunlocker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun UnlockScreen(
    state: UnlockState,
    passwordState: TextFieldState,
    onPick: () -> Unit,
    onUnlock: () -> Unit,
    onSave: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        when (state) {
            is UnlockState.Pick -> {
                state.message?.let { ErrorText(it) }
                Text(stringResource(R.string.pick_intro))
                Button(onClick = onPick) { Text(stringResource(R.string.pick_button)) }
            }
            is UnlockState.Working -> {
                // The text says what's happening; the spinner itself stays silent for screen readers.
                CircularProgressIndicator(Modifier.clearAndSetSemantics {})
                Text(stringResource(state.message))
            }
            is UnlockState.Password -> {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = null)
                Text(stringResource(R.string.password_intro))
                OutlinedSecureTextField(
                    state = passwordState,
                    label = { Text(stringResource(R.string.password_label)) },
                    isError = state.wrong,
                    supportingText = if (state.wrong) {
                        { ErrorText(R.string.error_wrong_password) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    onKeyboardAction = KeyboardActionHandler { onUnlock() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onUnlock) { Text(stringResource(R.string.unlock_button)) }
                TextButton(onClick = onStartOver) { Text(stringResource(R.string.cancel)) }
            }
            is UnlockState.ReadyToSave -> {
                val message = when {
                    state.notLocked -> R.string.not_locked
                    state.unlockedWithSaved -> R.string.unlocked_with_saved
                    else -> R.string.unlocked
                }
                Text(stringResource(message))
                if (state.saveFailed) ErrorText(R.string.error_save_failed)
                Button(onClick = onSave) { Text(stringResource(R.string.save_button)) }
                TextButton(onClick = onStartOver) { Text(stringResource(R.string.cancel)) }
            }
            UnlockState.Saved -> {
                Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null)
                Text(stringResource(R.string.saved))
                Button(onClick = onStartOver) { Text(stringResource(R.string.start_over)) }
            }
        }
    }
}

/** Read out by screen readers as soon as it appears. */
@Composable
private fun ErrorText(message: Int) {
    Text(
        stringResource(message),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
