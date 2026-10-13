package io.github.codenextdoor.pdfunlocker

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfUnlockerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val cacheDir = instrumentation.targetContext.cacheDir

    @Test fun plainOpensUnlocked() = runTest {
        val result = PdfUnlocker.open(openAsset("plain.pdf"), null)
        assertTrue(result is OpenResult.Opened)
        assertFalse((result as OpenResult.Opened).wasLocked)
        result.renderer.close()
    }

    @Test fun lockedWithoutPasswordNeedsPassword() = runTest {
        assertEquals(OpenResult.NeedsPassword, PdfUnlocker.open(openAsset("user-locked.pdf"), null))
    }

    @Test fun wrongPassword() = runTest {
        assertEquals(OpenResult.WrongPassword, PdfUnlocker.open(openAsset("user-locked.pdf"), "nope"))
    }

    @Test fun emptyPasswordIsWrong() = runTest {
        assertEquals(OpenResult.WrongPassword, PdfUnlocker.open(openAsset("user-locked.pdf"), ""))
    }

    @Test fun rightPasswordOpensAndSavedCopyNeedsNoPassword() = runTest {
        val opened = PdfUnlocker.open(openAsset("user-locked.pdf"), "user123") as OpenResult.Opened
        assertTrue(opened.wasLocked)
        val out = saveCopy(opened.renderer)
        // No LoadParams: throws SecurityException if the copy still needs a password.
        PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(opened.renderer.pageCount, it.pageCount)
            assertEquals(2, it.pageCount)
        }
        opened.renderer.close()
    }

    // Android removes owner restrictions (print/edit) only when the file was opened with the owner
    // password, which people rarely have. The copy still opens anywhere without a password.
    @Test fun ownerOnlySavedCopyOpensWithoutPassword() = runTest {
        val opened = PdfUnlocker.open(openAsset("owner-only.pdf"), null) as OpenResult.Opened
        assertFalse(opened.wasLocked)
        val out = saveCopy(opened.renderer)
        opened.renderer.close()
        PdfRenderer(ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)).use {
            assertEquals(2, it.pageCount)
        }
    }

    @Test fun notAPdf() = runTest {
        assertEquals(OpenResult.NotAPdf, PdfUnlocker.open(openAsset("not-a-pdf.pdf"), null))
    }

    // Some apps hand over a stream (a pipe), which PdfRenderer can't seek in: it throws
    // IllegalArgumentException. That must be "can't open this", not a crash.
    @Test fun notSeekableIsNotAPdf() = runTest {
        val (read, write) = ParcelFileDescriptor.createPipe()
        write.close()
        assertEquals(OpenResult.NotAPdf, PdfUnlocker.open(read, null))
    }

    @Test fun originalBytesUnchanged() = runTest {
        val original = copyAsset("user-locked.pdf")
        val opened = PdfUnlocker.open(open(original), "user123") as OpenResult.Opened
        saveCopy(opened.renderer)
        opened.renderer.close()
        assertArrayEquals(assetBytes("user-locked.pdf"), original.readBytes())
    }

    // PdfRenderer.write() reports nothing when it can't write; save() must not pass that off as success.
    @Test fun saveToReadOnlyDescriptorThrows() = runTest {
        val opened = PdfUnlocker.open(openAsset("plain.pdf"), null) as OpenResult.Opened
        val target = File(cacheDir, "readonly-out.pdf").apply { writeBytes(ByteArray(0)) }
        try {
            ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use {
                PdfUnlocker.save(opened.renderer, it)
            }
            fail("expected IOException")
        } catch (_: IOException) {
        } finally {
            opened.renderer.close()
        }
    }

    private suspend fun saveCopy(renderer: PdfRenderer): File {
        val out = File(cacheDir, "out.pdf").apply { delete() }
        ParcelFileDescriptor.open(
            out,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { PdfUnlocker.save(renderer, it) }
        return out
    }

    private fun assetBytes(name: String) = instrumentation.context.assets.open(name).use { it.readBytes() }

    private fun copyAsset(name: String) = File(cacheDir, name).apply { writeBytes(assetBytes(name)) }

    private fun open(file: File) = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    private fun openAsset(name: String) = open(copyAsset(name))
}
