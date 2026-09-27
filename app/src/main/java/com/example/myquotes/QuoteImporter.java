package com.example.myquotes;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Mirror of {@link QuoteExporter}: reads a JSON file at a Uri into a {@link BackupDocument}. */
public final class QuoteImporter {
    private QuoteImporter() {}

    public static BackupDocument readFromUri(Context context, Uri uri)
            throws IOException, QuoteCodecException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Could not open input stream for " + uri);
            return read(in);
        }
    }

    /** Decodes UTF-8 JSON from the stream. Does not close it. */
    public static BackupDocument read(InputStream in) throws IOException, QuoteCodecException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return BackupDocument.decode(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
    }
}
