package io.github.codenextdoor.pdfunlocker

import android.app.Service
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import kotlinx.coroutines.runBlocking

/**
 * Reads PDFs in an isolated process (no access to the app's files, Keystore or anything else), as
 * PdfRenderer's documentation recommends for files from untrusted sources: a malicious PDF that exploited
 * the PDF engine would be stuck here, away from the saved passwords. Gets only descriptors and a password.
 */
class PdfService : Service() {
    private var renderer: PdfRenderer? = null

    private val binder = object : IPdfService.Stub() {
        override fun open(pdf: ParcelFileDescriptor, password: String?): Int = synchronized(this) {
            closeDocument()
            when (val result = runBlocking { PdfUnlocker.open(pdf, password) }) {
                is OpenResult.Opened -> {
                    renderer = result.renderer
                    if (result.wasLocked) PdfStatus.UNLOCKED else PdfStatus.NOT_LOCKED
                }
                OpenResult.NeedsPassword -> PdfStatus.NEEDS_PASSWORD
                OpenResult.WrongPassword -> PdfStatus.WRONG_PASSWORD
                OpenResult.NotAPdf -> PdfStatus.NOT_A_PDF
            }.ordinal
        }

        override fun save(destination: ParcelFileDescriptor): Int = synchronized(this) {
            destination.use {
                val document = renderer ?: return SaveResult.NO_DOCUMENT.ordinal
                try {
                    runBlocking { PdfUnlocker.save(document, it) }
                    SaveResult.SAVED.ordinal
                } catch (_: Exception) {
                    SaveResult.FAILED.ordinal
                }
            }
        }

        override fun close() = synchronized(this) { closeDocument() }

        override fun isSandboxed() = Process.isIsolated()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        closeDocument()
        super.onDestroy()
    }

    private fun closeDocument() {
        renderer?.close()
        renderer = null
    }
}
