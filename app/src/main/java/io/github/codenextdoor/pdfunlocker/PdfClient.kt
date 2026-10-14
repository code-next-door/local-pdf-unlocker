package io.github.codenextdoor.pdfunlocker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class PdfStatus { NOT_LOCKED, UNLOCKED, NEEDS_PASSWORD, WRONG_PASSWORD, NOT_A_PDF }

/** NO_DOCUMENT: nothing is open in the helper (e.g. Android restarted it), so the PDF must be picked again. */
enum class SaveResult { SAVED, FAILED, NO_DOCUMENT }

/**
 * The app's side of the sandboxed helper (PdfService). Calls run on IO; bind() once, unbind() at the end.
 * Each client gets its own helper process, so two windows (launcher + "Open with") never share a document.
 */
class PdfClient(context: Context) {
    private val context = context.applicationContext
    private val service = MutableStateFlow<IPdfService?>(null)
    // Letters and digits only (Android rejects other characters in an instance name).
    private val instance = UUID.randomUUID().toString().replace("-", "")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service.value = IPdfService.Stub.asInterface(binder)
        }

        // The helper died; Android starts a fresh one (without the open document) and connects again.
        override fun onServiceDisconnected(name: ComponentName) {
            service.value = null
        }

        // Android won't reconnect this binding (e.g. the helper kept crashing): start a new one.
        override fun onBindingDied(name: ComponentName) {
            service.value = null
            context.unbindService(this)
            bind()
        }
    }

    fun bind() {
        context.bindIsolatedService(
            Intent(context, PdfService::class.java),
            Context.BIND_AUTO_CREATE,
            instance,
            context.mainExecutor,
            connection,
        )
    }

    fun unbind() = context.unbindService(connection)

    /** Opens [file] (the private copy) in the helper. A helper that died meanwhile counts as unreadable. */
    suspend fun open(file: File, password: String?): PdfStatus = withContext(Dispatchers.IO) {
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                PdfStatus.entries[connected().open(it, password)]
            }
        } catch (_: RemoteException) {
            PdfStatus.NOT_A_PDF
        } catch (_: java.io.IOException) {
            PdfStatus.NOT_A_PDF
        }
    }

    /** Writes the open document without its password to [out] and closes [out]. */
    suspend fun save(out: ParcelFileDescriptor): SaveResult = withContext(Dispatchers.IO) {
        out.use {
            try {
                SaveResult.entries[connected().save(it)]
            } catch (_: RemoteException) {
                SaveResult.NO_DOCUMENT
            }
        }
    }

    /** Fire and forget; the helper also closes everything when it's unbound. */
    fun close() {
        try {
            service.value?.close()
        } catch (_: RemoteException) {
        }
    }

    suspend fun isSandboxed(): Boolean = withContext(Dispatchers.IO) { connected().isSandboxed() }

    // Not connected within the timeout: treated like a helper that died (callers catch RemoteException).
    private suspend fun connected(): IPdfService =
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) { service.filterNotNull().first() }
            ?: throw RemoteException("The PDF helper isn't running")

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
