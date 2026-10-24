package com.zikra.tv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Read-only grant for exactly the validated update APK; no arbitrary file paths. */
public final class UpdateFileProvider extends ContentProvider {
    public static Uri uri(Context context) { return Uri.parse("content://" + context.getPackageName() + ".updates/apk/latest"); }
    @Override public boolean onCreate() { return true; }
    private File file(Uri uri) throws FileNotFoundException { if (!uri.equals(uri(getContext()))) throw new FileNotFoundException();File file = AppUpdater.apkFile(getContext());if (!file.isFile()) throw new FileNotFoundException();return file; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException { if (!"r".equals(mode)) throw new FileNotFoundException("Read-only");return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY); }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {File file = file(uri);String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;MatrixCursor cursor = new MatrixCursor(columns);Object[] values = new Object[columns.length];for (int i = 0; i < columns.length; i++) {if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = "zikra-update.apk";else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();}cursor.addRow(values);return cursor;}
        catch (FileNotFoundException error) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
