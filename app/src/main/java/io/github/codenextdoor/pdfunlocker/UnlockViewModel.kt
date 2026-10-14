package io.github.codenextdoor.pdfunlocker

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
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
 * Holds the step and the connection to the sandboxed helper that has the PDF open, which must survive
 * rotation but can't go into saved state. After the app is closed in the background it starts over.
 */
class UnlockViewModel(
    app: Application,
    private val store: PasswordStore,
    private val pdf: PdfClient,
) : AndroidViewModel(app) {
    constructor(app: Application) : this(app, PasswordStore.forApp(app), PdfClient(app))

    var state: UnlockState by mutableStateOf(UnlockState.Pick())
        private set
    var suggestedName: String = suggestedOutputName(null)
        private set

    /** The decrypted list while it's open on screen (after the phone's lock); null otherwise. */
    var savedPasswords: List<String>? by mutableStateOf(null)
        private set
    var savedCleared: Boolean by mutableStateOf(false)
        private set

    /**
     * The app's own copy of the chosen PDF (still locked), in its private cache: PdfRenderer can't read the
     * streams some apps hand over, and the original is never read again once copied.
     */
    private var copy: File? = null

    /** Whether the sandboxed helper has the PDF open (unlocked), ready to save. */
    private var opened = false
    private val resolver get() = getApplication<Application>().contentResolver
    private val copiesDir = File(app.cacheDir, "pdfs")

    init {
        pdf.bind()
        // Copies left by a crash or by Android closing the app; only once per process, so a second window
        // (e.g. "Open with" from Gmail) doesn't delete the first one's copy.
        if (!staleCopiesCleared) {
            staleCopiesCleared = true
            copiesDir.deleteRecursively()
        }
    }

    /** [uri] null: the picker was cancelled. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return
        endSession()
        state = UnlockState.Working()
        viewModelScope.launch {
            suggestedName = suggestedOutputName(displayName(uri))
            val file = copyIn(uri) ?: run {
                state = UnlockState.Pick(R.string.error_not_pdf)
                return@launch
            }
            copy = file
            state = when (pdf.open(file, password = null)) {
                PdfStatus.NOT_LOCKED, PdfStatus.UNLOCKED -> {
                    opened = true
                    UnlockState.ReadyToSave(notLocked = true)
                }
                PdfStatus.NEEDS_PASSWORD, PdfStatus.WRONG_PASSWORD -> tryStoredPasswords(file)
                PdfStatus.NOT_A_PDF -> UnlockState.Pick(R.string.error_not_pdf)
            }
        }
    }

    /** Most recently used first; the one that works moves to the top. Nothing found → ask. */
    private suspend fun tryStoredPasswords(file: File): UnlockState {
        val saved = store.load()
        if (saved.isEmpty()) return UnlockState.Password()
        state = UnlockState.Working(R.string.trying_saved)
        for (password in saved) {
            when (pdf.open(file, password)) {
                PdfStatus.NOT_LOCKED, PdfStatus.UNLOCKED -> {
                    opened = true
                    rememberPassword(password)
                    return UnlockState.ReadyToSave(notLocked = false, unlockedWithSaved = true)
                }
                PdfStatus.NOT_A_PDF -> return UnlockState.Pick(R.string.error_not_pdf)
                PdfStatus.NEEDS_PASSWORD, PdfStatus.WRONG_PASSWORD -> Unit
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
        val file = copy ?: run { state = UnlockState.Pick(R.string.error_pick_again); return }
        state = UnlockState.Working()
        viewModelScope.launch {
            state = when (pdf.open(file, password)) {
                PdfStatus.NOT_LOCKED, PdfStatus.UNLOCKED -> {
                    opened = true
                    rememberPassword(password)
                    UnlockState.ReadyToSave(notLocked = false)
                }
                PdfStatus.NEEDS_PASSWORD, PdfStatus.WRONG_PASSWORD -> UnlockState.Password(wrong = true)
                PdfStatus.NOT_A_PDF -> UnlockState.Pick(R.string.error_not_pdf)
            }
        }
    }

    /** [uri] null: the "Save as" dialog was cancelled. */
    fun onSaveTo(uri: Uri?) {
        if (uri == null) return
        if (!opened) {
            state = UnlockState.Pick(R.string.error_pick_again)
            return
        }
        val ready = state as? UnlockState.ReadyToSave ?: UnlockState.ReadyToSave(notLocked = false)
        state = UnlockState.Working()
        viewModelScope.launch {
            val result = try {
                val out = withContext(Dispatchers.IO) { resolver.openFileDescriptor(uri, "wt") }
                    ?: throw FileNotFoundException()
                pdf.save(out)
            } catch (_: Exception) {
                // IOException, SecurityException or IllegalArgumentException from the provider.
                SaveResult.FAILED
            }
            if (result != SaveResult.SAVED) {
                // Don't leave a broken copy behind (file:// test Uris aren't documents: nothing to delete).
                withContext(Dispatchers.IO) { runCatching { DocumentsContract.deleteDocument(resolver, uri) } }
            }
            state = when (result) {
                SaveResult.SAVED -> {
                    endSession()
                    UnlockState.Saved
                }
                SaveResult.FAILED -> ready.copy(saveFailed = true)
                // The helper was restarted and lost the open PDF.
                SaveResult.NO_DOCUMENT -> {
                    endSession()
                    UnlockState.Pick(R.string.error_reopen)
                }
            }
        }
    }

    /** Back to the start with [message], e.g. another app sent something this app can't open. */
    fun showMessage(@StringRes message: Int) {
        state = UnlockState.Pick(message)
    }

    /** Only after the phone's lock was passed. */
    fun openSavedPasswords() {
        viewModelScope.launch {
            val list = store.load()
            savedCleared = store.wasCleared
            savedPasswords = list
        }
    }

    fun closeSavedPasswords() {
        savedPasswords = null
        savedCleared = false
    }

    fun addSavedPassword(password: String) {
        if (password.isEmpty()) return
        viewModelScope.launch {
            rememberPassword(password)
            refreshSavedPasswords()
        }
    }

    fun deleteSavedPassword(password: String) {
        viewModelScope.launch {
            try {
                store.remove(password)
            } catch (_: Exception) {
            }
            refreshSavedPasswords()
        }
    }

    // Only while the list is still open: if it was closed meanwhile (app left), it stays closed.
    private suspend fun refreshSavedPasswords() {
        val list = store.load()
        if (savedPasswords != null) savedPasswords = list
    }

    fun onStartOver() {
        endSession()
        state = UnlockState.Pick()
    }

    override fun onCleared() {
        endSession()
        pdf.unbind()
    }

    /** Closes the opened PDF in the helper and deletes the private copy. */
    private fun endSession() {
        if (opened) pdf.close()
        opened = false
        copy?.delete()
        copy = null
    }

    /** Copies [uri] byte for byte into the private cache; null if it can't be read (gone, no permission, full). */
    private suspend fun copyIn(uri: Uri): File? = withContext(Dispatchers.IO) {
        val file = File(copiesDir, "${UUID.randomUUID()}.pdf")
        try {
            copiesDir.mkdirs()
            val input = resolver.openInputStream(uri) ?: return@withContext null
            input.use { from -> file.outputStream().use { from.copyTo(it) } }
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    private companion object {
        @Volatile var staleCopiesCleared = false
    }
}
