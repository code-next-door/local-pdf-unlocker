package io.github.codenextdoor.pdfunlocker

/** The name offered in the "Save as" dialog: `report.pdf` → `report-unlocked.pdf`. */
fun suggestedOutputName(displayName: String?): String {
    val name = displayName?.trim().orEmpty()
    if (name.isEmpty()) return "unlocked.pdf"
    val base = if (name.endsWith(".pdf", ignoreCase = true)) name.dropLast(4) else name
    return "$base-unlocked.pdf"
}
