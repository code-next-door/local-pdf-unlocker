package io.github.codenextdoor.pdfunlocker

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UnlockState {
    data class Pick(@StringRes val message: Int? = null) : UnlockState
    data class Working(@StringRes val message: Int = R.string.working) : UnlockState
    data class Password(val wrong: Boolean = false) : UnlockState
    data class ReadyToSave(
        val notLocked: Boolean,
        val saveFailed: Boolean = false,
        val unlockedWithSaved: Boolean = false,
    ) : UnlockState
    data object Saved : UnlockState
}

/**
 * Holds the step and the opened PDF, which must survive rotation but can't go into saved state. After the
 * app is closed in the background it starts over: the password is never kept anywhere.
 */
class UnlockViewModel(app: Application, private val store: PasswordStore) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, PasswordStore.forApp(app))

    var state: UnlockState by mutableStateOf(UnlockState.Pick())
        private set
    var suggestedName: String = suggestedOutputName(null)
        private set

    private var picked: Uri? = null
    private var renderer: PdfRenderer? = null
    private val resolver get() = getApplication<Application>().contentResolver

    /** [uri] null: the picker was cancelled. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        closeRenderer()
        picked = uri
        state = UnlockState.Working()
        viewModelScope.launch {
            suggestedName = suggestedOutputName(displayName(uri))
            state = when (val result = open(uri, password = null)) {
                is OpenResult.Opened -> {
                    renderer = result.renderer
                    UnlockState.ReadyToSave(notLocked = true)
                }
                OpenResult.NeedsPassword, OpenResult.WrongPassword -> tryStoredPasswords(uri)
                OpenResult.NotAPdf -> UnlockState.Pick(R.string.error_not_pdf)
            }
        }
    }

    /** Most recently used first; the one that works moves to the top. Nothing found → ask. */
    private suspend fun tryStoredPasswords(uri: Uri): UnlockState {
        val saved = store.load()
        if (saved.isEmpty()) return UnlockState.Password()
        state = UnlockState.Working(R.string.trying_saved)
        for (password in saved) {
            when (val result = open(uri, password)) {
                is OpenResult.Opened -> {
                    renderer = result.renderer
                    rememberPassword(password)
                    return UnlockState.ReadyToSave(notLocked = false, unlockedWithSaved = true)
                }
                OpenResult.NotAPdf -> return UnlockState.Pick(R.string.error_not_pdf)
                else -> Unit
            }
        }
        return UnlockState.Password()
    }

    /** Best-effort: a password that couldn't be saved must not fail the unlock. */
    private suspend fun rememberPassword(password: String) {
        try {
            store.remember(password)
        } catch (_: Exception) {
        }
    }

    fun onUnlock(password: String) {
        val uri = picked ?: run { state = UnlockState.Pick(R.string.error_pick_again); return }
        state = UnlockState.Working()
        viewModelScope.launch {
            state = when (val result = open(uri, password)) {
                is OpenResult.Opened -> {
                    renderer = result.renderer
                    rememberPassword(password)
                    UnlockState.ReadyToSave(notLocked = false)
                }
                OpenResult.NeedsPassword, OpenResult.WrongPassword -> UnlockState.Password(wrong = true)
                OpenResult.NotAPdf -> UnlockState.Pick(R.string.error_not_pdf)
            }
        }
    }

    /** [uri] null: the "Save as" dialog was cancelled. */
    fun onSaveTo(uri: Uri?) {
        if (uri == null) return
        val opened = renderer ?: run { state = UnlockState.Pick(R.string.error_pick_again); return }
        val ready = state as? UnlockState.ReadyToSave ?: UnlockState.ReadyToSave(notLocked = false)
        state = UnlockState.Working()
        viewModelScope.launch {
            state = try {
                withContext(Dispatchers.IO) {
                    val out = resolver.openFileDescriptor(uri, "wt") ?: throw FileNotFoundException()
                    out.use { PdfUnlocker.save(opened, it) }
                }
                closeRenderer()
                UnlockState.Saved
            } catch (_: Exception) {
                // IOException, SecurityException or IllegalArgumentException from the provider. Don't
                // leave a broken copy behind (file:// test Uris aren't documents: nothing to delete there).
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
                ready.copy(saveFailed = true)
            }
        }
    }

    /** Another app sent something this app can't open (no file, or not a content:// Uri). */
    fun showNotAPdf() {
        state = UnlockState.Pick(R.string.error_not_pdf)
    }

    fun onStartOver() {
        closeRenderer()
        picked = null
        state = UnlockState.Pick()
    }

    override fun onCleared() = closeRenderer()

    private fun closeRenderer() {
        renderer?.close()
        renderer = null
    }

    private suspend fun open(uri: Uri, password: String?): OpenResult {
        val pfd = withContext(Dispatchers.IO) {
            try {
                resolver.openFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null // Gone, no permission, or not a file: all mean "can't open this".
            }
        } ?: return OpenResult.NotAPdf
        return PdfUnlocker.open(pfd, password)
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }
}
