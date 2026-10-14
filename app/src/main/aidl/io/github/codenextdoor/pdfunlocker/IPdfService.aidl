package io.github.codenextdoor.pdfunlocker;

// The sandboxed helper that reads PDFs (PdfService, isolated process). One open document per binding.
interface IPdfService {
    // PdfStatus ordinal. The descriptor is the app's private copy; the password exists only in memory.
    int open(in ParcelFileDescriptor pdf, String password);

    // SaveResult ordinal: writes the open document without its password to [destination].
    int save(in ParcelFileDescriptor destination);

    oneway void close();

    // Process.isIsolated() inside the helper; lets a test prove the sandbox.
    boolean isSandboxed();
}
