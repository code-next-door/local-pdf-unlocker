package io.github.codenextdoor.pdfunlocker

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri

/** The PDF another app opened ("Open with") or shared with this one, or null. */
fun incomingPdfUri(intent: Intent?): Uri? {
    val uri = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
    // Only content:// Uris, which come with a read grant. A file:// path from another app could point at
    // files only this app may read.
    return uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
}

/** Whether the app was started to open or receive a file (rather than from the launcher). */
fun isIncomingFile(intent: Intent?) = intent?.action == Intent.ACTION_VIEW || intent?.action == Intent.ACTION_SEND
