package io.github.codenextdoor.pdfunlocker

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {
    @Test fun addsSuffixBeforeExtension() = assertEquals("report-unlocked.pdf", suggestedOutputName("report.pdf"))

    @Test fun upperCaseExtension() = assertEquals("REPORT-unlocked.pdf", suggestedOutputName("REPORT.PDF"))

    @Test fun noExtension() = assertEquals("report-unlocked.pdf", suggestedOutputName("report"))

    @Test fun onlyLastPdfExtensionStripped() = assertEquals("a.pdf.v2-unlocked.pdf", suggestedOutputName("a.pdf.v2.pdf"))

    @Test fun otherExtensionKept() = assertEquals("scan.txt-unlocked.pdf", suggestedOutputName("scan.txt"))

    @Test fun missingName() = assertEquals("unlocked.pdf", suggestedOutputName(null))

    @Test fun blankName() = assertEquals("unlocked.pdf", suggestedOutputName("  "))
}
