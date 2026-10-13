package io.github.codenextdoor.pdfunlocker

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val viewModel: UnlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // A PDF from another app ("Open with", Share). Only on a fresh start: after a rotation the
                // ViewModel already has it.
                LaunchedEffect(Unit) {
                    if (savedInstanceState == null && isIncomingFile(intent)) {
                        incomingPdfUri(intent)?.let(viewModel::onPicked) ?: viewModel.showMessage(R.string.error_not_pdf)
                    }
                }
                val state = viewModel.state
                val savedPasswords = viewModel.savedPasswords
                // Deliberately not rememberTextFieldState(): that would put the password in saved state.
                val passwordState = remember { TextFieldState() }
                val addState = remember { TextFieldState() }
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::onPicked)
                val save = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument(PDF),
                    viewModel::onSaveTo,
                )
                // Keep the typed password while unlocking (to fix a typo); drop it once past that step.
                LaunchedEffect(state) {
                    if (state !is UnlockState.Password && state !is UnlockState.Working) passwordState.clearText()
                }
                LaunchedEffect(savedPasswords == null) { if (savedPasswords == null) addState.clearText() }
                BackHandler(enabled = savedPasswords != null) { viewModel.closeSavedPasswords() }
                Scaffold { padding ->
                    if (savedPasswords != null) {
                        SavedPasswordsScreen(
                            passwords = savedPasswords,
                            cleared = viewModel.savedCleared,
                            addState = addState,
                            onAdd = {
                                viewModel.addSavedPassword(addState.text.toString())
                                addState.clearText()
                            },
                            onDelete = viewModel::deleteSavedPassword,
                            onDone = viewModel::closeSavedPasswords,
                            modifier = Modifier.padding(padding),
                        )
                    } else {
                        UnlockScreen(
                            state = state,
                            passwordState = passwordState,
                            onPick = { pick.launch(arrayOf(PDF)) },
                            onUnlock = { viewModel.onUnlock(passwordState.text.toString()) },
                            onSave = { save.launch(viewModel.suggestedName) },
                            onStartOver = viewModel::onStartOver,
                            onSavedPasswords = ::askForScreenLock,
                            modifier = Modifier.padding(padding),
                        )
                    }
                }
            }
        }
    }

    // Leaving the app closes the list, so coming back asks for the lock again. A rotation doesn't.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.closeSavedPasswords()
    }

    /** The phone's own fingerprint/face/PIN prompt before showing the saved passwords. */
    private fun askForScreenLock() {
        val allowed = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (getSystemService(BiometricManager::class.java).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.showMessage(R.string.no_screen_lock)
            return
        }
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.prompt_title))
            .setAllowedAuthenticators(allowed)
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        viewModel.openSavedPasswords()
                },
            )
    }

    private companion object {
        const val PDF = "application/pdf"
    }
}
