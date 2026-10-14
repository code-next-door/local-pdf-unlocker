package io.github.codenextdoor.pdfunlocker

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** PDFs are read by the sandboxed helper process; it answers like PdfUnlocker does in-process. */
@RunWith(AndroidJUnit4::class)
class PdfServiceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val client = PdfClient(context).apply { bind() }

    @After
    fun unbind() = client.unbind()

    @Test fun runsSandboxed() = runBlocking { assertTrue(client.isSandboxed()) }

    @Test fun answersLikePdfUnlocker() = runBlocking {
        assertEquals(PdfStatus.NOT_LOCKED, client.open(asset("plain.pdf"), null))
        assertEquals(PdfStatus.NEEDS_PASSWORD, client.open(asset("user-locked.pdf"), null))
        assertEquals(PdfStatus.WRONG_PASSWORD, client.open(asset("user-locked.pdf"), "nope"))
        assertEquals(PdfStatus.NOT_A_PDF, client.open(asset("not-a-pdf.pdf"), null))
        assertEquals(PdfStatus.UNLOCKED, client.open(asset("user-locked.pdf"), "user123"))
    }

    @Test fun savesACopyWithoutThePassword() = runBlocking {
        assertEquals(PdfStatus.UNLOCKED, client.open(asset("user-locked.pdf"), "user123"))
        val out = File(context.cacheDir, "service-out.pdf").apply { delete() }
        assertEquals(SaveResult.SAVED, client.save(writable(out)))
        PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(2, it.pageCount)
        }
    }

    @Test fun saveToUnwritableTargetFails() = runBlocking {
        assertEquals(PdfStatus.NOT_LOCKED, client.open(asset("plain.pdf"), null))
        val target = File(context.cacheDir, "service-readonly.pdf").apply { writeBytes(ByteArray(0)) }
        assertEquals(SaveResult.FAILED, client.save(ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY)))
    }

    @Test fun saveWithoutAnOpenPdfSaysSo() = runBlocking {
        val out = File(context.cacheDir, "service-none.pdf").apply { delete() }
        assertEquals(SaveResult.NO_DOCUMENT, client.save(writable(out)))
    }

    // Two windows (launcher + "Open with" from another app) each have a PdfClient: each needs its own
    // open document, or Save in one writes the other's PDF.
    @Test fun twoClientsKeepTheirOwnDocuments() = runBlocking {
        val other = PdfClient(context).apply { bind() }
        try {
            assertEquals(PdfStatus.UNLOCKED, client.open(asset("user-locked.pdf"), "user123"))
            assertEquals(PdfStatus.UNLOCKED, other.open(asset("user-locked-big.pdf"), "user123"))
            val out = File(context.cacheDir, "service-first.pdf").apply { delete() }
            assertEquals(SaveResult.SAVED, client.save(writable(out)))
            PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)).use {
                assertEquals(2, it.pageCount)
            }
        } finally {
            other.unbind()
        }
    }

    // If the helper never connects, calls give up instead of spinning forever.
    @Test fun unconnectedClientGivesUp() = runBlocking {
        val never = PdfClient(context) // not bound
        val result = withTimeoutOrNull(30_000) { never.open(asset("plain.pdf"), null) }
        assertEquals(PdfStatus.NOT_A_PDF, result)
    }

    private fun asset(name: String) = File(context.cacheDir, "service-$name").apply {
        writeBytes(instrumentation.context.assets.open(name).use { it.readBytes() })
    }

    private fun writable(file: File) = ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
    )
}
