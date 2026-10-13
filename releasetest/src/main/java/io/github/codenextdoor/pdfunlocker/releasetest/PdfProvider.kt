package io.github.codenextdoor.pdfunlocker.releasetest

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Serves an asset as `content://io.github.codenextdoor.pdfunlocker.releasetest.pdfs/<asset name>`. */
class PdfProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = requireNotNull(uri.lastPathSegment)
        val ctx = requireNotNull(context)
        val file = File(ctx.cacheDir, name).apply { writeBytes(ctx.assets.open(name).use { it.readBytes() }) }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(uri.lastPathSegment)) }

    override fun getType(uri: Uri) = "application/pdf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?) = 0
}
