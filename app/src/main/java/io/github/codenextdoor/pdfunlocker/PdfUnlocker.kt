package io.github.codenextdoor.pdfunlocker

import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface OpenResult {
    /** [wasLocked]: a password was needed. Owner-password-only PDFs open without one, so they count as not locked. */
    data class Opened(val renderer: PdfRenderer, val wasLocked: Boolean) : OpenResult
    data object NeedsPassword : OpenResult
    data object WrongPassword : OpenResult
    data object NotAPdf : OpenResult
}

/** Android's own PdfRenderer (password loading and write() need Android 15, the app's minSdk). */
object PdfUnlocker {
    /** Takes ownership of [pfd]: the returned renderer closes it, or it's closed here if opening fails. */
    suspend fun open(pfd: ParcelFileDescriptor, password: String?): OpenResult = withContext(Dispatchers.IO) {
        try {
            val renderer = if (password == null) {
                PdfRenderer(pfd)
            } else {
                PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())
            }
            OpenResult.Opened(renderer, wasLocked = password != null)
        } catch (_: SecurityException) {
            pfd.close()
            if (password == null) OpenResult.NeedsPassword else OpenResult.WrongPassword
        } catch (_: IOException) {
            pfd.close()
            OpenResult.NotAPdf
        } catch (_: IllegalArgumentException) {
            // Not seekable: some apps hand over a stream, which PdfRenderer can't read.
            pfd.close()
            OpenResult.NotAPdf
        }
    }

    /**
     * Writes a copy without the password. Throws IOException on failure. Owner restrictions (print/edit)
     * are only removed if the file was opened with the owner password.
     */
    suspend fun save(renderer: PdfRenderer, out: ParcelFileDescriptor) = withContext(Dispatchers.IO) {
        // write() closes `out` and stays silent when it can't write (it leaves an empty file), so the
        // size is checked through a second handle. Pipes (some cloud providers) report -1: can't tell.
        out.dup().use { check ->
            renderer.write(out, /* removePasswordProtection = */ true)
            if (check.statSize == 0L) throw IOException("Nothing was written")
        }
    }
}
