package io.github.codenextdoor.pdfunlocker

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomingPdfTest {
    private val content = Uri.parse("content://x/a.pdf")

    @Test fun viewGivesItsData() =
        assertEquals(content, incomingPdfUri(Intent(Intent.ACTION_VIEW).setDataAndType(content, "application/pdf")))

    @Test fun sendGivesItsStream() =
        assertEquals(content, incomingPdfUri(Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, content)))

    @Test fun fileUriIsRejected() =
        assertNull(incomingPdfUri(Intent(Intent.ACTION_VIEW).setData(Uri.parse("file:///sdcard/a.pdf"))))

    @Test fun sendWithoutStream() = assertNull(incomingPdfUri(Intent(Intent.ACTION_SEND).setType("application/pdf")))

    @Test fun launcherStart() = assertNull(incomingPdfUri(Intent(Intent.ACTION_MAIN)))

    @Test fun noIntent() = assertNull(incomingPdfUri(null))
}
