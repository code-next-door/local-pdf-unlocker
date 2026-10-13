package io.github.codenextdoor.pdfunlocker;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Serves a test asset as {@code content://io.github.codenextdoor.pdfunlocker.test.pdfs/<asset name>}.
 * Java, not Kotlin: a provider runs in the test APK's own process, and that APK has no Kotlin
 * standard library (the build leaves it out because the app under test has it).
 */
public class TestPdfProvider extends ContentProvider {
    static final String AUTHORITY = "io.github.codenextdoor.pdfunlocker.test.pdfs";

    public static Uri uri(String asset) {
        return Uri.parse("content://" + AUTHORITY + "/" + asset);
    }

    @Override public boolean onCreate() {
        return true;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        File file = new File(getContext().getCacheDir(), name);
        try (InputStream in = getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
            in.transferTo(out);
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[] {OpenableColumns.DISPLAY_NAME});
        cursor.addRow(new Object[] {uri.getLastPathSegment()});
        return cursor;
    }

    @Override public String getType(Uri uri) {
        return "application/pdf";
    }

    @Override public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
