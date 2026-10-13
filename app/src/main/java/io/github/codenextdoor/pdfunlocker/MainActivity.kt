package io.github.codenextdoor.pdfunlocker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val viewModel: UnlockViewModel = viewModel()
                val state = viewModel.state
                // Deliberately not rememberTextFieldState(): that would put the password in saved state.
                val passwordState = remember { TextFieldState() }
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onPicked)
                val save = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(PDF),
                    viewModel::onSaveTo,
                )
                // Keep the typed password while unlocking (to fix a typo); drop it once past that step.
                LaunchedEffect(state) {
                    if (state !is UnlockState.Password && state !is UnlockState.Working) passwordState.clearText()
                }
                Scaffold { padding ->
                    UnlockScreen(
                        state = state,
                        passwordState = passwordState,
                        onPick = { pick.launch(arrayOf(PDF)) },
                        onUnlock = { viewModel.onUnlock(passwordState.text.toString()) },
                        onSave = { save.launch(viewModel.suggestedName) },
                        onStartOver = viewModel::onStartOver,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    private companion object {
        const val PDF = "application/pdf"
    }
}
