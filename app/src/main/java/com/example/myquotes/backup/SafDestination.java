package com.example.myquotes.backup;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** A user-chosen local folder, accessed through the Storage Access Framework. */
final class SafDestination implements BackupDestination {
    private final Context context;
    private final Uri treeUri;

    SafDestination(Context context, Uri treeUri) {
        this.context = context;
        this.treeUri = treeUri;
    }

    private DocumentFile folder() throws IOException {
        DocumentFile folder = DocumentFile.fromTreeUri(context, treeUri);
        if (folder == null || !folder.exists() || !folder.canWrite()) {
            throw new IOException("Backup folder is not accessible");
        }
        return folder;
    }

    @Override
    public void checkAvailable() throws IOException {
        folder();
    }

    @Override
    public void write(String filename, byte[] bytes) throws IOException {
        DocumentFile file = folder().createFile("application/json", filename);
        if (file == null) throw new IOException("Could not create backup file");

        // "wt" forces truncate on providers that don't truncate on "w" alone.
        try (OutputStream out = context.getContentResolver().openOutputStream(file.getUri(), "wt")) {
            if (out == null) throw new IOException("Could not open output stream for " + file.getUri());
            out.write(bytes);
        }
    }

    @Override
    public List<Entry> list() throws IOException {
        List<Entry> entries = new ArrayList<>();
        DocumentFile[] children = folder().listFiles();
        if (children != null) {
            for (DocumentFile child : children) {
                entries.add(new Entry(child.getName(), child));
            }
        }
        return entries;
    }

    @Override
    public void delete(Entry entry) {
        ((DocumentFile) entry.handle).delete();
    }

    @Override
    public byte[] read(Entry entry) throws IOException {
        Uri uri = ((DocumentFile) entry.handle).getUri();
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open input stream for " + uri);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
}
